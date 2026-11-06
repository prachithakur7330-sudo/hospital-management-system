package com.hms.service;

import com.hms.dao.AppointmentDao;
import com.hms.dao.DoctorDao;
import com.hms.dao.PatientDao;
import com.hms.model.Appointment;
import com.hms.model.AppointmentStatus;
import com.hms.model.Doctor;
import com.hms.model.Patient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Map-backed DAOs so the service rules can be tested without a database. */
final class InMemoryDaos {

    private InMemoryDaos() {
    }

    static class Patients implements PatientDao {
        final Map<Long, Patient> rows = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        public Patient add(Patient p) {
            Patient saved = p.withId(ids.incrementAndGet());
            rows.put(saved.id(), saved);
            return saved;
        }

        public Optional<Patient> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        public List<Patient> findAll() {
            return new ArrayList<>(rows.values());
        }

        public List<Patient> searchByName(String text) {
            String t = text.toLowerCase();
            return rows.values().stream().filter(p -> p.fullName().toLowerCase().contains(t)).toList();
        }

        public boolean update(Patient p) {
            return rows.replace(p.id(), p) != null;
        }

        public boolean delete(long id) {
            return rows.remove(id) != null;
        }
    }

    static class Doctors implements DoctorDao {
        final Map<Long, Doctor> rows = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        public Doctor add(Doctor d) {
            Doctor saved = d.withId(ids.incrementAndGet());
            rows.put(saved.id(), saved);
            return saved;
        }

        public Optional<Doctor> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        public List<Doctor> findAll() {
            return new ArrayList<>(rows.values());
        }

        public List<Doctor> findBySpecialization(String s) {
            return rows.values().stream().filter(d -> d.specialization().equals(s)).toList();
        }
    }

    static class Appointments implements AppointmentDao {
        final Map<Long, Appointment> rows = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        public Appointment add(Appointment a) {
            Appointment saved = a.withId(ids.incrementAndGet());
            rows.put(saved.id(), saved);
            return saved;
        }

        public Optional<Appointment> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        public List<Appointment> findByDoctorAndDate(long doctorId, LocalDate date) {
            return rows.values().stream()
                    .filter(a -> a.doctorId() == doctorId && a.startTime().toLocalDate().equals(date))
                    .sorted(Comparator.comparing(Appointment::startTime))
                    .toList();
        }

        public List<Appointment> findByPatient(long patientId) {
            return rows.values().stream().filter(a -> a.patientId() == patientId).toList();
        }

        public boolean doctorHasActiveAt(long doctorId, LocalDateTime start) {
            return rows.values().stream().anyMatch(a -> a.doctorId() == doctorId
                    && a.startTime().equals(start) && a.status() == AppointmentStatus.SCHEDULED);
        }

        public boolean patientHasActiveAt(long patientId, LocalDateTime start) {
            return rows.values().stream().anyMatch(a -> a.patientId() == patientId
                    && a.startTime().equals(start) && a.status() == AppointmentStatus.SCHEDULED);
        }

        public boolean updateStatus(long id, AppointmentStatus status) {
            Appointment a = rows.get(id);
            if (a == null) {
                return false;
            }
            rows.put(id, a.withStatus(status));
            return true;
        }

        public Map<String, Integer> countPerDoctor(LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException("only implemented in the JDBC DAO");
        }
    }
}
