package se.ikama.bauta.autoconfigure;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import se.ikama.bauta.core.BautaConfig;
import se.ikama.bauta.core.BautaManager;

import static se.ikama.bauta.core.BautaConfigParams.*;

@Configuration
@ConditionalOnClass(BautaManager.class)
@EnableConfigurationProperties(BautaProperties.class)
@ComponentScan(basePackages = {"se.ikama.bauta.cli","se.ikama.bauta.ui","se.ikama.bauta.autoconfigure","se.ikama.bauta.config"})
public class BautaAutoConfiguration implements EnvironmentPostProcessor {

    @Autowired
    private BautaProperties bautaProperties;

    @Bean
    @ConditionalOnMissingBean
    public BautaConfig bautaConfig() {

        String homeDir = bautaProperties.getHomeDir() == null ? System.getProperty("bauta.homeDir") : bautaProperties.getHomeDir();
        String jobBeansDir = bautaProperties.getJobBeansDir() == null ? System.getProperty("bauta.jobBeansDir") : bautaProperties.getJobBeansDir();
        String reportDir = bautaProperties.getJobBeansDir() == null ? System.getProperty("bauta.reportDir") : bautaProperties.getReportDir();

        BautaConfig bautaConfig = new BautaConfig();
        bautaConfig.put(HOME_DIR, homeDir);
        bautaConfig.put(JOB_BEANS_DIR, jobBeansDir);
        bautaConfig.put(REPORT_DIR, reportDir);
        return bautaConfig;
    }

    @Bean
    @ConditionalOnMissingBean
    public BautaManager bautaManager(BautaConfig bautaConfig, JobOperator jobOperator, JobRepository jobRepository, JobExplorer jobExplorer) {

        return new BautaManager(bautaConfig, jobOperator, jobRepository, jobExplorer);
    }


    /**
     * Gives us a chance to customize the Environment or Application at an early stage
     * @param environment
     * @param application
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        /**
         * Avoiding problems with autoconfiguration of spring batch.
         * We want to create our own data sources.
         */
        environment.getSystemProperties().put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration");

         //application.setAllowBeanDefinitionOverriding(true);
    }
}
