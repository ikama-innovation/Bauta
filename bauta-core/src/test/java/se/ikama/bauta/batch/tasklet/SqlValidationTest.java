package se.ikama.bauta.batch.tasklet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import se.ikama.bauta.batch.tasklet.SqlValidation;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SqlValidationTest {

    @Test
    public void testValidation() {
        SqlValidation v = SqlValidation.builder().sqlQuery("select 'OK' from dual").result_equals("OK").build();
        mockSqlResult(v, "OK", 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus());

        v = SqlValidation.builder().sqlQuery("select 'OK' from dual").result_equals("OK").build();
        mockSqlResult(v, "NOTOK", 20);
        v.validate();
        Assert.assertEquals("Result is 'NOTOK'. Expected: 'OK'", v.getValidationError());
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus());

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_equals("300").build();
        mockSqlResult(v, new BigDecimal(300), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus());

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_equals("300").build();
        mockSqlResult(v, new BigDecimal(301), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;

        // Greater than
        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_gt(300).build();
        mockSqlResult(v, new BigDecimal(301), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus()) ;

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_gt(300).build();
        mockSqlResult(v, new BigDecimal(300), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;
        System.out.println(v.getValidationError());

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_gt(300).build();
        mockSqlResult(v, new BigDecimal(299), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;
        System.out.println(v.getValidationError());

        // Greater than or equals
        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_gte(300).build();
        mockSqlResult(v, new BigDecimal(301), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus()) ;

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_gte(300).build();
        mockSqlResult(v, new BigDecimal(300), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus()) ;
        System.out.println(v.getValidationError());

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_gte(300).build();
        mockSqlResult(v, new BigDecimal(299), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;
        System.out.println(v.getValidationError());


        // Less than
        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lt(300).build();
        mockSqlResult(v, new BigDecimal(299), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus()) ;

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lt(300).build();
        mockSqlResult(v, new BigDecimal(300), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;
        System.out.println(v.getValidationError());

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lt(300).build();
        mockSqlResult(v, new BigDecimal(301), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;
        System.out.println(v.getValidationError());

        // Less than or equals
        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lte(300).build();
        mockSqlResult(v, new BigDecimal(299), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus()) ;

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lte(300).build();
        mockSqlResult(v, new BigDecimal(300), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus()) ;
        System.out.println(v.getValidationError());

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lte(300).build();
        mockSqlResult(v, new BigDecimal(301), 20);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;
        System.out.println(v.getValidationError());

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lte(300).result_gte(200).maxExecutionTime(1000l).build();
        mockSqlResult(v, new BigDecimal(250), 300);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.OK,v.getStatus()) ;

        v = SqlValidation.builder().sqlQuery("select count(*) from demo").result_lte(300).result_gte(200).maxExecutionTime(1000l).build();
        mockSqlResult(v, new BigDecimal(250), 1200);
        v.validate();
        Assert.assertEquals(SqlValidation.ValidationResultStatus.ValidationFailed,v.getStatus()) ;
        System.out.println(v.getValidationError());

    }
    @Test
    public void JsonTest() {
        ObjectMapper mapper = new ObjectMapper();
        List<SqlValidation> vs = new ArrayList<>();
        vs.add(SqlValidation.builder().title("Just testing").result_gte(100).result_lte(500).sqlQuery("select * from demo").build());
        vs.add(SqlValidation.builder().title("Another").result_gte(1).result_lte(2).sqlQuery("select * from demo2").build());
        try {
            StringBuilderWriter writer = new StringBuilderWriter();
            mapper.writeValue(writer, vs);
            String json = writer.toString();
            System.out.println(json);
            SqlValidation validations[] = mapper.readValue(json, SqlValidation[].class);
            for(SqlValidation v : validations) {
                System.out.println(v.getSqlQuery());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void mockSqlResult(SqlValidation v, Object result, int executionTime) {
        v.setExecutionTime((long)executionTime);
        List<List<Object>> rows = new ArrayList<>();
        List row = new ArrayList();
        row.add(result);
        rows.add(row);
        v.setRows(rows);
    }
}
