package com.clinicbooking.appointment.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "cit_appointments")
public class AppointmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long patientId;
    public Long doctorId;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public String status;
    public String triage;
    @Column(length = 1000)
    public String reason;
    @Column(length = 1000)
    public String cancellationReason;
    public boolean lateCancellation;
    public int rescheduleCount;
    public boolean reminderSent;
}
