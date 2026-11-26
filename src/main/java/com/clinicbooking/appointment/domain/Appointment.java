package com.clinicbooking.appointment.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/** Agregado Cita: encapsula el ciclo de vida y las reglas de agenda. */
public class Appointment {

    public static final Duration MIN_ADVANCE = Duration.ofHours(1);
    public static final Duration FREE_CANCELLATION_WINDOW = Duration.ofHours(24);
    public static final int MAX_RESCHEDULES = 3;

    private Long id;
    private final Long patientId;
    private final Long doctorId;
    private LocalDateTime start;
    private LocalDateTime end;
    private AppointmentStatus status;
    private final TriageLevel triage;
    private final String reason;
    private String cancellationReason;
    private boolean lateCancellation;
    private int rescheduleCount;
    private boolean reminderSent;

    private Appointment(Long id, Long patientId, Long doctorId, LocalDateTime start, LocalDateTime end,
                        AppointmentStatus status, TriageLevel triage, String reason, String cancellationReason,
                        boolean lateCancellation, int rescheduleCount, boolean reminderSent) {
        this.id = id;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.start = start;
        this.end = end;
        this.status = status;
        this.triage = triage;
        this.reason = reason;
        this.cancellationReason = cancellationReason;
        this.lateCancellation = lateCancellation;
        this.rescheduleCount = rescheduleCount;
        this.reminderSent = reminderSent;
    }

    public static Appointment schedule(Long patientId, Long doctorId, LocalDateTime start, int durationMinutes,
                                       TriageLevel triage, String reason, LocalDateTime now) {
        if (patientId == null || doctorId == null || start == null) {
            throw new DomainException("Paciente, médico y fecha de inicio son obligatorios");
        }
        if (durationMinutes < 10 || durationMinutes > 120) {
            throw new DomainException("La duración debe estar entre 10 y 120 minutos");
        }
        TriageLevel level = triage == null ? TriageLevel.LOW : triage;
        boolean urgent = level == TriageLevel.URGENT;
        if (urgent ? start.isBefore(now) : start.isBefore(now.plus(MIN_ADVANCE))) {
            throw new DomainException(urgent
                    ? "La cita urgente no puede agendarse en el pasado"
                    : "La cita debe agendarse con al menos 1 hora de antelación");
        }
        return new Appointment(null, patientId, doctorId, start, start.plusMinutes(durationMinutes),
                AppointmentStatus.SCHEDULED, level, reason, null, false, 0, false);
    }

    public static Appointment restore(Long id, Long patientId, Long doctorId, LocalDateTime start, LocalDateTime end,
                                      AppointmentStatus status, TriageLevel triage, String reason,
                                      String cancellationReason, boolean lateCancellation, int rescheduleCount,
                                      boolean reminderSent) {
        return new Appointment(id, patientId, doctorId, start, end, status, triage, reason,
                cancellationReason, lateCancellation, rescheduleCount, reminderSent);
    }

    // ---- transiciones de estado ----
    public void confirm() {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new DomainException("Solo se puede confirmar una cita en estado SCHEDULED (actual: " + status + ")");
        }
        status = AppointmentStatus.CONFIRMED;
    }

    public void reschedule(LocalDateTime newStart, LocalDateTime now) {
        requireActive("reprogramar");
        if (rescheduleCount >= MAX_RESCHEDULES) {
            throw new DomainException("La cita alcanzó el máximo de " + MAX_RESCHEDULES + " reprogramaciones");
        }
        if (Duration.between(now, start).compareTo(MIN_ADVANCE) < 0) {
            throw new DomainException("No se puede reprogramar con menos de 1 hora de antelación");
        }
        if (Duration.between(now, newStart).compareTo(MIN_ADVANCE) < 0) {
            throw new DomainException("La nueva fecha debe tener al menos 1 hora de antelación");
        }
        long minutes = Duration.between(start, end).toMinutes();
        start = newStart;
        end = newStart.plusMinutes(minutes);
        status = AppointmentStatus.SCHEDULED;
        rescheduleCount++;
        reminderSent = false;
    }

    /** @return true si la cancelación fue tardía (menos de 24 h de antelación). */
    public boolean cancel(String why, LocalDateTime now) {
        requireActive("cancelar");
        if (!now.isBefore(start)) {
            throw new DomainException("No se puede cancelar una cita que ya inició");
        }
        lateCancellation = Duration.between(now, start).compareTo(FREE_CANCELLATION_WINDOW) < 0;
        cancellationReason = why;
        status = AppointmentStatus.CANCELLED;
        return lateCancellation;
    }

    public void markNoShow(LocalDateTime now) {
        requireActive("marcar inasistencia de");
        if (now.isBefore(start)) {
            throw new DomainException("Aún no es la hora de la cita; no se puede marcar inasistencia");
        }
        status = AppointmentStatus.NO_SHOW;
    }

    public void complete(LocalDateTime now) {
        requireActive("completar");
        if (now.isBefore(start)) {
            throw new DomainException("La cita aún no ha iniciado");
        }
        status = AppointmentStatus.COMPLETED;
    }

    public void markReminderSent() {
        reminderSent = true;
    }

    public void assignId(Long newId) {
        this.id = newId;
    }

    private void requireActive(String action) {
        if (!status.isActive()) {
            throw new DomainException("No se puede " + action + " una cita en estado " + status);
        }
    }

    public Long id() { return id; }
    public Long patientId() { return patientId; }
    public Long doctorId() { return doctorId; }
    public LocalDateTime start() { return start; }
    public LocalDateTime end() { return end; }
    public AppointmentStatus status() { return status; }
    public TriageLevel triage() { return triage; }
    public String reason() { return reason; }
    public String cancellationReason() { return cancellationReason; }
    public boolean lateCancellation() { return lateCancellation; }
    public int rescheduleCount() { return rescheduleCount; }
    public boolean reminderSent() { return reminderSent; }
}
