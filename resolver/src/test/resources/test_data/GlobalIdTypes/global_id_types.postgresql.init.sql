CREATE SCHEMA resolver_testing_global_id_types;

CREATE TABLE resolver_testing_global_id_types.int_float (
    int_id int UNIQUE,
    float_id numeric(10,5) UNIQUE,
    PRIMARY KEY(int_id, float_id)
);
INSERT INTO resolver_testing_global_id_types.int_float (int_id, float_id) VALUES (1, 2.5);
INSERT INTO resolver_testing_global_id_types.int_float (int_id, float_id) VALUES (3, 4.5);


CREATE TABLE resolver_testing_global_id_types.date_time (
    date_id date UNIQUE,
    time_id time with time zone UNIQUE,
    date_time_id timestamp with time zone UNIQUE,
    PRIMARY KEY(date_id, time_id, date_time_id)
);
INSERT INTO resolver_testing_global_id_types.date_time (date_id, time_id, date_time_id) 
VALUES ('2020-05-10', '11:12:13Z', '2021-02-03T01:02:03Z');
INSERT INTO resolver_testing_global_id_types.date_time (date_id, time_id, date_time_id)
VALUES ('2021-06-11', '12:13:14', '2022-03-04T01:02:03Z');

CREATE TABLE resolver_testing_global_id_types.lookup_table (
                                                               parent_int_id int references resolver_testing_global_id_types.int_float(int_id),
                                                               parent_float_id numeric(10,5) references resolver_testing_global_id_types.int_float(float_id),
                                                               parent_date_id date references resolver_testing_global_id_types.date_time(date_id),
                                                               parent_time_id time references resolver_testing_global_id_types.date_time(time_id),
                                                               parent_date_time_id timestamp with time zone references resolver_testing_global_id_types.date_time(date_time_id),
                                                               PRIMARY KEY(parent_int_id, parent_float_id, parent_date_id, parent_time_id, parent_date_time_id)
);
INSERT INTO resolver_testing_global_id_types.lookup_table (parent_int_id, parent_float_id, parent_date_id, parent_time_id, parent_date_time_id)
VALUES (3, 4.5, '2021-06-11', '12:13:14', '2022-03-04T01:02:03Z');