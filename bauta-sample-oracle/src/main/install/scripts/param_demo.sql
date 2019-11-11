SET SERVEROUT ON;
set verify off;

-- &1: scriptParam1
-- &2: scriptParam2
-- &3: scriptParam3
select value from v$nls_parameters where parameter in ('NLS_LANGUAGE','NLS_TERRITORY','NLS_CHARACTERSET');

DECLARE
sp1 VARCHAR2(200) := upper('&1');
sp2 VARCHAR2(200) := upper('&2');
sp3  VARCHAR2(200) := upper('&3');
env1  VARCHAR2(200);
env2  VARCHAR2(200);


BEGIN
   DBMS_OUTPUT.PUT_LINE('Param1: '||sp1);
   DBMS_OUTPUT.PUT_LINE('Param2: '||sp2);
   DBMS_OUTPUT.PUT_LINE('Param3: '||sp3);

END;
/

