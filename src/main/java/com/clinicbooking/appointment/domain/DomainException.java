package com.clinicbooking.appointment.domain;

/** Violación de una regla de negocio. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
