package com.clinicbooking.appointment.domain;

public enum AppointmentStatus {
    SCHEDULED, CONFIRMED, CANCELLED, NO_SHOW, COMPLETED;

    /** Una cita activa aún ocupa cupo y admite transiciones. */
    public boolean isActive() {
        return this == SCHEDULED || this == CONFIRMED;
    }
}
