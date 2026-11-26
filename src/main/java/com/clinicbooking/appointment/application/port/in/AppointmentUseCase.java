package com.clinicbooking.appointment.application.port.in;

import com.clinicbooking.appointment.domain.Appointment;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.TriageLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentUseCase {

    Appointment schedule(ScheduleCommand command);

    Appointment get(Long id);

    Appointment confirm(Long id);

    Appointment reschedule(Long id, LocalDateTime newStart);

    CancellationResult cancel(Long id, String reason);

    Appointment markNoShow(Long id);

    Appointment complete(Long id);

    List<Appointment> dailyAgenda(LocalDate date, Long doctorId, AppointmentStatus status);

    /** Envía recordatorios de citas que ocurren en ~24 h. @return cantidad de recordatorios enviados */
    int sendDueReminders();

    record ScheduleCommand(Long patientId, Long doctorId, LocalDateTime start, int durationMinutes,
                           TriageLevel triage, String reason) {
    }

    record CancellationResult(Appointment appointment, boolean lateCancellation) {
    }
}
