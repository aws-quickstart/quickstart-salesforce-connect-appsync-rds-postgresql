CREATE SCHEMA resolver_testing_types;

CREATE TABLE resolver_testing_types.types_test (
    table_id int primary key,
    int_field int,
    float_field numeric(10, 5),
    string_field character varying,
    boolean_field boolean,
    date_field date,
    time_field time(3),
    date_time_field timestamp with time zone,
    epoch_field int8,
    email_field character varying,
    phone_field character varying,
    url_field character varying,
    json_field character varying,
    ip_address_field character varying
);

INSERT INTO resolver_testing_types.types_test VALUES 
    (1, 2, 1.5, 'my_string', true, '2022-05-05', '10:00:15', '2022-05-05T10:00:20.123Z',
     1666761185, 'johndoe@notrealemail.com', '8085554444', 'https://google.com', '{"test":"json"}', '192.168.0.1');

INSERT INTO resolver_testing_types.types_test VALUES
    (2, null, null, null, null, null, null, null,
     null, null, null, null, null, null);