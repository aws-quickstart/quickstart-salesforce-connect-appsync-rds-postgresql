EXEC ('CREATE SCHEMA resolver_testing_types');

CREATE TABLE resolver_testing_types.types_test (
                                  table_id int primary key,
                                  int_field int,
                                  float_field numeric(10, 5),
                                  string_field varchar(MAX),
                                  boolean_field bit,
                                  date_field date,
                                  time_field time,
                                  date_time_field datetimeoffset,
                                  epoch_field bigint,
                                  email_field varchar(MAX),
                                  phone_field varchar(MAX),
                                  url_field varchar(MAX),
                                  json_field varchar(MAX),
                                  ip_address_field varchar(MAX)
);

INSERT INTO resolver_testing_types.types_test VALUES
    (1, 2, 1.5, 'my_string', 1, '2022-05-05', '10:00:15', '2022-05-05T10:00:20.123Z',
     1666761185, 'johndoe@notrealemail.com', '8085554444', 'https://google.com', '{"test":"json"}', '192.168.0.1');

INSERT INTO resolver_testing_types.types_test VALUES
    (2, null, null, null, null, null, null, null,
     null, null, null, null, null, null);