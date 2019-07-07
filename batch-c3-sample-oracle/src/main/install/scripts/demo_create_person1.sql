execute drop_table_if_exists('person1');
create table person1 (
    person_id int,
    last_name varchar2(255),
    first_name varchar2(255),
    address varchar2(255),
    city varchar2(255)
);
insert into person1 (person_id, last_name, first_name, address, city) values (1, 'Larsson', 'Lars', 'Lars St 1', 'Lars City');
insert into person1 (person_id, last_name, first_name, address, city) values (2, 'Svensson', 'Sven', 'Sven St 1', 'Sven City');
insert into person1 (person_id, last_name, first_name, address, city) values (3, 'Andersson', 'Anders', 'Anders St 1', 'Anders City');
insert into person1 (person_id, last_name, first_name, address, city) values (4, 'Tomasson', 'Tomas', 'Tomas St 1', 'Tomas City');
