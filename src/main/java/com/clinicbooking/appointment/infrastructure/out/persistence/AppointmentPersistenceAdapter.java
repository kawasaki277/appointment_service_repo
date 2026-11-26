package com.clinicbooking.appointment.infrastructure.out.persistence;

import com.clinicbooking.appointment.application.port.out.AppointmentRepositoryPort;
import com.clinicbooking.appointment.domain.Appointment;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.TriageLevel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class AppointmentPersistenceAdapter implements AppointmentRepositoryPort {

    private static final List<String> ACTIVE = List.of(AppointmentStatus.SCHEDULED.name(), AppointmentStatus.CONFIRMED.name());

    private final AppointmentJpaRepository repository;

    public AppointmentPersistenceAdapter(AppointmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Appointment save(Appointment a) {
        AppointmentEntity e = new AppointmentEntity();
        e.id = a.id();
        e.patientId = a.patientId();
        e.doctorId = a.doctorId();
        e.startTime = a.start();
        e.endTime = a.end();
        e.status = a.status().name();
        e.triage = a.triage().name();
        e.reason = a.reason();
        e.cancellationReason = a.cancellationReason();
        e.lateCancellation = a.lateCancellation();
        e.rescheduleCount = a.rescheduleCount();
        e.reminderSent = a.reminderSent();
        return toDomain(repository.save(e));
    }

    @Override
    public Optional<Appointment> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public List<Appointment> findActiveOverlapping(Long doctorId, LocalDateTime start, LocalDateTime end) {
        return repository.findByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusIn(doctorId, end, start, ACTIVE)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Appointment> findByDay(LocalDate date) {
        return repository.findByStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Appointment> findPendingReminders(LocalDateTime from, LocalDateTime to) {
        return repository.findByStartTimeBetweenAndReminderSentFalseAndStatusIn(from, to, ACTIVE)
                .stream().map(this::toDomain).toList();
    }

    private Appointment toDomain(AppointmentEntity e) {
        return Appointment.restore(e.id, e.patientId, e.doctorId, e.startTime, e.endTime,
                AppointmentStatus.valueOf(e.status), TriageLevel.valueOf(e.triage), e.reason,
                e.cancellationReason, e.lateCancellation, e.rescheduleCount, e.reminderSent);
    }
}
