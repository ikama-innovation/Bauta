package se.ikama.bauta.config;

import com.vaadin.flow.spring.annotation.EnableVaadin;

import jakarta.annotation.PostConstruct;
import se.ikama.bauta.batch.listeners.MailNotificationJobListener;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.Properties;

import javax.sql.DataSource;

@PropertySource("bauta_default.yml")
@Configuration()
@ImportResource({
        "classpath:spring_beans/job_common.xml", "file://${bauta.jobBeansDir}/*.xml",
        "file://${bauta.jobBeansDir2}/*.xml", "file://${bauta.jobBeansDir3}/*.xml"
})
@EnableVaadin("se.ikama.bauta.ui")
@EnableConfigurationProperties
public class BatchConfiguration {

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Autowired
    Environment env;

    @Value("${bauta.homeDir}")
    String homeDir;

    @Value("${bauta.stagingDB.url:}")
    String stagingDbUrl;

    @Value("${bauta.stagingDB.driverClassName:}")
    String stagingDbDriverClassName;

    @Value("${bauta.stagingDB.username:}")
    String stagingDbUsername;

    @Value("${bauta.stagingDB.password:}")
    String stagingDbPassword;

    @Value(value = "${bauta.jobRepository.isolationLevelForCreate:ISOLATION_READ_COMMITTED}")
    String isolationLevelForCreate;

    @Value(value = "${bauta.batchDataSource.url:jdbc:hsqldb:file:${bauta.homeDir}/db/data;hsqldb.tx=mvcc}")
    String batchDataSourceUrl;

    @Value("${bauta.batch.executor.maxThreads:50}")
    int multiThreadExecutorMaxPoolSize;

    @Value("${bauta.batch.executor.coreThreads:10}")
    int multiThreadExecutorCorePoolSize;

    @Value("${bauta.batch.executor.queueCapacity:999999}")
    int multiThreadExecutorQueueCapacity;

    /**
     * Semicolon-separated list of extra properties to pass to the DataSource upon
     * creation.
     * Typically not needed. One use-case is when using Oracle Wallet for
     * authentication. Then you
     * must provide connection properties here, e.g.
     * oracle.net.wallet_location=(source=(method=file)(method_data=(directory=/opt/oracle/mywallet)))
     */
    @Value(value = "${bauta.stagingDB.connectionProperties:}")
    String stagingDbConnectionProperties;

    @Value(value = "${bauta.stagingDB.connectionPool.maxTotal:20}")
    int stagingDbMaxTotal;

    @PostConstruct
    public void init() {
        log.info("BatchConfiguration init");
    }

    @Bean()
    @Primary
    DataSource dataSource() {

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setMaxTotal(100);
        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        dataSource.setUrl(batchDataSourceUrl);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaxWait(Duration.ofSeconds(10));
        dataSource.setMaxConn(Duration.ofMinutes(10));
        dataSource.setLogExpiredConnections(false);

        try {
            log.info("Batch DB url: {}", batchDataSourceUrl);
            log.info("Driver is {}", dataSource.getConnection().getMetaData().getDriverName());
            log.info("TX isolation {}", dataSource.getConnection().getMetaData().getDefaultTransactionIsolation());
            log.info("DB Product name {}", dataSource.getConnection().getMetaData().getDatabaseProductName());
        } catch (Exception e) {

        }
        // dataSource.setConnectionProperties(hsqldbProperties);
        log.info("Creating batch datasource {}", dataSource);
        return dataSource;
    }

    @Bean
    public DataSourceInitializer dataSourceInitializer() {
        log.info("Setting up datasource initializer");
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
        jobExplorer.setTransactionManager(transactionManager());
        jobExplorer.afterPropertiesSet();
        return jobExplorer.getObject();

    }

    @Bean
    public JobLauncher jobLauncher() throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
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
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    /**
     * TODO: Replace with JobRegistrySmartInitializingSingleton
     * @return
     */
    @Bean
    public JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor() {
        JobRegistryBeanPostProcessor postProcessor = new JobRegistryBeanPostProcessor();
        postProcessor.setJobRegistry(jobRegistry());
        return postProcessor;
    }

