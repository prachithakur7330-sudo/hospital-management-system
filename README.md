# Hospital Management System

A console-based hospital management app in **Java 17** backed by **MySQL**. It handles
patients, doctors and appointments, with the booking rules (no double-booking, slots
inside a doctor's shift, no past bookings) enforced in both the application layer and the
database.

This is a learning project built to practise JDBC, SQL schema design, a clean DAO/service
layering, and testing a database-backed app.

## What it does

- **Patients** — register, search by name, update phone, delete. Input is validated
  (10-digit mobile number, no future date of birth, known blood group).
- **Doctors** — each has a specialisation and a daily shift; seeded with four to start.
- **Appointments** — booked in 30-minute slots. The service checks that the slot is free,
  falls inside the doctor's shift, isn't in the past, and that neither the doctor nor the
  patient is already booked at that time. Appointments can be completed or cancelled, and a
  cancelled slot frees up for re-booking.
- **Reports** — free slots for a doctor on a day, a patient's history, and a count of
  appointments per doctor over a date range.

## How it's put together

```
com.hms
  model/       Patient, Doctor, Appointment (records) + enums
  db/          Database — reads config, hands out JDBC connections, runs .sql scripts
  dao/         interfaces + JDBC implementations (prepared statements throughout)
  service/     PatientService, AppointmentService — all the rules live here
  ui/          ConsoleMenu — a thin text menu
  Main         wires it together
```

The split is deliberate: the DAOs only talk to the database, the services hold the
business rules, and the UI is kept dumb. That's also what makes the rules testable without
standing up a database (see below).

### Two layers of protection against double-booking

The `AppointmentService` checks for a clash before inserting. But a check-then-insert has a
race: two requests could both pass the check and then both insert. So the schema also has a
unique key on `(doctor_id, active_slot)`, where `active_slot` is a generated column that
only holds a value while the appointment is `SCHEDULED`:

```sql
active_slot DATETIME GENERATED ALWAYS AS (IF(status='SCHEDULED', start_time, NULL)) STORED,
UNIQUE KEY uq_doctor_active_slot (doctor_id, active_slot)
```

NULLs don't collide in a MySQL unique index, so a cancelled appointment leaves the slot
free but two live bookings for the same doctor and time are impossible. If the insert loses
the race it throws, and the service turns that into a friendly "already booked" message.
There's an integration test that inserts straight through the DAO (bypassing the service)
to prove the database rejects the duplicate on its own.

## Running it

You need Java 17+ and a MySQL 8 server.

```bash
# 1. create the database
mysql -u root -p -e "CREATE DATABASE hms"

# 2. point the app at your database (either env vars or db.properties)
export HMS_DB_URL="jdbc:mysql://localhost:3306/hms"
export HMS_DB_USER=root
export HMS_DB_PASSWORD=yourpassword

# 3. build
mvn package

# 4. create the tables and load sample data
java -jar target/hms.jar --init-db

# 5. run the menu
java -jar target/hms.jar
```

Without Maven you can compile with `javac` and put the MySQL Connector/J jar on the
classpath; `sql/schema.sql` and `sql/seed.sql` can also be run directly with the `mysql`
client.

### Configuration

Settings are read from environment variables (`HMS_DB_URL`, `HMS_DB_USER`,
`HMS_DB_PASSWORD`) first, then from a `db.properties` file in the working directory, then a
`localhost:3306/hms` default. A sample is in `db.properties.example`.

## Tests

```bash
mvn test
```

There are two kinds:

- **Service tests** run against in-memory fake DAOs, so all the booking rules are tested
  with no database — fast and deterministic (time is pinned with a fixed `Clock`).
- **Integration tests** run against a real MySQL and are **skipped unless** you point them
  at a scratch database:

  ```bash
  HMS_TEST_DB_URL="jdbc:mysql://localhost:3306/hms_test?allowPublicKeyRetrieval=true&useSSL=false" \
  HMS_TEST_DB_USER=hms HMS_TEST_DB_PASSWORD=hms_pass mvn test
  ```

  They recreate the schema each time, so point them at a throwaway database. All 20 tests
  (15 service + 5 integration) pass against MySQL 8.0.

## Sample session

```
==== Hospital Management ====
...
Choose: 5
Patient id: 2
Doctor id: 1
Date (yyyy-mm-dd): 2026-11-09
Free slots: [09:00, 09:30, 10:00, 10:30, 11:00, 11:30, 12:00, 12:30]
Time (HH:mm): 09:30
Reason for visit: annual checkup
Booked. Appointment id 1

Choose: 5
Patient id: 3
Doctor id: 1
Date (yyyy-mm-dd): 2026-11-09
Time (HH:mm): 09:30
! Dr. Anjali Mehra is already booked at 09:30
```

## Possible next steps

- A small REST layer (Spring Boot or Javalin) so it isn't console-only.
- Login and roles (receptionist vs doctor).
- A `patient_notes` / visit-records table linked to completed appointments.
