package com.hms.dao;

import com.hms.db.ConnectionProvider;
import com.hms.model.Appointment;
import com.hms.model.AppointmentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcAppointmentDao implements AppointmentDao {

    private static final String COLUMNS = "id, patient_id, doctor_id, start_time, status, reason";

    private final ConnectionProvider db;

    public JdbcAppointmentDao(ConnectionProvider db) {
        this.db = db;
    }

    @Override
    public Appointment add(Appointment a) {
        String sql = "INSERT INTO appointments (patient_id, doctor_id, start_time, status, reason) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.patientId());
            ps.setLong(2, a.doctorId());
            ps.setTimestamp(3, Timestamp.valueOf(a.startTime()));
            ps.setString(4, a.status().name());
            ps.setString(5, a.reason());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return a.withId(keys.getLong(1));
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // uq_doctor_active_slot fired: someone else grabbed the slot between our check and insert
            throw new SlotTakenException(a.doctorId(), a.startTime(), e);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save appointment", e);
        }
    }

    @Override
    public Optional<Appointment> findById(long id) {
        return query("SELECT " + COLUMNS + " FROM appointments WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public List<Appointment> findByDoctorAndDate(long doctorId, LocalDate date) {
        return query("SELECT " + COLUMNS + " FROM appointments "
                        + "WHERE doctor_id = ? AND start_time >= ? AND start_time < ? ORDER BY start_time",
                doctorId, Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
    }

    @Override
    public List<Appointment> findByPatient(long patientId) {
        return query("SELECT " + COLUMNS + " FROM appointments WHERE patient_id = ? ORDER BY start_time DESC",
                patientId);
    }

    @Override
    public boolean doctorHasActiveAt(long doctorId, LocalDateTime start) {
        return exists("SELECT 1 FROM appointments WHERE doctor_id = ? AND start_time = ? AND status = 'SCHEDULED'",
                doctorId, Timestamp.valueOf(start));
    }

    @Override
    public boolean patientHasActiveAt(long patientId, LocalDateTime start) {
        return exists("SELECT 1 FROM appointments WHERE patient_id = ? AND start_time = ? AND status = 'SCHEDULED'",
                patientId, Timestamp.valueOf(start));
    }

    @Override
    public boolean updateStatus(long id, AppointmentStatus status) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE appointments SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update appointment " + id, e);
        }
    }

    @Override
    public Map<String, Integer> countPerDoctor(LocalDate from, LocalDate to) {
        String sql = "SELECT d.full_name, COUNT(a.id) AS total "
                + "FROM doctors d LEFT JOIN appointments a "
                + "  ON a.doctor_id = d.id AND a.status <> 'CANCELLED' AND a.start_time >= ? AND a.start_time < ? "
                + "GROUP BY d.id, d.full_name ORDER BY total DESC, d.full_name";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            Map<String, Integer> out = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("full_name"), rs.getInt("total"));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Report query failed", e);
        }
    }

    private boolean exists(String sql, Object... params) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Appointment lookup failed", e);
        }
    }

    private List<Appointment> query(String sql, Object... params) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<Appointment> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Appointment(
                            rs.getLong("id"),
                            rs.getLong("patient_id"),
                            rs.getLong("doctor_id"),
                            rs.getTimestamp("start_time").toLocalDateTime(),
                            AppointmentStatus.valueOf(rs.getString("status")),
                            rs.getString("reason")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Appointment query failed", e);
        }
    }
}
