package com.clinicbooking.appointment.application.port.out;

import java.time.LocalDate;

/** Consulta de pacientes y registro de asistencia en patient-service. */
public interface PatientDirectoryPort {

    PatientContact getContact(Long patientId);

    void recordAttendance(Long patientId, Long appointmentId, AttendanceOutcome outcome, LocalDate date);

    record PatientContact(Long id, String fullName, String email, String phone) {
    }

    enum AttendanceOutcome { ATTENDED, NO_SHOW, CANCELLED }
}
