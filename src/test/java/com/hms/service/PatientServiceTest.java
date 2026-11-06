package com.hms.service;

import com.hms.model.Gender;
import com.hms.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatientServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2025-03-01T00:00:00Z"), ZoneOffset.UTC);
    private InMemoryDaos.Patients dao;
    private PatientService service;

    @BeforeEach
    void setUp() {
        dao = new InMemoryDaos.Patients();
        service = new PatientService(dao, clock);
    }

    private Patient patient(String phone, String blood, LocalDate dob) {
        return new Patient(null, "Kabir", "Singh", dob, Gender.MALE, phone, blood);
    }

    @Test
    void registersValidPatient() {
        Patient saved = service.register(patient("9900011144", "A-", LocalDate.of(2016, 7, 21)));
        assertEquals(1L, saved.id());
        assertEquals(8, saved.ageOn(LocalDate.now(clock)));
    }

    @Test
    void rejectsBadPhoneNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register(patient("12345", null, LocalDate.of(2000, 1, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(patient("1234567890", null, LocalDate.of(2000, 1, 1))));
    }

    @Test
    void rejectsFutureBirthDateAndUnknownBloodGroup() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register(patient("9900011144", null, LocalDate.of(2030, 1, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(patient("9900011144", "C+", LocalDate.of(2000, 1, 1))));
    }

    @Test
    void updatesPhone() {
        Patient saved = service.register(patient("9900011144", null, LocalDate.of(2000, 1, 1)));
        service.updatePhone(saved.id(), "9812345678");
        assertEquals("9812345678", dao.findById(saved.id()).orElseThrow().phone());
    }
}
