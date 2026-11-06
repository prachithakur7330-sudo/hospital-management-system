package com.hms.dao;

import com.hms.model.Doctor;

import java.util.List;
import java.util.Optional;

public interface DoctorDao {
    Doctor add(Doctor doctor);

    Optional<Doctor> findById(long id);

    List<Doctor> findAll();

    List<Doctor> findBySpecialization(String specialization);
}
