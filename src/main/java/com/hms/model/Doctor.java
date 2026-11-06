package com.hms.model;

import java.time.LocalTime;

public record Doctor(
        Long id,
        String fullName,
        String specialization,
        String phone,
        LocalTime shiftStart,
        LocalTime shiftEnd) {

    public Doctor withId(long newId) {
        return new Doctor(newId, fullName, specialization, phone, shiftStart, shiftEnd);
    }
}
