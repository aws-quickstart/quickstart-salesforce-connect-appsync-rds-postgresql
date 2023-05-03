CREATE USER resolver_testing_global_id_types quota unlimited on USERS;

CREATE TABLE resolver_testing_global_id_types.int_float (
                                           int_id integer UNIQUE,
                                           float_id number(10,5) UNIQUE,
                                           PRIMARY KEY(int_id, float_id)
);
INSERT INTO resolver_testing_global_id_types.int_float (int_id, float_id) VALUES (1, 2.5);
INSERT INTO resolver_testing_global_id_types.int_float (int_id, float_id) VALUES (3, 4.5);

CREATE TABLE resolver_testing_global_id_types.lookup_table (
       parent_int_id integer references resolver_testing_global_id_types.int_float(int_id),
       parent_float_id number(10,5) references resolver_testing_global_id_types.int_float(float_id),
       PRIMARY KEY(parent_int_id, parent_float_id)
);
INSERT INTO resolver_testing_global_id_types.lookup_table (parent_int_id, parent_float_id)
VALUES (3, 4.5);