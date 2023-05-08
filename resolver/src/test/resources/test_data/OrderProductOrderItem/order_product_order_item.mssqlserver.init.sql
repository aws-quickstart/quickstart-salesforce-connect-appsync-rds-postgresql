EXEC ('CREATE SCHEMA resolver_testing_order_product_order_item');

CREATE TABLE resolver_testing_order_product_order_item.my_order (
                                                   order_id varchar(400) primary key,
                                                   order_date datetimeoffset,
                                                   status varchar(MAX),
                                                   total_cost numeric(10, 5)
);

CREATE TABLE resolver_testing_order_product_order_item.my_product (
                                                     product_id varchar(400) primary key,
                                                     name varchar(MAX),
                                                     price numeric(10, 5),
                                                     discontinued bit
);

CREATE TABLE resolver_testing_order_product_order_item.my_order_item (
                                                        parent_order_id varchar(400) references resolver_testing_order_product_order_item.my_order,
                                                        parent_product_id varchar(400) references resolver_testing_order_product_order_item.my_product,
                                                        quantity int,
                                                        PRIMARY KEY(parent_order_id, parent_product_id)
);

INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-100', '2020-05-10T10:10:01Z', 'shipped', 500.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-101', '2020-05-10T10:11:01Z', 'shipped', 520.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-102', '2020-05-10T10:12:01Z', 'shipped', 540.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-103', '2020-05-10T10:13:01Z', 'shipped', 560.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-104', '2020-05-10T10:14:01Z', 'shipped', 580.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-105', '2020-05-10T10:15:01Z', 'shipped', 600.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-106', '2020-05-10T10:16:01Z', 'shipped', 620.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-107', '2020-05-10T10:17:01Z', 'shipped', 640.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-108', '2020-05-10T10:18:01Z', 'shipped', 660.55);
INSERT INTO resolver_testing_order_product_order_item.my_order (order_id, order_date, status, total_cost) VALUES ('ORD-109', '2020-05-10T10:19:01Z', 'shipped', 680.55);

INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-299', 'cantaloupe', 50, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-300', 'apple', 10, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-301', 'banana', 20, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-302', 'watermelon', 30, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-303', 'lemon', 40, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-304', 'orange', 50, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-305', 'lime', 60, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-306', 'persimmon', 70, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-307', 'grapefruit', 80, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-308', 'blueberry', null, 0);
INSERT INTO resolver_testing_order_product_order_item.my_product (product_id, name, price, discontinued) VALUES ('PRD-309', 'strawberry', 100, 0);

INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-100', 'PRD-300', 10);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-101', 'PRD-301', 20);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-102', 'PRD-302', 30);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-103', 'PRD-303', 40);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-104', 'PRD-304', 50);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-105', 'PRD-305', 60);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-106', 'PRD-306', 70);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-107', 'PRD-307', 80);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-108', 'PRD-308', 90);
INSERT INTO resolver_testing_order_product_order_item.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-109', 'PRD-309', 100);