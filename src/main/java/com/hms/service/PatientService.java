package com.hms.service;

import com.hms.dao.PatientDao;
import com.hms.model.Patient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class PatientService {

    private static final Pattern PHONE = Pattern.compile("[6-9]\\d{9}");   // Indian mobile numbers
    private static final Set<String> BLOOD_GROUPS = Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");

    private final PatientDao patients;
    private final Clock clock;

    public PatientService(PatientDao patients, Clock clock) {
        this.patients = patients;
        this.clock = clock;
    }

    public Patient register(Patient p) {
        validate(p);
        return patients.add(p);
    }

    public Optional<Patient> find(long id) {
        return patients.findById(id);
    }

    public List<Patient> all() {
        return patients.findAll();
    }

    public List<Patient> search(String name) {
        if (name == null || name.isBlank()) {
            return patients.findAll();
        }
        return patients.searchByName(name);
    }

    public Patient updatePhone(long id, String phone) {
        Patient current = patients.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No patient with id " + id));
        Patient updated = current.withPhone(phone);
        validate(updated);
        patients.update(updated);
        return updated;
    }

    public boolean remove(long id) {
        return patients.delete(id);
    }

    void validate(Patient p) {
        if (p.firstName() == null || p.firstName().isBlank() || p.lastName() == null || p.lastName().isBlank()) {
            throw new IllegalArgumentException("First and last name are required");
        }
        if (p.dateOfBirth() == null || p.dateOfBirth().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Date of birth can't be in the future");
        }
        if (p.phone() == null || !PHONE.matcher(p.phone()).matches()) {
            throw new IllegalArgumentException("Phone should be a 10 digit mobile number");
        }
        if (p.bloodGroup() != null && !BLOOD_GROUPS.contains(p.bloodGroup())) {
            throw new IllegalArgumentException("Unknown blood group: " + p.bloodGroup());
        }
    }
}
