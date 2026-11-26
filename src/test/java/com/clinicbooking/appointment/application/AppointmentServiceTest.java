package com.clinicbooking.appointment.application;

import com.clinicbooking.appointment.application.port.in.AppointmentUseCase.CancellationResult;
import com.clinicbooking.appointment.application.port.in.AppointmentUseCase.ScheduleCommand;
import com.clinicbooking.appointment.application.port.out.AppointmentRepositoryPort;
import com.clinicbooking.appointment.application.port.out.DoctorAgendaPort;
import com.clinicbooking.appointment.application.port.out.HolidayPort;
import com.clinicbooking.appointment.application.port.out.NotificationPort;
import com.clinicbooking.appointment.application.port.out.NotificationPort.NotificationMessage;
import com.clinicbooking.appointment.application.port.out.NotificationPort.ReminderMessage;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort.AttendanceOutcome;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort.PatientContact;
import com.clinicbooking.appointment.application.service.AppointmentService;
import com.clinicbooking.appointment.domain.Appointment;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.DomainException;
import com.clinicbooking.appointment.domain.NotFoundException;
import com.clinicbooking.appointment.domain.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 8, 0);
    private static final LocalDateTime TOMORROW_10 = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Mock AppointmentRepositoryPort repo;
    @Mock PatientDirectoryPort patients;
    @Mock DoctorAgendaPort agenda;
    @Mock NotificationPort notifications;
    @Mock HolidayPort holidays;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(repo, patients, agenda, notifications, holidays, CLOCK);
        when(repo.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.id() == null) {
                a.assignId(100L);
            }
            return a;
        });
        when(patients.getContact(1L)).thenReturn(new PatientContact(1L, "Ana", "ana@mail.com", "3001234567"));
        when(repo.findActiveOverlapping(any(), any(), any())).thenReturn(List.of());
        when(holidays.isHoliday(any())).thenReturn(false);
    }

    private Appointment existing(AppointmentStatus status, LocalDateTime start) {
        Appointment a = Appointment.restore(100L, 1L, 7L, start, start.plusMinutes(30), status,
                TriageLevel.LOW, "Control", null, false, 0, false);
        when(repo.findById(100L)).thenReturn(Optional.of(a));
        return a;
    }

    private ScheduleCommand command() {
        return new ScheduleCommand(1L, 7L, TOMORROW_10, 30, TriageLevel.LOW, "Control");
    }

    // ---------- schedule ----------
    @Test
    void scheduleReservesSlotAndNotifies() {
        Appointment a = service.schedule(command());
        assertEquals(100L, a.id());
        assertEquals(AppointmentStatus.SCHEDULED, a.status());
        verify(agenda).reserve(7L, 100L, TOMORROW_10, TOMORROW_10.plusMinutes(30));
        verify(notifications).notifyPatient(any(NotificationMessage.class));
    }

    @Test
    void scheduleRejectsHolidays() {
        when(holidays.isHoliday(TOMORROW_10.toLocalDate())).thenReturn(true);
        assertThrows(DomainException.class, () -> service.schedule(command()));
        verify(repo, never()).save(any());
    }

    @Test
    void scheduleRejectsOverlappingAppointments() {
        // existing() hace su propio when(...); se crea ANTES para no anidar stubbings.
        Appointment overlapping = existing(AppointmentStatus.SCHEDULED, TOMORROW_10);
        when(repo.findActiveOverlapping(eq(7L), any(), any()))
                .thenReturn(List.of(overlapping));
        assertThrows(DomainException.class, () -> service.schedule(command()));
        verify(agenda, never()).reserve(any(), any(), any(), any());
    }

    @Test
    void scheduleRollsBackWhenAgendaReservationFails() {
        doThrow(new DomainException("cupo ocupado")).when(agenda).reserve(any(), any(), any(), any());
        assertThrows(DomainException.class, () -> service.schedule(command()));
        verify(repo).deleteById(100L);
        verify(notifications, never()).notifyPatient(any());
    }

    @Test
    void notificationFailureDoesNotBreakScheduling() {
        doThrow(new ExternalServiceException("caído", null)).when(notifications).notifyPatient(any());
        assertNotNull(service.schedule(command()));
    }

    @Test
    void scheduleFailsWhenPatientDoesNotExist() {
        when(patients.getContact(2L)).thenThrow(new NotFoundException("Paciente no encontrado: 2"));
        assertThrows(NotFoundException.class,
                () -> service.schedule(new ScheduleCommand(2L, 7L, TOMORROW_10, 30, TriageLevel.LOW, "x")));
    }

    // ---------- get / confirm ----------
    @Test
    void getThrowsWhenMissing() {
        assertThrows(NotFoundException.class, () -> service.get(404L));
    }

    @Test
    void confirmTransitionsAndNotifies() {
        existing(AppointmentStatus.SCHEDULED, TOMORROW_10);
        assertEquals(AppointmentStatus.CONFIRMED, service.confirm(100L).status());
        verify(notifications).notifyPatient(any(NotificationMessage.class));
    }

    @Test
    void confirmFailsWhenNotScheduled() {
        existing(AppointmentStatus.CANCELLED, TOMORROW_10);
        assertThrows(DomainException.class, () -> service.confirm(100L));
    }

    // ---------- reschedule ----------
    @Test
    void rescheduleReleasesOldSlotAndReservesNew() {
        existing(AppointmentStatus.CONFIRMED, TOMORROW_10);
        LocalDateTime newStart = TOMORROW_10.plusDays(1);
        Appointment a = service.reschedule(100L, newStart);
        assertEquals(newStart, a.start());
        verify(agenda).release(100L);
        verify(agenda).reserve(7L, 100L, newStart, newStart.plusMinutes(30));
    }

    @Test
    void rescheduleRestoresOldSlotWhenNewReservationFails() {
        existing(AppointmentStatus.SCHEDULED, TOMORROW_10);
        LocalDateTime newStart = TOMORROW_10.plusDays(1);
        doThrow(new DomainException("ocupado")).when(agenda)
                .reserve(eq(7L), eq(100L), eq(newStart), eq(newStart.plusMinutes(30)));
        assertThrows(DomainException.class, () -> service.reschedule(100L, newStart));
        verify(agenda).reserve(7L, 100L, TOMORROW_10, TOMORROW_10.plusMinutes(30));
    }

    @Test
    void rescheduleRejectsHolidayAndConflicts() {
        existing(AppointmentStatus.SCHEDULED, TOMORROW_10);
        LocalDateTime newStart = TOMORROW_10.plusDays(1);
        when(holidays.isHoliday(newStart.toLocalDate())).thenReturn(true);
        assertThrows(DomainException.class, () -> service.reschedule(100L, newStart));

        when(holidays.isHoliday(any())).thenReturn(false);
        Appointment other = Appointment.restore(200L, 3L, 7L, newStart, newStart.plusMinutes(30),
                AppointmentStatus.SCHEDULED, TriageLevel.LOW, "x", null, false, 0, false);
        when(repo.findActiveOverlapping(eq(7L), any(), any())).thenReturn(List.of(other));
        assertThrows(DomainException.class, () -> service.reschedule(100L, newStart));
    }

    @Test
    void rescheduleIgnoresItselfInOverlapCheck() {
        Appointment self = existing(AppointmentStatus.SCHEDULED, TOMORROW_10);
        when(repo.findActiveOverlapping(eq(7L), any(), any())).thenReturn(List.of(self));
        assertNotNull(service.reschedule(100L, TOMORROW_10.plusMinutes(15)));
    }

    // ---------- cancel ----------
    @Test
    void cancelFreeReleasesSlotAndRecordsAttendance() {
        existing(AppointmentStatus.SCHEDULED, TOMORROW_10);
        CancellationResult r = service.cancel(100L, "Cambio de planes");
        assertFalse(r.lateCancellation());
        assertEquals(AppointmentStatus.CANCELLED, r.appointment().status());
        verify(agenda).release(100L);
        verify(patients).recordAttendance(1L, 100L, AttendanceOutcome.CANCELLED, TOMORROW_10.toLocalDate());
    }

    @Test
    void cancelWithLessThan24hIsFlaggedLate() {
        existing(AppointmentStatus.CONFIRMED, NOW.plusHours(5));
        assertTrue(service.cancel(100L, "Imprevisto").lateCancellation());
    }

    // ---------- no-show / complete ----------
    @Test
    void markNoShowRecordsIncident() {
        existing(AppointmentStatus.SCHEDULED, NOW.minusHours(1));
        assertEquals(AppointmentStatus.NO_SHOW, service.markNoShow(100L).status());
        verify(patients).recordAttendance(1L, 100L, AttendanceOutcome.NO_SHOW, NOW.minusHours(1).toLocalDate());
    }

    @Test
    void markNoShowSurvivesPatientServiceFailure() {
        existing(AppointmentStatus.SCHEDULED, NOW.minusHours(1));
        doThrow(new ExternalServiceException("caído", null)).when(patients)
                .recordAttendance(any(), any(), any(), any());
        assertEquals(AppointmentStatus.NO_SHOW, service.markNoShow(100L).status());
    }

    @Test
    void markNoShowBeforeStartFails() {
        existing(AppointmentStatus.SCHEDULED, TOMORROW_10);
        assertThrows(DomainException.class, () -> service.markNoShow(100L));
    }

    @Test
    void completeRecordsAttendance() {
        existing(AppointmentStatus.CONFIRMED, NOW.minusHours(1));
        assertEquals(AppointmentStatus.COMPLETED, service.complete(100L).status());
        verify(patients).recordAttendance(eq(1L), eq(100L), eq(AttendanceOutcome.ATTENDED), any(LocalDate.class));
    }

    // ---------- agenda ----------
    @Test
    void dailyAgendaFiltersByDoctorAndStatus() {
        Appointment a = Appointment.restore(1L, 1L, 7L, TOMORROW_10, TOMORROW_10.plusMinutes(30),
                AppointmentStatus.CONFIRMED, TriageLevel.LOW, "x", null, false, 0, false);
        Appointment b = Appointment.restore(2L, 1L, 8L, TOMORROW_10, TOMORROW_10.plusMinutes(30),
                AppointmentStatus.SCHEDULED, TriageLevel.LOW, "x", null, false, 0, false);
        when(repo.findByDay(TOMORROW_10.toLocalDate())).thenReturn(List.of(a, b));

        LocalDate day = TOMORROW_10.toLocalDate();
        assertEquals(2, service.dailyAgenda(day, null, null).size());
        assertEquals(List.of(a), service.dailyAgenda(day, 7L, null));
        assertEquals(List.of(b), service.dailyAgenda(day, null, AppointmentStatus.SCHEDULED));
        assertTrue(service.dailyAgenda(day, 7L, AppointmentStatus.SCHEDULED).isEmpty());
    }

    // ---------- reminders ----------
    @Test
    void sendDueRemindersDispatchesAndMarksSent() {
        Appointment due = Appointment.restore(1L, 1L, 7L, NOW.plusHours(24), NOW.plusHours(24).plusMinutes(30),
                AppointmentStatus.CONFIRMED, TriageLevel.LOW, "x", null, false, 0, false);
        when(repo.findPendingReminders(NOW.plusHours(23), NOW.plusHours(25))).thenReturn(List.of(due));
        assertEquals(1, service.sendDueReminders());
        assertTrue(due.reminderSent());
        verify(notifications).dispatchReminder(any(ReminderMessage.class));
    }

    @Test
    void sendDueRemindersSkipsFailures() {
        Appointment due = Appointment.restore(1L, 1L, 7L, NOW.plusHours(24), NOW.plusHours(24).plusMinutes(30),
                AppointmentStatus.CONFIRMED, TriageLevel.LOW, "x", null, false, 0, false);
        when(repo.findPendingReminders(any(), any())).thenReturn(List.of(due));
        doThrow(new ExternalServiceException("caído", null)).when(notifications).dispatchReminder(any());
        assertEquals(0, service.sendDueReminders());
        assertFalse(due.reminderSent());
    }
}