    // Staging database
    @Bean(name = "stagingDataSource")
    @ConditionalOnProperty(prefix = "bauta", name = "stagingDB.url")
    DataSource stagingDataSource() {
        log.info("Setting up staging DB. Url is {}", stagingDbUrl);
        log.info("Connection pool max size: {}", stagingDbMaxTotal);
        log.info("Username is {}", stagingDbUsername);
        log.info("Password is {}", stagingDbPassword != null ? "********" : null);

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setMaxWait(Duration.ofSeconds(10));
        dataSource.setMaxConn(Duration.ofMinutes(10));
        dataSource.setMaxTotal(stagingDbMaxTotal);
        dataSource.setLogExpiredConnections(false);

        dataSource.setDriverClassName(stagingDbDriverClassName);
        dataSource.setUrl(stagingDbUrl);

        // username + password will typically be required, but may be left out when
        // using something like Oracle Wallet
        if (StringUtils.isNotEmpty(stagingDbUsername) && StringUtils.isNotEmpty(stagingDbPassword)) {
            dataSource.setUsername(stagingDbUsername);
            dataSource.setPassword(stagingDbPassword);
        }
        if (StringUtils.isNotEmpty(stagingDbConnectionProperties)) {
            // Properties props =
            // PropertiesUtils.fromCommaSeparatedString(stagingDbConnectionProperties);
            dataSource.setConnectionProperties(stagingDbConnectionProperties);
            log.info("Properties: {}", stagingDbConnectionProperties);
        }

        return dataSource;
    }

    @Bean(name = "stagingTransactionManager")
    @ConditionalOnProperty(prefix = "bauta", name = "stagingDB.url")
    PlatformTransactionManager stagingTransactionManager() {
        return new DataSourceTransactionManager(stagingDataSource());
    }

    @Bean
    @Primary
    TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("bauta-");
        executor.setConcurrencyLimit(10);
        executor.setThreadNamePrefix("bauta-");

        return executor;
    }

    @Bean(name = "multiTaskExecutor")
    TaskExecutor multiTaskExecutor() {
        log.info("Setting up multi-task-executor. core/max/queue: {}/{}/{}", multiThreadExecutorCorePoolSize,
                multiThreadExecutorMaxPoolSize, multiThreadExecutorQueueCapacity);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("bautam-");
        executor.setCorePoolSize(multiThreadExecutorCorePoolSize);
        executor.setMaxPoolSize(multiThreadExecutorMaxPoolSize);
        executor.setQueueCapacity(multiThreadExecutorQueueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean
    @ConditionalOnProperty("bauta.mail.host")
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();

        javaMailSender.setHost(env.getProperty("bauta.mail.host"));
        log.info("mail host is {}", javaMailSender.getHost());
        javaMailSender.setPort(Integer.parseInt(env.getProperty("bauta.mail.port")));
        javaMailSender.setUsername(env.getProperty("bauta.mail.username"));
        javaMailSender.setPassword(env.getProperty("bauta.mail.password"));
        javaMailSender.setDefaultEncoding("UTF-8");
        javaMailSender.setJavaMailProperties(getMailProperties());

        return javaMailSender;
    }

    private Properties getMailProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", env.getProperty("bauta.mail.transport.protocol"));
        properties.setProperty("mail.smtp.auth", env.getProperty("bauta.mail.smtp.auth"));
        properties.setProperty("mail.smtp.starttls.enable", env.getProperty("bauta.mail.smtp.starttls.enable"));
        properties.setProperty("mail.debug", env.getProperty("bauta.mail.debug"));
        properties.setProperty("mail.smtp.from", env.getProperty("bauta.mail.from"));
        return properties;
    }

    @Bean
    public MailNotificationJobListener mailNotificationJobListener() {
        MailNotificationJobListener listener = new MailNotificationJobListener();
        return listener;
    }

}
