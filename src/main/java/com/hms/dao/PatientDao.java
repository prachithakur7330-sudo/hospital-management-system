package com.hms.dao;

import com.hms.model.Patient;

import java.util.List;
import java.util.Optional;

public interface PatientDao {
    Patient add(Patient patient);

    Optional<Patient> findById(long id);

    List<Patient> findAll();

    List<Patient> searchByName(String text);

    boolean update(Patient patient);

    boolean delete(long id);
}
