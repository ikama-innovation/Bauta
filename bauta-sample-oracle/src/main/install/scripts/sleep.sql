select 'sleeping' from dual;
DECLARE
    t INTEGER;
BEGIN
    t := &1;
    DBMS_LOCK.sleep(t);
END;
/

select 'done!' from dual;