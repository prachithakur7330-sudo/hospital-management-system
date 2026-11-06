package com.hms.dao;

import com.hms.db.ConnectionProvider;
import com.hms.model.Gender;
import com.hms.model.Patient;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcPatientDao implements PatientDao {

    private static final String COLUMNS =
            "id, first_name, last_name, date_of_birth, gender, phone, blood_group";

    private final ConnectionProvider db;

    public JdbcPatientDao(ConnectionProvider db) {
        this.db = db;
    }

    @Override
    public Patient add(Patient p) {
        String sql = "INSERT INTO patients (first_name, last_name, date_of_birth, gender, phone, blood_group) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindFields(ps, p);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return p.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not add patient " + p.fullName(), e);
        }
    }

    @Override
    public Optional<Patient> findById(long id) {
        List<Patient> rows = query("SELECT " + COLUMNS + " FROM patients WHERE id = ?", id);
        return rows.stream().findFirst();
    }

    @Override
    public List<Patient> findAll() {
        return query("SELECT " + COLUMNS + " FROM patients ORDER BY last_name, first_name");
    }

    @Override
    public List<Patient> searchByName(String text) {
        String like = "%" + text.trim() + "%";
        return query("SELECT " + COLUMNS + " FROM patients "
                + "WHERE first_name LIKE ? OR last_name LIKE ? ORDER BY last_name, first_name", like, like);
    }

    @Override
    public boolean update(Patient p) {
        String sql = "UPDATE patients SET first_name = ?, last_name = ?, date_of_birth = ?, gender = ?, "
                + "phone = ?, blood_group = ? WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindFields(ps, p);
            ps.setLong(7, p.id());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update patient " + p.id(), e);
        }
    }

    @Override
    public boolean delete(long id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM patients WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete patient " + id, e);
        }
    }

    private void bindFields(PreparedStatement ps, Patient p) throws SQLException {
        ps.setString(1, p.firstName());
        ps.setString(2, p.lastName());
        ps.setDate(3, Date.valueOf(p.dateOfBirth()));
        ps.setString(4, p.gender().name());
        ps.setString(5, p.phone());
        if (p.bloodGroup() == null) {
            ps.setNull(6, Types.VARCHAR);
        } else {
            ps.setString(6, p.bloodGroup());
        }
    }

    private List<Patient> query(String sql, Object... params) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<Patient> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Patient query failed", e);
        }
    }

    private static Patient map(ResultSet rs) throws SQLException {
        return new Patient(
                rs.getLong("id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getDate("date_of_birth").toLocalDate(),
                Gender.valueOf(rs.getString("gender")),
                rs.getString("phone"),
                rs.getString("blood_group"));
    }
}
