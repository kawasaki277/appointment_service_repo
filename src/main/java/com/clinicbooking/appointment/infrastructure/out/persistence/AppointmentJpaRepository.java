package com.clinicbooking.appointment.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, Long> {

    List<AppointmentEntity> findByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusIn(
            Long doctorId, LocalDateTime end, LocalDateTime start, Collection<String> statuses);

    List<AppointmentEntity> findByStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
            LocalDateTime from, LocalDateTime to);

    List<AppointmentEntity> findByStartTimeBetweenAndReminderSentFalseAndStatusIn(
            LocalDateTime from, LocalDateTime to, Collection<String> statuses);
}
