package com.hms.dao;

import com.hms.db.Database;
import com.hms.model.Appointment;
import com.hms.model.AppointmentStatus;
import com.hms.model.Doctor;
import com.hms.model.Gender;
import com.hms.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs against a real MySQL. Skipped unless HMS_TEST_DB_URL is set, e.g.
 *   HMS_TEST_DB_URL=jdbc:mysql://localhost:3306/hms HMS_TEST_DB_USER=root HMS_TEST_DB_PASSWORD=... mvn test
 * WARNING: it drops and recreates the tables, so point it at a scratch database.
 */
class JdbcDaoIntegrationTest {

    private Database db;
    private JdbcPatientDao patients;
    private JdbcDoctorDao doctors;
    private JdbcAppointmentDao appointments;

    @BeforeEach
    void resetSchema() throws Exception {
        String url = System.getenv("HMS_TEST_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "HMS_TEST_DB_URL not set, skipping MySQL tests");
        db = new Database(url, System.getenv("HMS_TEST_DB_USER"), System.getenv("HMS_TEST_DB_PASSWORD"));
        db.runScript(Path.of("sql/schema.sql"));
        patients = new JdbcPatientDao(db);
        doctors = new JdbcDoctorDao(db);
        appointments = new JdbcAppointmentDao(db);
    }

    private Patient newPatient(String first, String phone) {
        return patients.add(new Patient(null, first, "Test", LocalDate.of(1990, 1, 1), Gender.FEMALE, phone, "O+"));
    }

    private Doctor newDoctor(String name) {
        return doctors.add(new Doctor(null, name, "Cardiology", "9810000000", LocalTime.of(9, 0), LocalTime.of(13, 0)));
    }

    @Test
    void patientCrudRoundTrip() {
        Patient p = newPatient("Riya", "9900000001");
        assertEquals("Riya", patients.findById(p.id()).orElseThrow().firstName());
        assertEquals(1, patients.searchByName("riy").size());

        assertTrue(patients.update(p.withPhone("9900000002")));
        assertEquals("9900000002", patients.findById(p.id()).orElseThrow().phone());

        assertTrue(patients.delete(p.id()));
        assertTrue(patients.findById(p.id()).isEmpty());
    }

    @Test
    void databaseBlocksDoubleBookingEvenIfServiceIsBypassed() {
        Patient a = newPatient("Riya", "9900000001");
        Patient b = newPatient("Tara", "9900000003");
        Doctor d = newDoctor("Dr. Test");
        LocalDateTime slot = LocalDateTime.of(2030, 1, 7, 10, 0);

        appointments.add(new Appointment(null, a.id(), d.id(), slot, AppointmentStatus.SCHEDULED, "x"));
        assertThrows(SlotTakenException.class, () ->
                appointments.add(new Appointment(null, b.id(), d.id(), slot, AppointmentStatus.SCHEDULED, "y")));
    }

    @Test
    void cancelledSlotFreesTheUniqueKey() {
        Patient a = newPatient("Riya", "9900000001");
        Doctor d = newDoctor("Dr. Test");
        LocalDateTime slot = LocalDateTime.of(2030, 1, 7, 10, 0);

        Appointment first = appointments.add(new Appointment(null, a.id(), d.id(), slot, AppointmentStatus.SCHEDULED, ""));
        appointments.updateStatus(first.id(), AppointmentStatus.CANCELLED);
        appointments.add(new Appointment(null, a.id(), d.id(), slot, AppointmentStatus.SCHEDULED, ""));

        assertEquals(2, appointments.findByDoctorAndDate(d.id(), slot.toLocalDate()).size());
        assertTrue(appointments.doctorHasActiveAt(d.id(), slot));
    }

    @Test
    void reportCountsOnlyLiveAppointmentsInRange() {
        Patient p = newPatient("Riya", "9900000001");
        Doctor busy = newDoctor("Dr. Busy");
        newDoctor("Dr. Idle");
        LocalDate day = LocalDate.of(2030, 1, 7);

        appointments.add(new Appointment(null, p.id(), busy.id(), day.atTime(9, 0), AppointmentStatus.SCHEDULED, ""));
        appointments.add(new Appointment(null, p.id(), busy.id(), day.atTime(9, 30), AppointmentStatus.COMPLETED, ""));
        Appointment c = appointments.add(new Appointment(null, p.id(), busy.id(), day.atTime(10, 0), AppointmentStatus.SCHEDULED, ""));
        appointments.updateStatus(c.id(), AppointmentStatus.CANCELLED);
        appointments.add(new Appointment(null, p.id(), busy.id(), day.plusDays(10).atTime(9, 0), AppointmentStatus.SCHEDULED, ""));

        Map<String, Integer> counts = appointments.countPerDoctor(day, day);
        assertEquals(2, counts.get("Dr. Busy"));
        assertEquals(0, counts.get("Dr. Idle"));
    }

    @Test
    void deletingPatientRemovesTheirAppointments() {
        Patient p = newPatient("Riya", "9900000001");
        Doctor d = newDoctor("Dr. Test");
        appointments.add(new Appointment(null, p.id(), d.id(), LocalDateTime.of(2030, 1, 7, 9, 0),
                AppointmentStatus.SCHEDULED, ""));
        patients.delete(p.id());
        assertTrue(appointments.findByPatient(p.id()).isEmpty());
    }
}
