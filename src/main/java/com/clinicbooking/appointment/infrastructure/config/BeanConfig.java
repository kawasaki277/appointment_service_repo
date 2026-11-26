package com.clinicbooking.appointment.infrastructure.config;

import com.clinicbooking.appointment.application.port.in.AppointmentUseCase;
import com.clinicbooking.appointment.application.port.out.AppointmentRepositoryPort;
import com.clinicbooking.appointment.application.port.out.DoctorAgendaPort;
import com.clinicbooking.appointment.application.port.out.HolidayPort;
import com.clinicbooking.appointment.application.port.out.NotificationPort;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort;
import com.clinicbooking.appointment.application.service.AppointmentService;
import com.clinicbooking.appointment.infrastructure.out.http.NagerHolidayAdapter;
import com.clinicbooking.appointment.infrastructure.out.http.NotificationServiceHttpAdapter;
import com.clinicbooking.appointment.infrastructure.out.http.PatientServiceHttpAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class BeanConfig {

    @Bean
    Clock clock(@Value("${clinic.timezone:America/Bogota}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    PatientServiceHttpAdapter patientServiceAdapter(RestClient.Builder builder,
            @Value("${clinic.services.patient-url}") String url,
            @Value("${clinic.http.connect-timeout-ms}") long connectMs,
            @Value("${clinic.http.read-timeout-ms}") long readMs) {
        return new PatientServiceHttpAdapter(RestClients.create(builder, url, connectMs, readMs));
    }

    @Bean
    NotificationServiceHttpAdapter notificationServiceAdapter(RestClient.Builder builder,
            @Value("${clinic.services.notification-url}") String url,
            @Value("${clinic.http.connect-timeout-ms}") long connectMs,
            @Value("${clinic.http.read-timeout-ms}") long readMs) {
        return new NotificationServiceHttpAdapter(RestClients.create(builder, url, connectMs, readMs));
    }

    @Bean
    NagerHolidayAdapter holidayAdapter(RestClient.Builder builder,
            @Value("${clinic.holidays.base-url}") String url,
            @Value("${clinic.holidays.country}") String country,
            @Value("${clinic.http.connect-timeout-ms}") long connectMs,
            @Value("${clinic.http.read-timeout-ms}") long readMs) {
        return new NagerHolidayAdapter(RestClients.create(builder, url, connectMs, readMs), country);
    }

    @Bean
    AppointmentUseCase appointmentUseCase(AppointmentRepositoryPort repository, PatientServiceHttpAdapter patientService,
                                          NotificationServiceHttpAdapter notifications, NagerHolidayAdapter holidays,
                                          Clock clock) {
        PatientDirectoryPort directory = patientService;
        DoctorAgendaPort agenda = patientService;
        NotificationPort notificationPort = notifications;
        HolidayPort holidayPort = holidays;
        return new AppointmentService(repository, directory, agenda, notificationPort, holidayPort, clock);
    }
}
