package com.clinicbooking.appointment.infrastructure.out.http;

import com.clinicbooking.appointment.application.port.out.HolidayPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** API externa #2: días festivos públicos (Nager.Date). Con caché por año y fallback tolerante a fallos. */
public class NagerHolidayAdapter implements HolidayPort {

    private static final Logger log = LoggerFactory.getLogger(NagerHolidayAdapter.class);

    private final RestClient client;
    private final String country;
    private final Map<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();

    public NagerHolidayAdapter(RestClient client, String country) {
        this.client = client;
        this.country = country;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NagerHoliday(String date, String name) {
    }

    @Override
    public boolean isHoliday(LocalDate date) {
        try {
            return cache.computeIfAbsent(date.getYear(), this::fetchYear).contains(date);
        } catch (RuntimeException e) {
            // Fallback: si la API no responde, se asume día hábil para no bloquear la agenda.
            log.warn("API de festivos no disponible; se asume día hábil para {}: {}", date, e.getMessage());
            return false;
        }
    }

    private Set<LocalDate> fetchYear(int year) {
        NagerHoliday[] holidays = client.get()
                .uri("/api/v3/PublicHolidays/{year}/{country}", year, country)
                .retrieve().body(NagerHoliday[].class);
        if (holidays == null) {
            return Set.of();
        }
        return Arrays.stream(holidays).map(h -> LocalDate.parse(h.date())).collect(Collectors.toSet());
    }
}
