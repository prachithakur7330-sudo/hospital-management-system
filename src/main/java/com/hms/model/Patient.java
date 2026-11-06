package com.hms.model;

import java.time.LocalDate;
import java.time.Period;

public record Patient(
        Long id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String bloodGroup) {

    public Patient withId(long newId) {
        return new Patient(newId, firstName, lastName, dateOfBirth, gender, phone, bloodGroup);
    }

    public Patient withPhone(String newPhone) {
        return new Patient(id, firstName, lastName, dateOfBirth, gender, newPhone, bloodGroup);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public int ageOn(LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }
}
