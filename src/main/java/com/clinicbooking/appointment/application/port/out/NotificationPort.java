package com.clinicbooking.appointment.application.port.out;

import java.time.LocalDateTime;

/** Envío de notificaciones vía notification-service. */
public interface NotificationPort {

    void notifyPatient(NotificationMessage message);

    void dispatchReminder(ReminderMessage message);

    record NotificationMessage(Long appointmentId, String patientName, String email, String phone,
                               String subject, String body) {
    }

    record ReminderMessage(Long appointmentId, String patientName, String email, String phone,
                           LocalDateTime appointmentStart) {
    }
}
