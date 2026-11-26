package com.clinicbooking.appointment.infrastructure.out.http;

import com.clinicbooking.appointment.application.ExternalServiceException;
import com.clinicbooking.appointment.application.port.out.NotificationPort;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/** Adaptador HTTP hacia notification-service. */
public class NotificationServiceHttpAdapter implements NotificationPort {

    private final RestClient client;

    public NotificationServiceHttpAdapter(RestClient client) {
        this.client = client;
    }

    @Override
    public void notifyPatient(NotificationMessage m) {
        try {
            if (hasText(m.email())) {
                post("/api/v1/notifications/send-email", payload(m.appointmentId(),
                        "to", m.email(), "subject", m.subject(), "body", m.body()));
            }
            if (hasText(m.phone())) {
                post("/api/v1/notifications/send-sms", payload(m.appointmentId(),
                        "to", m.phone(), "body", m.subject() + ": " + m.body()));
            }
        } catch (RestClientException e) {
            throw new ExternalServiceException("notification-service no disponible", e);
        }
    }

    @Override
    public void dispatchReminder(ReminderMessage m) {
        Map<String, Object> body = new HashMap<>();
        body.put("appointmentId", m.appointmentId());
        body.put("patientName", m.patientName());
        body.put("email", m.email());
        body.put("phone", m.phone());
        body.put("appointmentStart", m.appointmentStart().toString());
        try {
            post("/api/v1/notifications/dispatch-appointment-reminder", body);
        } catch (RestClientException e) {
            throw new ExternalServiceException("notification-service no disponible", e);
        }
    }

    private void post(String uri, Map<String, Object> body) {
        client.post().uri(uri).body(body).retrieve().toBodilessEntity();
    }

    private static Map<String, Object> payload(Long appointmentId, String... keyValues) {
        Map<String, Object> map = new HashMap<>();
        map.put("appointmentId", appointmentId);
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
