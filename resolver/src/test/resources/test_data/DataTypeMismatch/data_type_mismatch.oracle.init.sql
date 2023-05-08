CREATE USER resolver_testing_data_type_mismatch quota unlimited on USERS;

CREATE TABLE resolver_testing_data_type_mismatch.date_time (
    int_id int PRIMARY KEY,
    date_time_no_tz timestamp,
    date_time_tz timestamp with time zone
);
INSERT INTO resolver_testing_data_type_mismatch.date_time (int_id, date_time_no_tz, date_time_tz) 
VALUES (1, to_timestamp_tz('2020-05-10T01:02:03', 'YYYY-MM-DD"t"HH24:MI:SS.FF3'), to_timestamp_tz('2023-08-01T05:06:07Z', 'YYYY-MM-DD"t"HH24:MI:SS.FF3TZR'));