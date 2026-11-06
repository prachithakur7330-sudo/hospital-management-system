package com.hms.service;

import com.hms.dao.AppointmentDao;
import com.hms.dao.DoctorDao;
import com.hms.dao.PatientDao;
import com.hms.dao.SlotTakenException;
import com.hms.model.Appointment;
import com.hms.model.AppointmentStatus;
import com.hms.model.Doctor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Booking rules live here rather than in the DAOs:
 *  - appointments are fixed 30 minute slots starting on :00 or :30
 *  - the whole slot has to fit inside the doctor's shift
 *  - no booking in the past
 *  - a doctor can't have two live appointments in one slot, and neither can a patient
 */
public class AppointmentService {

    public static final int SLOT_MINUTES = 30;

    private final PatientDao patients;
    private final DoctorDao doctors;
    private final AppointmentDao appointments;
    private final Clock clock;

    public AppointmentService(PatientDao patients, DoctorDao doctors, AppointmentDao appointments, Clock clock) {
        this.patients = patients;
        this.doctors = doctors;
        this.appointments = appointments;
        this.clock = clock;
    }

    public Appointment book(long patientId, long doctorId, LocalDateTime start, String reason) {
        if (patients.findById(patientId).isEmpty()) {
            throw new BookingException("No patient with id " + patientId);
        }
        Doctor doctor = doctors.findById(doctorId)
                .orElseThrow(() -> new BookingException("No doctor with id " + doctorId));

        if (start.isBefore(LocalDateTime.now(clock))) {
            throw new BookingException("Can't book a slot in the past");
        }
        if (start.getMinute() % SLOT_MINUTES != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            throw new BookingException("Appointments start on the hour or half hour");
        }
        LocalTime t = start.toLocalTime();
        if (!fitsShift(doctor, t)) {
            throw new BookingException(doctor.fullName() + " only sees patients between "
                    + doctor.shiftStart() + " and " + doctor.shiftEnd());
        }
        if (appointments.doctorHasActiveAt(doctorId, start)) {
            throw new BookingException(doctor.fullName() + " is already booked at " + t);
        }
        if (appointments.patientHasActiveAt(patientId, start)) {
            throw new BookingException("Patient already has another appointment at " + t);
        }

        Appointment draft = new Appointment(null, patientId, doctorId, start, AppointmentStatus.SCHEDULED, reason);
        try {
            return appointments.add(draft);
        } catch (SlotTakenException e) {
            // lost a race with another booking; the DB constraint caught it
            throw new BookingException(doctor.fullName() + " is already booked at " + t);
        }
    }

    public void cancel(long appointmentId) {
        changeStatus(appointmentId, AppointmentStatus.CANCELLED);
    }

    public void complete(long appointmentId) {
        changeStatus(appointmentId, AppointmentStatus.COMPLETED);
    }

    /** Open slots for a doctor on a given day. Past slots are left out when the day is today. */
    public List<LocalTime> freeSlots(long doctorId, LocalDate date) {
        Doctor doctor = doctors.findById(doctorId)
                .orElseThrow(() -> new BookingException("No doctor with id " + doctorId));

        Set<LocalTime> taken = new HashSet<>();
        for (Appointment a : appointments.findByDoctorAndDate(doctorId, date)) {
            if (a.status() == AppointmentStatus.SCHEDULED) {
                taken.add(a.startTime().toLocalTime());
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalTime> free = new ArrayList<>();
        // work in minutes-of-day so a late shift can't wrap past midnight
        int first = roundUpToSlot(minuteOfDay(doctor.shiftStart()));
        int end = minuteOfDay(doctor.shiftEnd());
        for (int m = first; m + SLOT_MINUTES <= end; m += SLOT_MINUTES) {
            LocalTime slot = LocalTime.of(m / 60, m % 60);
            if (!taken.contains(slot) && !date.atTime(slot).isBefore(now)) {
                free.add(slot);
            }
        }
        return free;
    }

    private static boolean fitsShift(Doctor doctor, LocalTime t) {
        int m = minuteOfDay(t);
        return m >= minuteOfDay(doctor.shiftStart()) && m + SLOT_MINUTES <= minuteOfDay(doctor.shiftEnd());
    }

    private static int minuteOfDay(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private static int roundUpToSlot(int minutes) {
        return (minutes + SLOT_MINUTES - 1) / SLOT_MINUTES * SLOT_MINUTES;
    }

    private void changeStatus(long appointmentId, AppointmentStatus target) {
        Appointment appt = appointments.findById(appointmentId)
                .orElseThrow(() -> new BookingException("No appointment with id " + appointmentId));
        if (appt.status() != AppointmentStatus.SCHEDULED) {
            throw new BookingException("Appointment " + appointmentId + " is already " + appt.status());
        }
        appointments.updateStatus(appointmentId, target);
    }
}
