-- Hospital Management System schema (MySQL 8)
-- Creates the tables in whatever database you're connected to:
--   mysql -u root -p -e "CREATE DATABASE hms"
--   mysql -u root -p hms < sql/schema.sql

DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS doctors;
DROP TABLE IF EXISTS patients;

CREATE TABLE patients (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name    VARCHAR(60)  NOT NULL,
    last_name     VARCHAR(60)  NOT NULL,
    date_of_birth DATE         NOT NULL,
    gender        ENUM('MALE', 'FEMALE', 'OTHER') NOT NULL,
    phone         VARCHAR(15)  NOT NULL,
    blood_group   VARCHAR(3),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_patient_phone (phone),
    KEY idx_patient_name (last_name, first_name)
);

CREATE TABLE doctors (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(120) NOT NULL,
    specialization VARCHAR(80)  NOT NULL,
    phone          VARCHAR(15)  NOT NULL,
    shift_start    TIME         NOT NULL,
    shift_end      TIME         NOT NULL,
    CONSTRAINT chk_shift CHECK (shift_end > shift_start),
    KEY idx_doctor_spec (specialization)
);

CREATE TABLE appointments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id  BIGINT       NOT NULL,
    doctor_id   BIGINT       NOT NULL,
    start_time  DATETIME     NOT NULL,
    status      ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    reason      VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- MySQL has no partial unique index, so this generated column is only
    -- filled for SCHEDULED rows. NULLs don't collide, which means a cancelled
    -- slot can be booked again but two live bookings for one doctor can't.
    active_slot DATETIME GENERATED ALWAYS AS (IF(status = 'SCHEDULED', start_time, NULL)) STORED,
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE CASCADE,
    CONSTRAINT fk_appt_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors (id)  ON DELETE CASCADE,
    UNIQUE KEY uq_doctor_active_slot (doctor_id, active_slot),
    KEY idx_appt_patient (patient_id, start_time)
);
