package se.ikama.bauta.batch.tasklet;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a validation that will be run by the {@link SqlValidationTasklet} in the form of JSON objects.
 */
@Builder
@AllArgsConstructor
@Slf4j
@Getter
@Setter
public class SqlValidation implements ResultSetExtractor<Integer> {
    public enum ValidationResultStatus {OK, ValidationFailed, SqlFailed}

    /**
     * The SQL query. Must not return a large result set. Should typically return one row with one column,
     * e.g. <pre>select count(*) from mytable</pre> or <pre>select ((select count(*) from person1_converted)-(select count(*) from person1)) from dual</pre>
     */
    @NonNull
    private String sqlQuery;

    /**
     * Text describing what is being validated, e.g. 'Count should be greater than 100'
     */
    private String title;

    /**
     * All validation queries will be timed. You can validate that the execution time stays below a given number by setting this parameter.
     * Time is in ms.
     */
    private Long maxExecutionTime;
    /**
     * Validates that the result is greater than this number
     */
    private Integer result_gt;

    /**
     * Validates that the result is less than this number
     */
    private Integer result_lt;

    /**
     * Validates that the result is greater or equal to this number
     */
    private Integer result_gte;

    /**
     * Validates that the result is less or equal to this number
     */
    private Integer result_lte;

    /**
     * Validates that the result is equal to this number or string
     */
    private String result_equals;

    private List<String> columnNames;
    private List<Object> columnTypes;

    @Setter(AccessLevel.PACKAGE)
    @Getter(AccessLevel.PACKAGE)
    private List<List<Object>> rows;

    @Setter(AccessLevel.PACKAGE)
    private Long executionTime;

    @Setter(AccessLevel.PACKAGE)
    private String sqlError;

    private String validationError;

    private ValidationResultStatus status;

    public SqlValidation() {

    }

    @Override
    public Integer extractData(ResultSet rs) throws SQLException, DataAccessException {
        log.debug("Extracting data");
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        this.columnNames = new ArrayList<>();
        this.columnTypes = new ArrayList<>();
        log.debug("Column names are {}", columnNames);
        log.debug("Column types are {}", columnTypes);
        for (int i = 0; i < columnCount; i++) {
            columnNames.add(metaData.getColumnName(i + 1));
            columnTypes.add(metaData.getColumnTypeName(i + 1));
        }
        this.rows = new ArrayList<List<Object>>();
        while (rs.next()) {
            log.debug("Processing row");
            ArrayList<Object> row = new ArrayList<>();
            for (int i = 0; i < columnCount; i++) {
                row.add(rs.getObject(i + 1));
            }
            this.rows.add(row);
        }
        return columnCount;
    }
    public void validate()  {
        if (sqlError != null) {
            this.status = ValidationResultStatus.SqlFailed;
            // Nothing to validate
            return;
        }
        if (rows.size() > 1) {
            this.status = ValidationResultStatus.ValidationFailed;
            this.validationError = "Only single-row result can be validated";
        }
        if (rows.size() == 1) {
            Object resultValue = rows.get(0).get(0);
            log.debug("Result value is {}. Type is {}", resultValue, resultValue.getClass());
            if (resultValue instanceof BigDecimal) {
                BigDecimal resultNumber = (BigDecimal) resultValue;
                if (this.result_gt != null) {
                    if (resultNumber.compareTo(new BigDecimal(this.result_gt)) <= 0) {
                        this.status = ValidationResultStatus.ValidationFailed;
                        this.validationError = "Result is '" + resultValue+"'. Expected > (result_gt) " + this.result_gt;
                        return;
                    }
                }
                if (this.result_gte != null) {
                    if (resultNumber.compareTo(new BigDecimal(this.result_gte)) < 0) {
                        this.status = ValidationResultStatus.ValidationFailed;
                        this.validationError = "Result is " + resultValue+". Expected => (result_gte) " + this.result_gte;
                        return;
                    }
                }
                if (this.result_lt != null) {
                    if (resultNumber.compareTo(new BigDecimal(this.result_lt)) >= 0) {
                        this.status = ValidationResultStatus.ValidationFailed;
                        this.validationError = "Result is " + resultValue+". Expected < (result_lt) " + this.result_lt;
                        return;
                    }
                }
                if (this.result_lte != null) {
                    if (resultNumber.compareTo(new BigDecimal(this.result_lte)) > 0) {
                        this.status = ValidationResultStatus.ValidationFailed;
                        this.validationError = "Result is " + resultValue+". Expected > (result_lte) " + this.result_lte;
                        return;
                    }
                }
                if (this.result_equals != null) {
                    log.debug("Checking for equal (number)");
                    if (!this.result_equals.equals(resultValue.toString())) {
                        this.status = ValidationResultStatus.ValidationFailed;
                        this.validationError = "Result is " + resultValue+". Expected (result_equals): " + this.result_equals;
                        return;
                    }
                }
            } else if (resultValue instanceof String) {
                if (StringUtils.isNotEmpty(this.result_equals)) {
                    if (!resultValue.equals(this.result_equals)) {
                        this.status = ValidationResultStatus.ValidationFailed;
                        this.validationError = "Result is '" + resultValue+"'. Expected: '" + this.result_equals+"'";
                        return;
                    }
                }
            }
            if (this.maxExecutionTime != null && this.executionTime > this.maxExecutionTime) {
                this.status = ValidationResultStatus.ValidationFailed;
                this.validationError = "Execution time " + executionTime +" exceeded maxExecutiontime " + this.maxExecutionTime;
                return;
            }
        }
        this.status = ValidationResultStatus.OK;

    }

}
