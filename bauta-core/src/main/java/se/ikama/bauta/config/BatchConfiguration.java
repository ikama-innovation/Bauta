package se.ikama.bauta.config;

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.BatchConfigurer;
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@PropertySource("bauta_default.properties")
@Configuration
@ImportResource({
        "classpath:spring_beans/job_common.xml",
        "file://${bauta.jobBeansDir}/*.xml"
})
@EnableBatchProcessing(modular = true)
@EnableVaadin("se.ikama.bauta.ui")
public class BatchConfiguration {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Value("${bauta.homeDir}")
    String homeDir;

    @Value("${bauta.stagingDB.url}")
    String stagingDbUrl;

    @Value("${bauta.stagingDB.username}")
    String stagingDbUsername;

    @Value("${bauta.stagingDB.password}")
    String stagingDbPassword;


    @Bean()
    @Primary
    DataSource batchDataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        dataSource.setUrl("jdbc:hsqldb:file:" + homeDir + "/db/data");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        log.info("Creating batch datasource {}", dataSource);
        return dataSource;
    }


    @Bean
    @Primary
    PlatformTransactionManager batchTransactionManager() {

        return new DataSourceTransactionManager(batchDataSource());
    }

    @Bean
    public BatchConfigurer batchConfigurer() {
        log.info("Creating Bauta batch configurer.");
        BatchConfigurer bf = new DefaultBatchConfigurer(batchDataSource()) {

            @Autowired()
            @Qualifier("batchDataSource")
            DataSource dataSource;

            //@Autowired()
            //@Qualifier("batchTransactionManager")
            //PlatformTransactionManager txManager;

            @Override
            public PlatformTransactionManager getTransactionManager() {
                return batchTransactionManager();
            }

            @Override
            protected JobRepository createJobRepository() throws Exception {
                JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
                factory.setDataSource(dataSource);
                factory.setTransactionManager(batchTransactionManager());
                factory.setIsolationLevelForCreate("ISOLATION_REPEATABLE_READ");
                factory.setTablePrefix("BATCH_");
                factory.setMaxVarCharLength(1000);

                return factory.getObject();
            }

            @Override
            public JobExplorer createJobExplorer() throws Exception {
                JobExplorerFactoryBean jobExplorer = new JobExplorerFactoryBean();
                jobExplorer.setDataSource(dataSource);
                jobExplorer.setTablePrefix("BATCH_");
                jobExplorer.afterPropertiesSet();
                return jobExplorer.getObject();

            }

            @Override
            public JobLauncher createJobLauncher() throws Exception {
                SimpleJobLauncher jobLauncher = new SimpleJobLauncher();
                jobLauncher.setJobRepository(getJobRepository());
                jobLauncher.setTaskExecutor(taskExecutor());
                jobLauncher.afterPropertiesSet();
                return jobLauncher;
            }
        };
        return bf;

    }


    @Bean
    JobOperator jobOperator(JobExplorer jobExplorer,
                            JobRepository jobRepository,
                            JobRegistry jobRegistry,
                            JobLauncher jobLauncher) {
        log.info("Creating job operator {}", jobRepository);
        SimpleJobOperator jobOperator = new SimpleJobOperator();

        jobOperator.setJobExplorer(jobExplorer);
        jobOperator.setJobRepository(jobRepository);
        jobOperator.setJobRegistry(jobRegistry);
        jobOperator.setJobLauncher(jobLauncher);

        return jobOperator;
    }

    //@Bean
    //public JobRegistry jobRegistry() throws Exception {
    //   return new MapJobRegistry();
    //}
    @Bean
    public JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor(JobRegistry jobRegistry) {
        JobRegistryBeanPostProcessor postProcessor = new JobRegistryBeanPostProcessor();
        postProcessor.setJobRegistry(jobRegistry);
        return postProcessor;
    }

    // Staging database
    @Bean()
    //@ConditionalOnProperty(prefix = "bauta", name = "stagingDB.url")
    DataSource stagingDataSource() {
        log.info("Setting up staging DB. Url is {}", stagingDbUrl);
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("oracle.jdbc.driver.OracleDriver");
        dataSource.setUrl(stagingDbUrl);
        dataSource.setUsername(stagingDbUsername);
        dataSource.setPassword(stagingDbPassword);

        log.info("Creating staging datasource {}", dataSource);
        return dataSource;
    }

    @Bean
    PlatformTransactionManager stagingTransactionManager() {
        return new DataSourceTransactionManager(stagingDataSource());
    }

    @Bean
    TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(-1);
        return executor;
    }


}
