package ikama.batchc3.autoconfigure;

import ikama.batchc3.core.C3Config;
import ikama.batchc3.core.C3Manager;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static ikama.batchc3.core.C3ConfigParams.*;

@Configuration
@ConditionalOnClass(C3Manager.class)
@EnableConfigurationProperties(C3Properties.class)
@ComponentScan(basePackages = "ikama.batchc3.cli,ikama.batchc3.ui,ikama.batchc3.autoconfigure,ikama.batchc3.config")
public class C3AutoConfiguration {

    @Autowired
    private C3Properties c3Properties;

    @Bean
    @ConditionalOnMissingBean
    public C3Config c3Config() {

        String homeDir = c3Properties.getHomeDir() == null ? System.getProperty("batchc3.homeDir") : c3Properties.getHomeDir();
        String jobBeansDir = c3Properties.getJobBeansDir() == null ? System.getProperty("batchc3.jobBeansDir") : c3Properties.getJobBeansDir();
        String reportDir = c3Properties.getJobBeansDir() == null ? System.getProperty("batchc3.reportDir") : c3Properties.getReportDir();

        C3Config c3Config = new C3Config();
        c3Config.put(HOME_DIR, homeDir);
        c3Config.put(JOB_BEANS_DIR, jobBeansDir);
        c3Config.put(REPORT_DIR, reportDir);
        return c3Config;
    }

    @Bean
    @ConditionalOnMissingBean
    public C3Manager c3Manager(C3Config c3Config, JobOperator jobOperator, JobRepository jobRepository, JobExplorer jobExplorer) {

        return new C3Manager(c3Config, jobOperator, jobRepository, jobExplorer);
    }

}
