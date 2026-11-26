package com.clinicbooking.appointment.application;

/** Fallo de un servicio externo (red, timeout, 5xx). */
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
