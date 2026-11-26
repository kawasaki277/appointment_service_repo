package com.clinicbooking.appointment.application.port.out;

import com.clinicbooking.appointment.domain.Appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepositoryPort {
    Appointment save(Appointment appointment);

    Optional<Appointment> findById(Long id);

    void deleteById(Long id);

    List<Appointment> findActiveOverlapping(Long doctorId, LocalDateTime start, LocalDateTime end);

    List<Appointment> findByDay(LocalDate date);

    List<Appointment> findPendingReminders(LocalDateTime from, LocalDateTime to);
}
