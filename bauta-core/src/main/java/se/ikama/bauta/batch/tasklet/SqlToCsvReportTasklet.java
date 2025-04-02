package se.ikama.bauta.batch.tasklet;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Generates a CSV file based on an SQL query
 */
@Slf4j
@Getter
@Setter
public class SqlToCsvReportTasklet extends ReportTasklet implements ReportGenerator, InitializingBean {
    private int fetchSize = 100;
    private String sql;
    private String reportName;
    private String reportFilename;
    /**
     * Query timeout in seconds
     */
    protected int queryTimeout = 30;

    @Autowired
    @Qualifier("stagingDataSource")
    private DataSource dataSource;

    // Csv settings
    private char delimiter = ',';
    private String encoding = "UTF-8";
    /**
     * Generate a header
     */
    private boolean generateHeader = true;


    public SqlToCsvReportTasklet() {
        addReportGenerator(this);
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        if(reportFilename == null) throw new Exception("reportFilename must not be null");

    }

    @Override
    @Transactional(readOnly = true, transactionManager = "stagingTransactionManager")
    public ReportGenerationResult generateReport(File reportFile, StepContribution sc, ChunkContext cc) throws Exception {
        log.info("Exporting to file. {}", reportFile);

        /*
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), Charset.forName(encoding).newEncoder())) {
            try(CSVWriter csvWriter = new CSVWriter(writer, delimiter, ICSVWriter.DEFAULT_QUOTE_CHARACTER, ICSVWriter.DEFAULT_ESCAPE_CHARACTER, ICSVWriter.DEFAULT_LINE_END)) {

                try (PreparedStatement ps = dataSource.getConnection().prepareStatement(sql)) {
                    ps.setQueryTimeout(queryTimeout);
                    ps.setFetchSize(fetchSize);
                    try (ResultSet rs = ps.executeQuery()) {
                        csvWriter.writeAll(rs, true, true);
                    }
                }
            }
        }
        */
        /*
         * TODO: 
         * - Fix deprecations in CSVFormat
         * - Fix possible memory leak in CSVPrinter
         */
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), Charset.forName(encoding).newEncoder())) {
            try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setQueryTimeout(queryTimeout);
                ps.setFetchSize(fetchSize);
                CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).get();
                try (ResultSet rs = ps.executeQuery();CSVPrinter csvPrinter = new CSVPrinter(writer, format)) {
                    if (this.generateHeader) {
                        format = format.builder().setHeader(rs).get();
                    }
                    csvPrinter.printRecords(rs);
                }
            }
        }
        return ReportGenerationResult.OK;
    }

}
