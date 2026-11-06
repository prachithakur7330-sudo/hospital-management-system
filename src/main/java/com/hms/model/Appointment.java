package com.hms.model;

import java.time.LocalDateTime;

public record Appointment(
        Long id,
        long patientId,
        long doctorId,
        LocalDateTime startTime,
        AppointmentStatus status,
        String reason) {

    public Appointment withId(long newId) {
        return new Appointment(newId, patientId, doctorId, startTime, status, reason);
    }

    public Appointment withStatus(AppointmentStatus newStatus) {
        return new Appointment(id, patientId, doctorId, startTime, newStatus, reason);
    }
}
