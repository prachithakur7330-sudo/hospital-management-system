-- Sample data so the app has something to show on first run.

INSERT INTO doctors (full_name, specialization, phone, shift_start, shift_end) VALUES
    ('Dr. Anjali Mehra',  'Cardiology',       '9810001001', '09:00', '13:00'),
    ('Dr. Rohan Iyer',    'Orthopaedics',     '9810001002', '10:00', '16:00'),
    ('Dr. Sana Qureshi',  'Paediatrics',      '9810001003', '14:00', '18:00'),
    ('Dr. Vikram Nair',   'General Medicine', '9810001004', '08:30', '12:30');

INSERT INTO patients (first_name, last_name, date_of_birth, gender, phone, blood_group) VALUES
    ('Aarav',  'Sharma', '1991-04-12', 'MALE',   '9900011122', 'B+'),
    ('Meera',  'Pillai', '1985-11-03', 'FEMALE', '9900011133', 'O+'),
    ('Kabir',  'Singh',  '2016-07-21', 'MALE',   '9900011144', 'A-'),
    ('Fatima', 'Khan',   '1962-02-28', 'FEMALE', '9900011155', 'AB+'),
    ('Ishaan', 'Verma',  '2001-09-09', 'MALE',   '9900011166', NULL);
