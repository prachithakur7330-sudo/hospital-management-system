package com.hms.dao;

import com.hms.db.ConnectionProvider;
import com.hms.model.Doctor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcDoctorDao implements DoctorDao {

    private static final String COLUMNS = "id, full_name, specialization, phone, shift_start, shift_end";

    private final ConnectionProvider db;

    public JdbcDoctorDao(ConnectionProvider db) {
        this.db = db;
    }

    @Override
    public Doctor add(Doctor d) {
        String sql = "INSERT INTO doctors (full_name, specialization, phone, shift_start, shift_end) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.fullName());
            ps.setString(2, d.specialization());
            ps.setString(3, d.phone());
            ps.setTime(4, Time.valueOf(d.shiftStart()));
            ps.setTime(5, Time.valueOf(d.shiftEnd()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return d.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not add doctor " + d.fullName(), e);
        }
    }

    @Override
    public Optional<Doctor> findById(long id) {
        return query("SELECT " + COLUMNS + " FROM doctors WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public List<Doctor> findAll() {
        return query("SELECT " + COLUMNS + " FROM doctors ORDER BY specialization, full_name");
    }

    @Override
    public List<Doctor> findBySpecialization(String specialization) {
        return query("SELECT " + COLUMNS + " FROM doctors WHERE specialization = ? ORDER BY full_name",
                specialization);
    }

    private List<Doctor> query(String sql, Object... params) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<Doctor> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Doctor(
                            rs.getLong("id"),
                            rs.getString("full_name"),
                            rs.getString("specialization"),
                            rs.getString("phone"),
                            rs.getTime("shift_start").toLocalTime(),
                            rs.getTime("shift_end").toLocalTime()));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Doctor query failed", e);
        }
    }
}
