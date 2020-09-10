-- Creates a stored procedure which we will later execute as a scheduled job
exec drop_table_if_exists('person2');
create table person2 (
    person_id int,
    last_name varchar2(255),
    first_name varchar2(255),
    address varchar2(255),
    city varchar2(255)
);

create or replace PROCEDURE demo_create_person2(p_size NUMBER) AS
BEGIN
For IDS in 1..p_size
Loop
INSERT INTO person2 (person_id, last_name, first_name, address, city) VALUES (IDS, dbms_random.string('L', 15), dbms_random.string('L', 15),dbms_random.string('L', 15),dbms_random.string('L', 15));
Commit;
End loop;
END demo_create_person2;
/

CREATE OR REPLACE PROCEDURE demo_convert_person2 AS
  l_stmt VARCHAR2(4000);
BEGIN
  l_stmt := 'begin drop_table_if_exists(''person2_converted''); end;';
  EXECUTE IMMEDIATE l_stmt;

  l_stmt := 'create table person2_converted as select  person_id, upper(last_name) as last_name, upper(first_name) as first_name, address, city from person2';
  EXECUTE IMMEDIATE l_stmt;
  DBMS_LOCK.sleep(120);
END demo_convert_person2;
/