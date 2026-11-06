package com.hms.service;

import com.hms.model.Appointment;
import com.hms.model.AppointmentStatus;
import com.hms.model.Doctor;
import com.hms.model.Gender;
import com.hms.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentServiceTest {

    // "now" is pinned to 1 March 2025, 08:00 so the tests don't depend on the real date
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2025, 3, 1, 8, 0).atZone(ZONE).toInstant(), ZONE);
    private static final LocalDate DAY = LocalDate.of(2025, 3, 3);

    private InMemoryDaos.Patients patients;
    private InMemoryDaos.Doctors doctors;
    private InMemoryDaos.Appointments appointments;
    private AppointmentService service;
    private long patientId;
    private long otherPatientId;
    private long doctorId;

    @BeforeEach
    void setUp() {
        patients = new InMemoryDaos.Patients();
        doctors = new InMemoryDaos.Doctors();
        appointments = new InMemoryDaos.Appointments();
        service = new AppointmentService(patients, doctors, appointments, CLOCK);

        patientId = patients.add(new Patient(null, "Aarav", "Sharma", LocalDate.of(1991, 4, 12),
                Gender.MALE, "9900011122", "B+")).id();
        otherPatientId = patients.add(new Patient(null, "Meera", "Pillai", LocalDate.of(1985, 11, 3),
                Gender.FEMALE, "9900011133", "O+")).id();
        doctorId = doctors.add(new Doctor(null, "Dr. Anjali Mehra", "Cardiology", "9810001001",
                LocalTime.of(9, 0), LocalTime.of(11, 0))).id();
    }

    @Test
    void booksAFreeSlot() {
        Appointment a = service.book(patientId, doctorId, DAY.atTime(9, 30), "chest pain");
        assertEquals(AppointmentStatus.SCHEDULED, a.status());
        assertEquals(1, appointments.rows.size());
    }

    @Test
    void doctorCannotBeDoubleBooked() {
        service.book(patientId, doctorId, DAY.atTime(10, 0), "follow-up");
        BookingException e = assertThrows(BookingException.class,
                () -> service.book(otherPatientId, doctorId, DAY.atTime(10, 0), "ecg"));
        assertTrue(e.getMessage().contains("already booked"));
    }

    @Test
    void patientCannotBeInTwoPlacesAtOnce() {
        long secondDoctor = doctors.add(new Doctor(null, "Dr. Rohan Iyer", "Orthopaedics", "9810001002",
                LocalTime.of(9, 0), LocalTime.of(12, 0))).id();
        service.book(patientId, doctorId, DAY.atTime(9, 0), "bp check");
        assertThrows(BookingException.class,
                () -> service.book(patientId, secondDoctor, DAY.atTime(9, 0), "knee"));
    }

    @Test
    void rejectsTimesOutsideTheShift() {
        assertThrows(BookingException.class, () -> service.book(patientId, doctorId, DAY.atTime(8, 30), ""));
        // 10:30 is the last slot that still ends by 11:00
        service.book(patientId, doctorId, DAY.atTime(10, 30), "");
        assertThrows(BookingException.class, () -> service.book(patientId, doctorId, DAY.atTime(11, 0), ""));
    }

    @Test
    void rejectsOddStartTimes() {
        assertThrows(BookingException.class, () -> service.book(patientId, doctorId, DAY.atTime(9, 15), ""));
    }

    @Test
    void rejectsPastSlots() {
        LocalDateTime yesterday = LocalDateTime.of(2025, 2, 28, 9, 0);
        assertThrows(BookingException.class, () -> service.book(patientId, doctorId, yesterday, ""));
    }

    @Test
    void rejectsUnknownPatientOrDoctor() {
        assertThrows(BookingException.class, () -> service.book(999, doctorId, DAY.atTime(9, 0), ""));
        assertThrows(BookingException.class, () -> service.book(patientId, 999, DAY.atTime(9, 0), ""));
    }

    @Test
    void cancelledSlotCanBeRebooked() {
        Appointment first = service.book(patientId, doctorId, DAY.atTime(9, 0), "");
        service.cancel(first.id());
        Appointment second = service.book(otherPatientId, doctorId, DAY.atTime(9, 0), "");
        assertEquals(AppointmentStatus.SCHEDULED, second.status());
    }

    @Test
    void onlyScheduledAppointmentsChangeStatus() {
        Appointment a = service.book(patientId, doctorId, DAY.atTime(9, 0), "");
        service.complete(a.id());
        assertThrows(BookingException.class, () -> service.cancel(a.id()));
        assertEquals(AppointmentStatus.COMPLETED, appointments.findById(a.id()).orElseThrow().status());
    }

    @Test
    void freeSlotsSkipBookedTimes() {
        service.book(patientId, doctorId, DAY.atTime(9, 30), "");
        List<LocalTime> free = service.freeSlots(doctorId, DAY);
        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(10, 30)), free);
    }

    @Test
    void freeSlotsHandleShiftsEndingNearMidnight() {
        long night = doctors.add(new Doctor(null, "Dr. Night", "Emergency", "9810001009",
                LocalTime.of(22, 45), LocalTime.of(23, 59))).id();
        // first slot rounds up to 23:00; a 23:30 slot would end at midnight, after the 23:59 shift end
        assertEquals(List.of(LocalTime.of(23, 0)), service.freeSlots(night, DAY));
        assertFalse(service.freeSlots(night, DAY).contains(LocalTime.of(23, 30)));
    }
}
