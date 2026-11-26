package com.clinicbooking.appointment.infrastructure.in;

import com.clinicbooking.appointment.application.port.in.AppointmentUseCase;
import com.clinicbooking.appointment.application.port.in.AppointmentUseCase.CancellationResult;
import com.clinicbooking.appointment.application.port.in.AppointmentUseCase.ScheduleCommand;
import com.clinicbooking.appointment.domain.Appointment;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.TriageLevel;
import com.clinicbooking.appointment.infrastructure.in.rest.AppointmentController;
import com.clinicbooking.appointment.infrastructure.in.rest.ExternalServiceExceptionHandler;
import com.clinicbooking.appointment.infrastructure.in.rest.ReminderJob;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.AppointmentResponse;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.CancelRequest;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.CancelResponse;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.RescheduleRequest;
import com.clinicbooking.appointment.infrastructure.in.rest.dto.AppointmentDtos.ScheduleRequest;
import com.clinicbooking.appointment.application.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentControllerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Mock AppointmentUseCase useCase;

    private Appointment appointment(AppointmentStatus status) {
        return Appointment.restore(1L, 1L, 7L, START, START.plusMinutes(30), status, TriageLevel.LOW, "x", null, false, 0, false);
    }

    @Test
    void scheduleAppliesDefaults() {
        when(useCase.schedule(any())).thenReturn(appointment(AppointmentStatus.SCHEDULED));
        AppointmentController controller = new AppointmentController(useCase);
        AppointmentResponse r = controller.schedule(new ScheduleRequest(1L, 7L, START, null, null, "Control"));
        assertEquals(AppointmentStatus.SCHEDULED, r.status());

        ArgumentCaptor<ScheduleCommand> captor = ArgumentCaptor.forClass(ScheduleCommand.class);
        verify(useCase).schedule(captor.capture());
        assertEquals(30, captor.getValue().durationMinutes());
        assertEquals(TriageLevel.LOW, captor.getValue().triage());
    }

    @Test
    void stateTransitionEndpointsDelegate() {
        AppointmentController controller = new AppointmentController(useCase);
        when(useCase.get(1L)).thenReturn(appointment(AppointmentStatus.SCHEDULED));
        when(useCase.confirm(1L)).thenReturn(appointment(AppointmentStatus.CONFIRMED));
        when(useCase.reschedule(1L, START.plusDays(1))).thenReturn(appointment(AppointmentStatus.SCHEDULED));
        when(useCase.markNoShow(1L)).thenReturn(appointment(AppointmentStatus.NO_SHOW));
        when(useCase.complete(1L)).thenReturn(appointment(AppointmentStatus.COMPLETED));

        assertEquals(AppointmentStatus.SCHEDULED, controller.get(1L).status());
        assertEquals(AppointmentStatus.CONFIRMED, controller.confirm(1L).status());
        assertEquals(AppointmentStatus.SCHEDULED, controller.reschedule(1L, new RescheduleRequest(START.plusDays(1))).status());
        assertEquals(AppointmentStatus.NO_SHOW, controller.markNoShow(1L).status());
        assertEquals(AppointmentStatus.COMPLETED, controller.complete(1L).status());
    }

    @Test
    void cancelBuildsMessageDependingOnPolicy() {
        AppointmentController controller = new AppointmentController(useCase);
        when(useCase.cancel(1L, "viaje")).thenReturn(new CancellationResult(appointment(AppointmentStatus.CANCELLED), true));
        when(useCase.cancel(1L, null)).thenReturn(new CancellationResult(appointment(AppointmentStatus.CANCELLED), false));

        CancelResponse late = controller.cancel(1L, new CancelRequest("viaje"));
        assertTrue(late.lateCancellation());
        assertTrue(late.message().contains("tardía"));
        assertEquals("Cancelación sin penalidad", controller.cancel(1L, null).message());
    }

    @Test
    void dailyAgendaAndRemindersDelegate() {
        AppointmentController controller = new AppointmentController(useCase);
        LocalDate day = START.toLocalDate();
        when(useCase.dailyAgenda(day, 7L, AppointmentStatus.CONFIRMED)).thenReturn(List.of(appointment(AppointmentStatus.CONFIRMED)));
        when(useCase.sendDueReminders()).thenReturn(3);

        assertEquals(1, controller.dailyAgenda(day, 7L, AppointmentStatus.CONFIRMED).size());
        assertEquals(3, controller.dispatchDueReminders().sent());

        new ReminderJob(useCase).run();
        verify(useCase, org.mockito.Mockito.times(2)).sendDueReminders();
    }

    @Test
    void externalFailuresMapToBadGateway() {
        assertEquals(502, new ExternalServiceExceptionHandler().handle(new ExternalServiceException("caído", null)).getStatus());
    }
}
