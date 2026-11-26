package com.clinicbooking.appointment.infrastructure.in.rest;

import com.clinicbooking.appointment.application.port.in.AppointmentUseCase;
import com.clinicbooking.appointment.application.port.in.AppointmentUseCase.CancellationResult;
import com.clinicbooking.appointment.application.port.in.AppointmentUseCase.ScheduleCommand;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.TriageLevel;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.AppointmentResponse;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.CancelRequest;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.CancelResponse;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.RemindersResponse;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.RescheduleRequest;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.ScheduleRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private static final int DEFAULT_DURATION_MINUTES = 30;

    private final AppointmentUseCase useCase;

    public AppointmentController(AppointmentUseCase useCase) {
        this.useCase = useCase;
    }

    // ---- CRUD básico (crear / consultar) ----
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse schedule(@Valid @RequestBody ScheduleRequest r) {
        int duration = r.durationMinutes() == null ? DEFAULT_DURATION_MINUTES : r.durationMinutes();
        TriageLevel triage = r.triage() == null ? TriageLevel.LOW : r.triage();
        return AppointmentResponse.from(useCase.schedule(
                new ScheduleCommand(r.patientId(), r.doctorId(), r.start(), duration, triage, r.reason())));
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id) {
        return AppointmentResponse.from(useCase.get(id));
    }

    // ---- APIs de negocio (no-CRUD) ----
    @PostMapping("/{id}/confirm")
    public AppointmentResponse confirm(@PathVariable Long id) {
        return AppointmentResponse.from(useCase.confirm(id));
    }

    @PostMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest r) {
        return AppointmentResponse.from(useCase.reschedule(id, r.newStart()));
    }

    @PostMapping("/{id}/cancel")
    public CancelResponse cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest r) {
        CancellationResult result = useCase.cancel(id, r == null ? null : r.reason());
        String message = result.lateCancellation()
                ? "Cancelación tardía: se aplicó la política de cancelación"
                : "Cancelación sin penalidad";
        return new CancelResponse(AppointmentResponse.from(result.appointment()), result.lateCancellation(), message);
    }

    @PostMapping("/{id}/mark-no-show")
    public AppointmentResponse markNoShow(@PathVariable Long id) {
        return AppointmentResponse.from(useCase.markNoShow(id));
    }

    @GetMapping("/daily-agenda")
    public List<AppointmentResponse> dailyAgenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) AppointmentStatus status) {
        return useCase.dailyAgenda(date, doctorId, status).stream().map(AppointmentResponse::from).toList();
    }

    // ---- Operación adicional: cierre de cita y recordatorios ----
    @PostMapping("/{id}/complete")
    public AppointmentResponse complete(@PathVariable Long id) {
        return AppointmentResponse.from(useCase.complete(id));
    }

    @PostMapping("/reminders/dispatch-due")
    public RemindersResponse dispatchDueReminders() {
        return new RemindersResponse(useCase.sendDueReminders());
    }
}
