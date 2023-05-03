EXEC ('CREATE SCHEMA resolver_testing_data_type_mismatch');

CREATE TABLE resolver_testing_data_type_mismatch.date_time (
    int_id int PRIMARY KEY,
    date_time_no_tz datetime2,
    date_time_tz datetimeoffset
);
INSERT INTO resolver_testing_data_type_mismatch.date_time (int_id, date_time_no_tz, date_time_tz)
VALUES (1, '2020-05-10T01:02:03', '2023-08-01T05:06:07Z');