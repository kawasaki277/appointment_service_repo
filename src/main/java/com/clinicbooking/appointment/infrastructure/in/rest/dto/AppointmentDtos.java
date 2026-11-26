package com.clinicbooking.appointment.infrastructure.in.rest.dto;

import com.clinicbooking.appointment.domain.Appointment;
import com.clinicbooking.appointment.domain.AppointmentStatus;
import com.clinicbooking.appointment.domain.TriageLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class AppointmentDtos {
    private AppointmentDtos() {
    }

    public record ScheduleRequest(@NotNull Long patientId, @NotNull Long doctorId, @NotNull LocalDateTime start,
                                  @Min(10) @Max(120) Integer durationMinutes, TriageLevel triage, String reason) {
    }

    public record RescheduleRequest(@NotNull LocalDateTime newStart) {
    }

    public record CancelRequest(String reason) {
    }

    public record AppointmentResponse(Long id, Long patientId, Long doctorId, LocalDateTime start, LocalDateTime end,
                                      AppointmentStatus status, TriageLevel triage, String reason,
                                      String cancellationReason, boolean lateCancellation, int rescheduleCount) {
        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(a.id(), a.patientId(), a.doctorId(), a.start(), a.end(), a.status(),
                    a.triage(), a.reason(), a.cancellationReason(), a.lateCancellation(), a.rescheduleCount());
        }
    }

    public record CancelResponse(AppointmentResponse appointment, boolean lateCancellation, String message) {
    }

    public record RemindersResponse(int sent) {
    }
}
