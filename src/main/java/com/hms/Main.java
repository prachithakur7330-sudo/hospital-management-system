package com.hms;

import com.hms.dao.JdbcAppointmentDao;
import com.hms.dao.JdbcDoctorDao;
import com.hms.dao.JdbcPatientDao;
import com.hms.db.Database;
import com.hms.service.AppointmentService;
import com.hms.service.PatientService;
import com.hms.ui.ConsoleMenu;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Database db = Database.fromEnvironment();

        if (args.length > 0 && args[0].equals("--init-db")) {
            db.runScript(Path.of("sql/schema.sql"));
            db.runScript(Path.of("sql/seed.sql"));
            System.out.println("Schema created and sample data loaded.");
            return;
        }

        try (Connection ignored = db.getConnection()) {
            System.out.println("Connected to " + db.url());
        } catch (SQLException e) {
            System.err.println("Can't reach MySQL at " + db.url() + ": " + e.getMessage());
            System.err.println("Check db.properties or the HMS_DB_* environment variables.");
            System.exit(1);
        }

        Clock clock = Clock.systemDefaultZone();
        var patientDao = new JdbcPatientDao(db);
        var doctorDao = new JdbcDoctorDao(db);
        var appointmentDao = new JdbcAppointmentDao(db);

        new ConsoleMenu(
                new PatientService(patientDao, clock),
                new AppointmentService(patientDao, doctorDao, appointmentDao, clock),
                doctorDao,
                appointmentDao,
                new Scanner(System.in),
                System.out
        ).run();
    }
}
