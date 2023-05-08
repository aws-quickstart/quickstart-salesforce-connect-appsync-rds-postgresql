CREATE USER resolver_testing_special__name QUOTA unlimited on USERS;

CREATE TABLE resolver_testing_special__name.multi___underscores (
    table____id integer PRIMARY KEY
);
INSERT INTO resolver_testing_special__name.multi___underscores (table____id) VALUES (5);