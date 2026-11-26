package com.clinicbooking.appointment.application.port.out;

import java.time.LocalDateTime;

/** Reserva y liberación de cupos en la agenda del médico (patient-service). */
public interface DoctorAgendaPort {

    void reserve(Long doctorId, Long appointmentId, LocalDateTime start, LocalDateTime end);

    void release(Long appointmentId);
}
