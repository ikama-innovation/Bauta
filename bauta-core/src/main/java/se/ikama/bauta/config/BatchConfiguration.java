package se.ikama.bauta.config;

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobFactory;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.BatchConfigurer;
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Properties;

@PropertySource("bauta_default.yml")
@Configuration()
@ImportResource({
        "classpath:spring_beans/job_common.xml",
        "file://${bauta.jobBeansDir}/*.xml"
})
//@EnableBatchProcessing()
@EnableVaadin("se.ikama.bauta.ui")
public class BatchConfiguration {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Value("${bauta.homeDir}")
    String homeDir;

    @Value("${bauta.stagingDB.url}")
    String stagingDbUrl;

    @Value("${bauta.stagingDB.driverClassName}")
    String stagingDbDriverClassName;

    @Value("${bauta.stagingDB.username}")
    String stagingDbUsername;

    @Value("${bauta.stagingDB.password}")
    String stagingDbPassword;

    @Value(value = "${bauta.jobRepository.isolationLevelForCreate:ISOLATION_READ_COMMITTED}")
    String isolationLevelForCreate;

    @Value(value = "${bauta.hsqldb.properties:hsqldb.tx=mvcc}")
    String hsqldbProperties;





    /**
     * Semicolon-separated list of extra properties to pass to the DataSource upon creation.
     * Typically not needed. One use-case is when using Oracle Wallet for authentication. Then you
     * must provide connection properties here, e.g.
     * oracle.net.wallet_location=(source=(method=file)(method_data=(directory=/opt/oracle/mywallet)))
     */
    @Value(value = "${bauta.stagingDB.connectionProperties:}")
    String stagingDbConnectionProperties;

    @Bean()
    @Primary
    DataSource dataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        dataSource.setUrl("jdbc:hsqldb:file:" + homeDir + "/db/data");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setConnectionProperties(hsqldbProperties);
        log.info("Creating batch datasource {}", dataSource);
        log.info("HSQLDB properties: {}", hsqldbProperties);
        return dataSource;
    }


    @Bean
    public DataSourceInitializer dataSourceInitializer() {
        final DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource());
        initializer.setDatabasePopulator(databasePopulator());
        return initializer;
    }

    private DatabasePopulator databasePopulator() {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        ClassPathResource schemaScript = new ClassPathResource("sql/schema-hsqldb.sql");
        populator.addScript(schemaScript);
        populator.setContinueOnError(true);
        return populator;
    }


    @Bean
    @Primary
    PlatformTransactionManager transactionManager() {

        return new DataSourceTransactionManager(dataSource());
    }



    @Bean
    JobRepository jobRepository() throws Exception {
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();

        factory.setDataSource(dataSource());
        factory.setTransactionManager(transactionManager());
        log.info("jobRepository.isolationLevelForCreate: {}", isolationLevelForCreate);
        factory.setIsolationLevelForCreate(isolationLevelForCreate);
        factory.setTablePrefix("BATCH_");
        factory.setMaxVarCharLength(1000);
        factory.afterPropertiesSet();

        return factory.getObject();
    }

    @Bean
    public JobExplorer jobExplorer() throws Exception {
        JobExplorerFactoryBean jobExplorer = new JobExplorerFactoryBean();
        jobExplorer.setDataSource(dataSource());
        jobExplorer.setTablePrefix("BATCH_");
        jobExplorer.afterPropertiesSet();
        return jobExplorer.getObject();

    }

    @Bean
    public JobLauncher jobLauncher() throws Exception {
        SimpleJobLauncher jobLauncher = new SimpleJobLauncher();
        jobLauncher.setJobRepository(jobRepository());
        jobLauncher.setTaskExecutor(taskExecutor());
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }


    @Bean()
    JobOperator jobOperator() throws Exception {
        SimpleJobOperator jobOperator = new SimpleJobOperator();

        jobOperator.setJobExplorer(jobExplorer());
        jobOperator.setJobRepository(jobRepository());
        jobOperator.setJobRegistry(jobRegistry());
        jobOperator.setJobLauncher(jobLauncher());
        jobOperator.afterPropertiesSet();
        return jobOperator;
    }

    @Bean
    @Primary
    public JobRegistry jobRegistry()  {
        return new MapJobRegistry();
    }
    @Bean
    public JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor() {
        JobRegistryBeanPostProcessor postProcessor = new JobRegistryBeanPostProcessor();
        postProcessor.setJobRegistry(jobRegistry());
        return postProcessor;
    }

    // Staging database
    @Bean(name = "stagingDataSource")
    //@ConditionalOnProperty(prefix = "bauta", name = "stagingDB.url")
    DataSource stagingDataSource() {
        log.info("Setting up staging DB. Url is {}", stagingDbUrl);
        log.info("Username is {}", stagingDbUsername);
        log.info("Password is {}", stagingDbPassword != null ? "********" : null);

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(stagingDbDriverClassName);
        dataSource.setUrl(stagingDbUrl);
        // username + password will typically be required, but may be left out when using something like Oracle Wallet
        if (StringUtils.isNotEmpty(stagingDbUsername) && StringUtils.isNotEmpty(stagingDbPassword)) {
            dataSource.setUsername(stagingDbUsername);
            dataSource.setPassword(stagingDbPassword);
        }
        if (StringUtils.isNotEmpty(stagingDbConnectionProperties)) {
            //Properties props = PropertiesUtils.fromCommaSeparatedString(stagingDbConnectionProperties);
            dataSource.setConnectionProperties(stagingDbConnectionProperties);
            log.info("Properties: {}", stagingDbConnectionProperties);
        }

        return dataSource;
    }

    @Bean(name = "stagingTransactionManager")
    PlatformTransactionManager stagingTransactionManager() {
        return new DataSourceTransactionManager(stagingDataSource());
    }

    @Bean
    @Primary
    TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(-1);
        return executor;
    }
    @Bean (name = "multiTaskExecutor")
    TaskExecutor multiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(25);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

}
