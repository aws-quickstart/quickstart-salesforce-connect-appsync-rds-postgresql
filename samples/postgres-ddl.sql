CREATE SCHEMA graphqlsample;

CREATE TABLE graphqlsample.my_order (
    order_id character varying primary key,
    customer_id character varying,
    order_date timestamp with time zone,
    status character varying,
    source_ip_address character varying,
    total_cost numeric(10, 5)
);

CREATE TABLE graphqlsample.my_product (
    product_id character varying primary key,
    name character varying,
    price numeric(10, 5),
    discontinued boolean
);

CREATE TABLE graphqlsample.my_order_item (
    parent_order_id character varying REFERENCES graphqlsample.my_order,
    parent_product_id character varying REFERENCES graphqlsample.my_product,
    quantity int,
    PRIMARY KEY(parent_order_id, parent_product_id)
);

INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-100', 'CUST-3675', '2022-09-13T10:10:01Z', '169.114.197.175', 'Placed', 500.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-101', 'CUST-6258', '2022-10-18T10:11:01Z', '127.179.97.210', 'Placed', 520.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-102', 'CUST-5973', '2022-10-10T10:12:01Z', '172.253.73.138', 'Shipped', 540.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-103', 'CUST-2777', '2021-12-10T10:13:01Z', '117.171.110.250', 'Canceled', 560.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-104', 'CUST-3675', '2022-12-16T10:14:01Z', '229.228.224.139', 'Shipped', 580.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-105', 'CUST-6258', '2022-12-12T10:15:01Z', '223.122.229.34', 'Shipped', 600.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-106', 'CUST-5973', '2022-11-14T10:16:01Z', '230.200.255.112', 'Shipped', 620.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-107', 'CUST-2777', '2022-10-19T10:17:01Z', '163.157.161.174', 'Shipped', 640.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-108', 'CUST-3675', '2022-06-17T10:18:01Z', '233.29.36.40', 'Shipped', 660.55);
INSERT INTO graphqlsample.my_order (order_id, customer_id, order_date, source_ip_address, status, total_cost) VALUES ('ORD-109', 'CUST-6258', '2022-09-15T10:19:01Z', '35.136.43.142', 'Shipped', 680.55);

INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-300', 'Apple', 1.79, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-301', 'Banana', 4.59, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-302', 'Kiwi', 3.29, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-303', 'Lemon', 0.49, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-304', 'Orange', 2.29, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-305', 'Lime', 0.25, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-306', 'Persimmon', 5.99, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-307', 'Grapefruit', 6.49, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-308', 'Blueberry', null, false);
INSERT INTO graphqlsample.my_product (product_id, name, price, discontinued) VALUES ('PRD-309', 'Strawberry', 3.49, false);

INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-100', 'PRD-300', 10);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-101', 'PRD-301', 20);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-102', 'PRD-302', 30);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-103', 'PRD-303', 40);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-104', 'PRD-304', 50);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-105', 'PRD-305', 60);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-106', 'PRD-306', 70);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-107', 'PRD-307', 80);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-108', 'PRD-308', 90);
INSERT INTO graphqlsample.my_order_item (parent_order_id, parent_product_id, quantity) VALUES ('ORD-109', 'PRD-309', 100);