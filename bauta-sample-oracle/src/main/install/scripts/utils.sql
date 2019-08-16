create or replace PROCEDURE DROP_INDEX_IF_EXISTS(p_indexname VARCHAR2) AS
 l_sql VARCHAR2(100);
BEGIN
   l_sql := 'DROP INDEX '||p_indexname;
   EXECUTE IMMEDIATE l_sql;
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -1418 THEN
         RAISE;
      END IF;
END;
/

create or replace PROCEDURE DROP_TABLE_IF_EXISTS(p_tablename VARCHAR2) AS
 l_sql VARCHAR2(100);
BEGIN
   l_sql := 'DROP TABLE '||p_tablename||' PURGE';
   EXECUTE IMMEDIATE l_sql;
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN
         RAISE;
      END IF;
END;
/

/* Drops table if exists and any associated constraints. */
create or replace PROCEDURE DROP_TABLE_CASCADE_IF_EXISTS(p_tablename VARCHAR2) AS
 l_sql VARCHAR2(100);
BEGIN
   l_sql := 'DROP TABLE '||p_tablename||' CASCADE CONSTRAINTS PURGE';
   EXECUTE IMMEDIATE l_sql;
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN
         RAISE;
      END IF;
END;
/

select 'hello' from dual;
