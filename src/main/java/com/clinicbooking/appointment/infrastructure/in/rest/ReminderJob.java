package com.clinicbooking.appointment.infrastructure.in.rest;

import com.clinicbooking.appointment.application.port.in.AppointmentUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara los recordatorios de 24 h (por defecto, cada hora en punto). */
@Component
public class ReminderJob {

    private final AppointmentUseCase useCase;

    public ReminderJob(AppointmentUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(cron = "${clinic.reminders.cron:0 0 * * * *}")
    public void run() {
        useCase.sendDueReminders();
    }
}
