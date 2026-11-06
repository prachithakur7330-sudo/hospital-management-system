package com.hms.ui;

import com.hms.dao.AppointmentDao;
import com.hms.dao.DataAccessException;
import com.hms.dao.DoctorDao;
import com.hms.model.Appointment;
import com.hms.model.Doctor;
import com.hms.model.Gender;
import com.hms.model.Patient;
import com.hms.service.AppointmentService;
import com.hms.service.BookingException;
import com.hms.service.PatientService;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/** Plain text menu. Kept dumb on purpose: all the rules are in the service classes. */
public class ConsoleMenu {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final DoctorDao doctors;
    private final AppointmentDao appointments;
    private final Scanner in;
    private final PrintStream out;

    public ConsoleMenu(PatientService patientService, AppointmentService appointmentService,
                       DoctorDao doctors, AppointmentDao appointments, Scanner in, PrintStream out) {
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.doctors = doctors;
        this.appointments = appointments;
        this.in = in;
        this.out = out;
    }

    public void run() {
        while (true) {
            out.println();
            out.println("==== Hospital Management ====");
            out.println("1. Register patient");
            out.println("2. Search patients");
            out.println("3. Update patient phone");
            out.println("4. List doctors");
            out.println("5. Book appointment");
            out.println("6. Doctor's schedule for a day");
            out.println("7. Patient history");
            out.println("8. Cancel appointment");
            out.println("9. Mark appointment completed");
            out.println("10. Appointments per doctor (report)");
            out.println("0. Exit");
            String choice = ask("Choose");
            try {
                switch (choice) {
                    case "1" -> registerPatient();
                    case "2" -> searchPatients();
                    case "3" -> updatePhone();
                    case "4" -> listDoctors();
                    case "5" -> book();
                    case "6" -> doctorSchedule();
                    case "7" -> patientHistory();
                    case "8" -> {
                        appointmentService.cancel(askLong("Appointment id"));
                        out.println("Cancelled.");
                    }
                    case "9" -> {
                        appointmentService.complete(askLong("Appointment id"));
                        out.println("Marked as completed.");
                    }
                    case "10" -> report();
                    case "0" -> {
                        out.println("Bye.");
                        return;
                    }
                    default -> out.println("Not a menu option.");
                }
            } catch (BookingException | IllegalArgumentException | DateTimeParseException e) {
                out.println("! " + e.getMessage());
            } catch (DataAccessException e) {
                out.println("! Database error: " + e.getMessage()
                        + (e.getCause() != null ? " (" + e.getCause().getMessage() + ")" : ""));
            }
        }
    }

    private void registerPatient() {
        String first = ask("First name");
        String last = ask("Last name");
        LocalDate dob = LocalDate.parse(ask("Date of birth (yyyy-mm-dd)"));
        Gender gender = Gender.valueOf(ask("Gender (MALE/FEMALE/OTHER)").toUpperCase());
        String phone = ask("Phone");
        String blood = ask("Blood group (blank if unknown)").toUpperCase();
        Patient saved = patientService.register(
                new Patient(null, first, last, dob, gender, phone, blood.isBlank() ? null : blood));
        out.println("Registered with id " + saved.id());
    }

    private void searchPatients() {
        List<Patient> found = patientService.search(ask("Name (blank for all)"));
        if (found.isEmpty()) {
            out.println("No patients found.");
            return;
        }
        out.printf("%-5s %-25s %-12s %-4s %-7s %-12s%n", "ID", "Name", "DOB", "Age", "Blood", "Phone");
        for (Patient p : found) {
            out.printf("%-5d %-25s %-12s %-4d %-7s %-12s%n", p.id(), p.fullName(), p.dateOfBirth(),
                    p.ageOn(LocalDate.now()), p.bloodGroup() == null ? "-" : p.bloodGroup(), p.phone());
        }
    }

    private void updatePhone() {
        long id = askLong("Patient id");
        Patient p = patientService.updatePhone(id, ask("New phone"));
        out.println("Updated " + p.fullName());
    }

    private void listDoctors() {
        out.printf("%-5s %-22s %-18s %s%n", "ID", "Name", "Specialization", "Shift");
        for (Doctor d : doctors.findAll()) {
            out.printf("%-5d %-22s %-18s %s-%s%n", d.id(), d.fullName(), d.specialization(),
                    d.shiftStart(), d.shiftEnd());
        }
    }

    private void book() {
        long patientId = askLong("Patient id");
        long doctorId = askLong("Doctor id");
        LocalDate date = LocalDate.parse(ask("Date (yyyy-mm-dd)"));
        List<LocalTime> free = appointmentService.freeSlots(doctorId, date);
        if (free.isEmpty()) {
            out.println("No free slots that day.");
            return;
        }
        out.println("Free slots: " + free);
        LocalTime time = LocalTime.parse(ask("Time (HH:mm)"));
        String reason = ask("Reason for visit");
        Appointment a = appointmentService.book(patientId, doctorId, date.atTime(time), reason);
        out.println("Booked. Appointment id " + a.id());
    }

    private void doctorSchedule() {
        long doctorId = askLong("Doctor id");
        LocalDate date = LocalDate.parse(ask("Date (yyyy-mm-dd)"));
        List<Appointment> list = appointments.findByDoctorAndDate(doctorId, date);
        if (list.isEmpty()) {
            out.println("Nothing booked.");
        }
        for (Appointment a : list) {
            String name = patientService.find(a.patientId()).map(Patient::fullName).orElse("?");
            out.printf("#%d  %s  %-20s %-10s %s%n", a.id(), a.startTime().toLocalTime(), name, a.status(),
                    a.reason() == null ? "" : a.reason());
        }
    }

    private void patientHistory() {
        long patientId = askLong("Patient id");
        for (Appointment a : appointments.findByPatient(patientId)) {
            String doc = doctors.findById(a.doctorId()).map(Doctor::fullName).orElse("?");
            out.printf("#%d  %s  %-20s %s%n", a.id(), a.startTime().format(DATE_TIME), doc, a.status());
        }
    }

    private void report() {
        LocalDate from = LocalDate.parse(ask("From (yyyy-mm-dd)"));
        LocalDate to = LocalDate.parse(ask("To (yyyy-mm-dd)"));
        Map<String, Integer> counts = appointments.countPerDoctor(from, to);
        counts.forEach((name, n) -> out.printf("%-22s %d%n", name, n));
    }

    private String ask(String label) {
        out.print(label + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "0";
    }

    private long askLong(String label) {
        String s = ask(label);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number, got '" + s + "'");
        }
    }
}
