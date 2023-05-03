CREATE USER resolver_testing_types quota unlimited on USERS;

CREATE TABLE resolver_testing_types.types_test (
    table_id integer primary key,
    int_field integer,
    float_field number(10, 5),
    string_field varchar2(500),
    boolean_field number(1),
    date_field date,
    time_field timestamp,
    date_time_field timestamp with time zone,
    epoch_field number(19),
    email_field varchar2(500),
    phone_field varchar2(500),
    url_field varchar2(500),
    json_field varchar2(500),
    ip_address_field varchar2(500)
);

INSERT INTO resolver_testing_types.types_test VALUES 
    (1, 2, 1.5, 'my_string', 1, to_date('2022-05-05', 'YYYY-MM-DD'), to_timestamp_tz('10:00:15Z', 'HH24:MI:SS.FF3TZR'), to_timestamp_tz('2022-05-05T10:00:20.123Z', 'YYYY-MM-DD"t"HH24:MI:SS.FF3TZR'),
     1666761185, 'johndoe@notrealemail.com', '8085554444', 'https://google.com', '{"test":"json"}', '192.168.0.1');

INSERT INTO resolver_testing_types.types_test VALUES
    (2, null, null, null, null, null, null, null,
     null, null, null, null, null, null);