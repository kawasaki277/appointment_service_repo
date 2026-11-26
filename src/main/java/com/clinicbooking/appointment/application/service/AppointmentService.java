package com.clinicbooking.appointment.application.service;

import com.clinicbooking.appointment.application.port.in.AppointmentUseCase;
import com.clinicbooking.appointment.application.port.out.AppointmentRepositoryPort;
import com.clinicbooking.appointment.application.port.out.DoctorAgendaPort;
import com.clinicbooking.appointment.application.port.out.HolidayPort;
import com.clinicbooking.appointment.application.port.out.NotificationPort;
import com.clinicbooking.appointment.application.port.out.NotificationPort.NotificationMessage;
import com.clinicbooking.appointment.application.port.out.NotificationPort.ReminderMessage;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort.AttendanceOutcome;
import com.clinicbooking.appointment.application.port.out.PatientDirectoryPort.PatientContact;
import com.clinicbooking.appointment.domain.Appointment;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.DomainException;
import com.clinicbooking.appointment.domain.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class AppointmentService implements AppointmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppointmentRepositoryPort repository;
    private final PatientDirectoryPort patients;
    private final DoctorAgendaPort agenda;
    private final NotificationPort notifications;
    private final HolidayPort holidays;
    private final Clock clock;

    public AppointmentService(AppointmentRepositoryPort repository, PatientDirectoryPort patients,
                              DoctorAgendaPort agenda, NotificationPort notifications,
                              HolidayPort holidays, Clock clock) {
        this.repository = repository;
        this.patients = patients;
        this.agenda = agenda;
        this.notifications = notifications;
        this.holidays = holidays;
        this.clock = clock;
    }

    @Override
    public Appointment schedule(ScheduleCommand cmd) {
        Appointment appointment = Appointment.schedule(cmd.patientId(), cmd.doctorId(), cmd.start(),
                cmd.durationMinutes(), cmd.triage(), cmd.reason(), now());
        ensureNotHoliday(appointment.start().toLocalDate());
        ensureNoOverlap(appointment, null);
        patients.getContact(cmd.patientId()); // valida que el paciente exista

        Appointment saved = repository.save(appointment);
        try {
            agenda.reserve(saved.doctorId(), saved.id(), saved.start(), saved.end());
        } catch (RuntimeException e) {
            repository.deleteById(saved.id());
            throw e;
        }
        notifyPatient(saved, "Cita agendada",
                "Su cita fue agendada para el " + saved.start().format(FORMAT) + ".");
        return saved;
    }

    @Override
    public Appointment get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Cita no encontrada: " + id));
    }

    @Override
    public Appointment confirm(Long id) {
        Appointment appointment = get(id);
        appointment.confirm();
        Appointment saved = repository.save(appointment);
        notifyPatient(saved, "Cita confirmada",
                "Su asistencia a la cita del " + saved.start().format(FORMAT) + " quedó confirmada.");
        return saved;
    }

    @Override
    public Appointment reschedule(Long id, LocalDateTime newStart) {
        Appointment appointment = get(id);
        LocalDateTime oldStart = appointment.start();
        LocalDateTime oldEnd = appointment.end();

        appointment.reschedule(newStart, now());
        ensureNotHoliday(newStart.toLocalDate());
        ensureNoOverlap(appointment, appointment.id());

        agenda.release(id);
        try {
            agenda.reserve(appointment.doctorId(), id, appointment.start(), appointment.end());
        } catch (RuntimeException e) {
            agenda.reserve(appointment.doctorId(), id, oldStart, oldEnd); // compensación: restaura el cupo anterior
            throw e;
        }
        Appointment saved = repository.save(appointment);
        notifyPatient(saved, "Cita reprogramada",
                "Su cita fue reprogramada para el " + saved.start().format(FORMAT) + ".");
        return saved;
    }

    @Override
    public CancellationResult cancel(Long id, String reason) {
        Appointment appointment = get(id);
        boolean late = appointment.cancel(reason, now());
        Appointment saved = repository.save(appointment);
        agenda.release(id);
        recordAttendance(saved, AttendanceOutcome.CANCELLED);
        String suffix = late ? " Cancelación tardía (menos de 24 h): puede aplicar la política de la clínica." : "";
        notifyPatient(saved, "Cita cancelada",
                "Su cita del " + saved.start().format(FORMAT) + " fue cancelada." + suffix);
        return new CancellationResult(saved, late);
    }

    @Override
    public Appointment markNoShow(Long id) {
        Appointment appointment = get(id);
        appointment.markNoShow(now());
        Appointment saved = repository.save(appointment);
        recordAttendance(saved, AttendanceOutcome.NO_SHOW);
        return saved;
    }

    @Override
    public Appointment complete(Long id) {
        Appointment appointment = get(id);
        appointment.complete(now());
        Appointment saved = repository.save(appointment);
        recordAttendance(saved, AttendanceOutcome.ATTENDED);
        return saved;
    }

    @Override
    public List<Appointment> dailyAgenda(LocalDate date, Long doctorId, AppointmentStatus status) {
        return repository.findByDay(date).stream()
                .filter(a -> doctorId == null || a.doctorId().equals(doctorId))
                .filter(a -> status == null || a.status() == status)
                .toList();
    }

    @Override
    public int sendDueReminders() {
        LocalDateTime now = now();
        int sent = 0;
        for (Appointment a : repository.findPendingReminders(now.plusHours(23), now.plusHours(25))) {
            try {
                PatientContact c = patients.getContact(a.patientId());
                notifications.dispatchReminder(new ReminderMessage(a.id(), c.fullName(), c.email(), c.phone(), a.start()));
                a.markReminderSent();
                repository.save(a);
                sent++;
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el recordatorio de la cita {}: {}", a.id(), e.getMessage());
            }
        }
        return sent;
    }

    // ---- helpers ----
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void ensureNotHoliday(LocalDate date) {
        if (holidays.isHoliday(date)) {
            throw new DomainException("No se pueden agendar citas en días festivos (" + date + ")");
        }
    }

    private void ensureNoOverlap(Appointment candidate, Long excludeId) {
        boolean conflict = repository.findActiveOverlapping(candidate.doctorId(), candidate.start(), candidate.end())
                .stream().anyMatch(o -> !Objects.equals(o.id(), excludeId));
        if (conflict) {
            throw new DomainException("El médico ya tiene una cita en ese horario");
        }
    }

    /** Las notificaciones son "best effort": un fallo nunca revierte la operación clínica. */
    private void notifyPatient(Appointment a, String subject, String body) {
        try {
            PatientContact c = patients.getContact(a.patientId());
            notifications.notifyPatient(new NotificationMessage(a.id(), c.fullName(), c.email(), c.phone(), subject, body));
        } catch (RuntimeException e) {
            log.warn("Notificación no enviada para la cita {}: {}", a.id(), e.getMessage());
        }
    }

    private void recordAttendance(Appointment a, AttendanceOutcome outcome) {
        try {
            patients.recordAttendance(a.patientId(), a.id(), outcome, a.start().toLocalDate());
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la asistencia ({}) de la cita {}: {}", outcome, a.id(), e.getMessage());
        }
    }
}
