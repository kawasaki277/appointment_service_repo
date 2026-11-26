package com.clinicbooking.appointment.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 8, 0);

    private Appointment scheduledIn(long hours) {
        return Appointment.schedule(1L, 7L, NOW.plusHours(hours), 30, TriageLevel.LOW, "Control", NOW);
    }

    @Test
    void scheduleCreatesScheduledAppointment() {
        Appointment a = scheduledIn(48);
        assertEquals(AppointmentStatus.SCHEDULED, a.status());
        assertEquals(a.start().plusMinutes(30), a.end());
        assertEquals(TriageLevel.LOW, a.triage());
        assertNull(a.id());
        a.assignId(5L);
        assertEquals(5L, a.id());
    }

    @Test
    void scheduleValidatesInput() {
        assertThrows(DomainException.class, () -> Appointment.schedule(null, 7L, NOW.plusDays(1), 30, null, "x", NOW));
        assertThrows(DomainException.class, () -> Appointment.schedule(1L, 7L, NOW.plusDays(1), 5, null, "x", NOW));
        assertThrows(DomainException.class, () -> Appointment.schedule(1L, 7L, NOW.plusMinutes(30), 30, null, "x", NOW));
    }

    @Test
    void nullTriageDefaultsToLow() {
        assertEquals(TriageLevel.LOW,
                Appointment.schedule(1L, 7L, NOW.plusDays(1), 30, null, "x", NOW).triage());
    }

    @Test
    void urgentAppointmentsSkipMinimumAdvanceButNotThePast() {
        Appointment urgent = Appointment.schedule(1L, 7L, NOW.plusMinutes(10), 30, TriageLevel.URGENT, "Dolor", NOW);
        assertEquals(TriageLevel.URGENT, urgent.triage());
        assertThrows(DomainException.class,
                () -> Appointment.schedule(1L, 7L, NOW.minusMinutes(1), 30, TriageLevel.URGENT, "Dolor", NOW));
    }

    @Test
    void confirmOnlyFromScheduled() {
        Appointment a = scheduledIn(48);
        a.confirm();
        assertEquals(AppointmentStatus.CONFIRMED, a.status());
        assertThrows(DomainException.class, a::confirm);
    }

    @Test
    void rescheduleMovesAppointmentKeepingDuration() {
        Appointment a = scheduledIn(48);
        a.confirm();
        a.reschedule(NOW.plusHours(72), NOW);
        assertEquals(AppointmentStatus.SCHEDULED, a.status());
        assertEquals(NOW.plusHours(72), a.start());
        assertEquals(NOW.plusHours(72).plusMinutes(30), a.end());
        assertEquals(1, a.rescheduleCount());
    }

    @Test
    void rescheduleEnforcesAdvanceRules() {
        Appointment soon = scheduledIn(2);
        assertThrows(DomainException.class, () -> soon.reschedule(NOW.plusHours(48), NOW.plusHours(1).plusMinutes(30)));
        Appointment a = scheduledIn(48);
        assertThrows(DomainException.class, () -> a.reschedule(NOW.plusMinutes(30), NOW));
    }

    @Test
    void rescheduleLimit() {
        Appointment a = scheduledIn(48);
        for (int i = 0; i < Appointment.MAX_RESCHEDULES; i++) {
            a.reschedule(NOW.plusHours(50 + i), NOW);
        }
        assertThrows(DomainException.class, () -> a.reschedule(NOW.plusHours(90), NOW));
    }

    @Test
    void cancelFlagsLateCancellations() {
        Appointment late = scheduledIn(5);
        assertTrue(late.cancel("Viaje", NOW));
        assertEquals(AppointmentStatus.CANCELLED, late.status());
        assertTrue(late.lateCancellation());
        assertEquals("Viaje", late.cancellationReason());

        Appointment early = scheduledIn(48);
        assertFalse(early.cancel("Cambio de planes", NOW));
        assertFalse(early.lateCancellation());
    }

    @Test
    void cancelRejectedWhenAlreadyStartedOrInactive() {
        Appointment started = scheduledIn(2);
        assertThrows(DomainException.class, () -> started.cancel("x", NOW.plusHours(3)));
        Appointment cancelled = scheduledIn(48);
        cancelled.cancel("x", NOW);
        assertThrows(DomainException.class, () -> cancelled.cancel("y", NOW));
        assertThrows(DomainException.class, () -> cancelled.reschedule(NOW.plusDays(5), NOW));
    }

    @Test
    void noShowOnlyAfterStartTime() {
        Appointment a = scheduledIn(2);
        assertThrows(DomainException.class, () -> a.markNoShow(NOW));
        a.markNoShow(NOW.plusHours(3));
        assertEquals(AppointmentStatus.NO_SHOW, a.status());
        assertFalse(a.status().isActive());
    }

    @Test
    void completeOnlyAfterStartTime() {
        Appointment a = scheduledIn(2);
        assertThrows(DomainException.class, () -> a.complete(NOW));
        a.complete(NOW.plusHours(3));
        assertEquals(AppointmentStatus.COMPLETED, a.status());
    }

    @Test
    void reminderFlagAndRestore() {
        Appointment a = scheduledIn(24);
        assertFalse(a.reminderSent());
        a.markReminderSent();
        assertTrue(a.reminderSent());

        Appointment r = Appointment.restore(9L, 1L, 7L, NOW, NOW.plusMinutes(30), AppointmentStatus.CONFIRMED,
                TriageLevel.HIGH, "x", null, false, 2, true);
        assertEquals(9L, r.id());
        assertEquals(2, r.rescheduleCount());
        assertEquals("x", r.reason());
        assertEquals(1L, r.patientId());
        assertEquals(7L, r.doctorId());
    }
}
