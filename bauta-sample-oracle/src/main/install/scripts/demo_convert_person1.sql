execute drop_table_if_exists('person1_converted');
-- Perform som basic conversion by CTAS
create table person1_converted as select 90000000+person_id person_id, upper(last_name) last_name, upper(first_name) first_name, address, city from person1;
