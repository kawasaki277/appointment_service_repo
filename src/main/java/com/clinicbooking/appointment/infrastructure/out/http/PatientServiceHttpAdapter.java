package com.clinicbooking.appointment.infrastructure.out.http;

import com.clinicbooking.appointment.application.ExternalServiceException;
import com.clinicbooking.appointment.application.port.out.DoctorAgendaPort;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort;
import com.clinicbooking.appointment.domain.DomainException;
import com.clinicbooking.appointment.domain.NotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Adaptador HTTP hacia patient-service (directorio de pacientes + agenda de médicos). */
public class PatientServiceHttpAdapter implements PatientDirectoryPort, DoctorAgendaPort {

    private final RestClient client;

    public PatientServiceHttpAdapter(RestClient client) {
        this.client = client;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PatientPayload(Long id, String fullName, String email, String phone) {
    }

    @Override
    public PatientContact getContact(Long patientId) {
        PatientPayload p = call("consultar paciente " + patientId, () -> {
            try {
                return client.get().uri("/api/v1/patients/{id}", patientId).retrieve().body(PatientPayload.class);
            } catch (HttpClientErrorException.NotFound e) {
                throw new NotFoundException("Paciente no encontrado: " + patientId);
            }
        });
        if (p == null) {
            throw new ExternalServiceException("patient-service devolvió una respuesta vacía", null);
        }
        return new PatientContact(p.id(), p.fullName(), p.email(), p.phone());
    }

    @Override
    public void recordAttendance(Long patientId, Long appointmentId, AttendanceOutcome outcome, LocalDate date) {
        call("registrar asistencia", () -> client.post().uri("/api/v1/patients/{id}/attendance", patientId)
                .body(Map.of("appointmentId", appointmentId, "status", outcome.name(), "date", date.toString()))
                .retrieve().toBodilessEntity());
    }

    @Override
    public void reserve(Long doctorId, Long appointmentId, LocalDateTime start, LocalDateTime end) {
        call("reservar cupo del médico " + doctorId, () -> client.post().uri("/api/v1/doctors/{id}/booked-slots", doctorId)
                .body(Map.of("appointmentId", appointmentId, "start", start.toString(), "end", end.toString()))
                .retrieve().toBodilessEntity());
    }

    @Override
    public void release(Long appointmentId) {
        call("liberar cupo", () -> client.delete().uri("/api/v1/doctors/booked-slots/{id}", appointmentId)
                .retrieve().toBodilessEntity());
    }

    private <T> T call(String action, java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            throw new DomainException("patient-service rechazó la operación (" + action + "): " + e.getStatusCode());
        } catch (RestClientException e) {
            throw new ExternalServiceException("patient-service no disponible al " + action, e);
        }
    }
}
