package com.clinicbooking.appointment.infrastructure.out;

import com.clinicbooking.appointment.application.ExternalServiceException;
import com.clinicbooking.appointment.application.port.out.NotificationPort.NotificationMessage;
import com.clinicbooking.appointment.application.port.out.NotificationPort.ReminderMessage;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort.AttendanceOutcome;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort.PatientContact;
import com.clinicbooking.appointment.domain.DomainException;
import com.clinicbooking.appointment.domain.NotFoundException;
import com.clinicbooking.appointment.infrastructure.out.http.NagerHolidayAdapter;
import com.clinicbooking.appointment.infrastructure.out.http.NotificationServiceHttpAdapter;
import com.clinicbooking.appointment.infrastructure.out.http.PatientServiceHttpAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAdaptersTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 24, 10, 0);

    // ---------- patient-service ----------
    private final RestClient.Builder patientBuilder = RestClient.builder().baseUrl("http://patients.test");
    private final MockRestServiceServer patientServer = MockRestServiceServer.bindTo(patientBuilder).build();
    private final PatientServiceHttpAdapter patientAdapter = new PatientServiceHttpAdapter(patientBuilder.build());

    @Test
    void getContactParsesPatient() {
        patientServer.expect(requestTo("http://patients.test/api/v1/patients/1"))
                .andRespond(withSuccess("{\"id\":1,\"fullName\":\"Ana\",\"email\":\"ana@mail.com\",\"phone\":\"300\",\"insuranceActive\":true}",
                        MediaType.APPLICATION_JSON));
        PatientContact c = patientAdapter.getContact(1L);
        assertEquals("Ana", c.fullName());
        assertEquals("ana@mail.com", c.email());
        patientServer.verify();
    }

    @Test
    void getContactMapsErrors() {
        patientServer.expect(once(), requestTo("http://patients.test/api/v1/patients/9")).andRespond(withResourceNotFound());
        assertThrows(NotFoundException.class, () -> patientAdapter.getContact(9L));

        patientServer.reset(); // no se pueden añadir expectativas tras haber hecho peticiones
        patientServer.expect(once(), requestTo("http://patients.test/api/v1/patients/8")).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThrows(DomainException.class, () -> patientAdapter.getContact(8L));

        patientServer.reset(); // no se pueden añadir expectativas tras haber hecho peticiones
        patientServer.expect(once(), requestTo("http://patients.test/api/v1/patients/7")).andRespond(withServerError());
        assertThrows(ExternalServiceException.class, () -> patientAdapter.getContact(7L));

        patientServer.reset(); // no se pueden añadir expectativas tras haber hecho peticiones
        patientServer.expect(once(), requestTo("http://patients.test/api/v1/patients/6")).andRespond(withNoContent());
        assertThrows(ExternalServiceException.class, () -> patientAdapter.getContact(6L));
    }

    @Test
    void reserveReleaseAndRecordAttendanceCallPatientService() {
        patientServer.expect(requestTo("http://patients.test/api/v1/doctors/7/booked-slots"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.appointmentId").value(100))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
        patientAdapter.reserve(7L, 100L, START, START.plusMinutes(30));
        patientServer.verify();
        patientServer.reset();

        patientServer.expect(requestTo("http://patients.test/api/v1/doctors/booked-slots/100"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
        patientAdapter.release(100L);
        patientServer.verify();
        patientServer.reset();

        patientServer.expect(requestTo("http://patients.test/api/v1/patients/1/attendance"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
        patientAdapter.recordAttendance(1L, 100L, AttendanceOutcome.NO_SHOW, START.toLocalDate());
        patientServer.verify();
    }

    @Test
    void reserveConflictBecomesDomainError() {
        patientServer.expect(requestTo("http://patients.test/api/v1/doctors/7/booked-slots"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));
        assertThrows(DomainException.class, () -> patientAdapter.reserve(7L, 100L, START, START.plusMinutes(30)));
    }

    // ---------- notification-service ----------
    private final RestClient.Builder notifBuilder = RestClient.builder().baseUrl("http://notif.test");
    private final MockRestServiceServer notifServer = MockRestServiceServer.bindTo(notifBuilder).build();
    private final NotificationServiceHttpAdapter notifAdapter = new NotificationServiceHttpAdapter(notifBuilder.build());

    @Test
    void notifyPatientUsesBothChannels() {
        notifServer.expect(requestTo("http://notif.test/api/v1/notifications/send-email"))
                .andExpect(jsonPath("$.to").value("ana@mail.com"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
        notifServer.expect(requestTo("http://notif.test/api/v1/notifications/send-sms"))
                .andExpect(jsonPath("$.to").value("300"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
        notifAdapter.notifyPatient(new NotificationMessage(1L, "Ana", "ana@mail.com", "300", "Cita", "texto"));
        notifServer.verify();
    }

    @Test
    void notifyPatientSkipsMissingChannels() {
        notifAdapter.notifyPatient(new NotificationMessage(1L, "Ana", null, " ", "Cita", "texto"));
        notifServer.verify(); // no se esperaba ninguna llamada
    }

    @Test
    void notifyPatientWrapsFailures() {
        notifServer.expect(requestTo("http://notif.test/api/v1/notifications/send-email")).andRespond(withServerError());
        assertThrows(ExternalServiceException.class,
                () -> notifAdapter.notifyPatient(new NotificationMessage(1L, "Ana", "ana@mail.com", null, "Cita", "texto")));
    }

    @Test
    void dispatchReminderCallsReminderEndpoint() {
        notifServer.expect(requestTo("http://notif.test/api/v1/notifications/dispatch-appointment-reminder"))
                .andExpect(jsonPath("$.appointmentId").value(1))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        notifAdapter.dispatchReminder(new ReminderMessage(1L, "Ana", "ana@mail.com", "300", START));
        notifServer.verify();
        notifServer.reset();

        notifServer.expect(requestTo("http://notif.test/api/v1/notifications/dispatch-appointment-reminder")).andRespond(withServerError());
        assertThrows(ExternalServiceException.class,
                () -> notifAdapter.dispatchReminder(new ReminderMessage(1L, "Ana", "ana@mail.com", "300", START)));
    }

    // ---------- Nager.Date ----------
    private final RestClient.Builder holidayBuilder = RestClient.builder().baseUrl("http://holidays.test");
    private final MockRestServiceServer holidayServer = MockRestServiceServer.bindTo(holidayBuilder).build();
    private final NagerHolidayAdapter holidayAdapter = new NagerHolidayAdapter(holidayBuilder.build(), "CO");

    @Test
    void holidayAdapterDetectsHolidaysAndCachesTheYear() {
        holidayServer.expect(once(), requestTo("http://holidays.test/api/v3/PublicHolidays/2026/CO"))
                .andRespond(withSuccess("[{\"date\":\"2026-12-25\",\"localName\":\"Navidad\",\"name\":\"Christmas Day\",\"countryCode\":\"CO\"}]",
                        MediaType.APPLICATION_JSON));
        assertTrue(holidayAdapter.isHoliday(LocalDate.of(2026, 12, 25)));
        assertFalse(holidayAdapter.isHoliday(LocalDate.of(2026, 12, 24)));
        holidayServer.verify(); // una sola llamada HTTP gracias a la caché
    }

    @Test
    void holidayAdapterFallsBackToBusinessDayOnFailure() {
        holidayServer.expect(requestTo("http://holidays.test/api/v3/PublicHolidays/2026/CO")).andRespond(withServerError());
        assertFalse(holidayAdapter.isHoliday(LocalDate.of(2026, 12, 25)));
    }

    @Test
    void holidayAdapterHandlesEmptyBody() {
        holidayServer.expect(requestTo("http://holidays.test/api/v3/PublicHolidays/2027/CO")).andRespond(withNoContent());
        assertFalse(holidayAdapter.isHoliday(LocalDate.of(2027, 1, 1)));
    }
}