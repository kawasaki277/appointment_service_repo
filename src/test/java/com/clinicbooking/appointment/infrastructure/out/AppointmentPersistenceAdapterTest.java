package com.clinicbooking.appointment.infrastructure.out;

import com.clinicbooking.appointment.domain.Appointment;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.TriageLevel;
import com.clinicbooking.appointment.infrastructure.out.persistence.AppointmentEntity;
import com.clinicbooking.appointment.infrastructure.out.persistence.AppointmentJpaRepository;
import com.clinicbooking.appointment.infrastructure.out.persistence.AppointmentPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentPersistenceAdapterTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Mock AppointmentJpaRepository repository;

    private AppointmentEntity entity() {
        AppointmentEntity e = new AppointmentEntity();
        e.id = 1L;
        e.patientId = 2L;
        e.doctorId = 3L;
        e.startTime = START;
        e.endTime = START.plusMinutes(30);
        e.status = "CONFIRMED";
        e.triage = "HIGH";
        e.reason = "Control";
        e.rescheduleCount = 1;
        return e;
    }

    @Test
    void saveMapsBothWays() {
        when(repository.save(any(AppointmentEntity.class))).thenAnswer(i -> {
            AppointmentEntity e = i.getArgument(0);
            e.id = 11L;
            return e;
        });
        AppointmentPersistenceAdapter adapter = new AppointmentPersistenceAdapter(repository);
        Appointment saved = adapter.save(Appointment.schedule(2L, 3L, START, 30, TriageLevel.MEDIUM, "x", START.minusDays(2)));
        assertEquals(11L, saved.id());
        assertEquals(AppointmentStatus.SCHEDULED, saved.status());
        assertEquals(TriageLevel.MEDIUM, saved.triage());
    }

    @Test
    void queriesDelegateToRepository() {
        AppointmentPersistenceAdapter adapter = new AppointmentPersistenceAdapter(repository);
        when(repository.findById(1L)).thenReturn(Optional.of(entity()));
        assertEquals(AppointmentStatus.CONFIRMED, adapter.findById(1L).orElseThrow().status());
        assertTrue(adapter.findById(2L).isEmpty());

        when(repository.findByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusIn(any(), any(), any(), anyCollection()))
                .thenReturn(List.of(entity()));
        assertEquals(1, adapter.findActiveOverlapping(3L, START, START.plusMinutes(30)).size());

        LocalDate day = START.toLocalDate();
        when(repository.findByStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(entity()));
        assertEquals(1, adapter.findByDay(day).size());

        when(repository.findByStartTimeBetweenAndReminderSentFalseAndStatusIn(any(), any(), anyCollection()))
                .thenReturn(List.of(entity()));
        assertEquals(1, adapter.findPendingReminders(START.minusDays(1), START).size());

        adapter.deleteById(1L);
        verify(repository).deleteById(1L);
    }
}
