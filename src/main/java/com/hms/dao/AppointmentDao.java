package com.hms.dao;

import com.hms.model.Appointment;
import com.hms.model.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AppointmentDao {
    Appointment add(Appointment appointment);

    Optional<Appointment> findById(long id);

    List<Appointment> findByDoctorAndDate(long doctorId, LocalDate date);

    List<Appointment> findByPatient(long patientId);

    /** True if the doctor already has a SCHEDULED appointment starting at this time. */
    boolean doctorHasActiveAt(long doctorId, LocalDateTime start);

    boolean patientHasActiveAt(long patientId, LocalDateTime start);

    boolean updateStatus(long id, AppointmentStatus status);

    /** Doctor name -> number of non-cancelled appointments between the two dates (inclusive). */
    Map<String, Integer> countPerDoctor(LocalDate from, LocalDate to);
}
