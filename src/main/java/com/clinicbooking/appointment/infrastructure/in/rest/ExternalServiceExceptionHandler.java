package com.clinicbooking.appointment.infrastructure.in.rest;

import com.clinicbooking.appointment.application.ExternalServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExternalServiceExceptionHandler {

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handle(ExternalServiceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
}
