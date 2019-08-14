package ikama.bauta.autoconfigure;

import ikama.bauta.core.BautaConfig;
import ikama.bauta.core.BautaManager;

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

import static ikama.bauta.core.BautaConfigParams.*;

@Configuration
@ConditionalOnClass(BautaManager.class)
@EnableConfigurationProperties(BautaProperties.class)
@ComponentScan(basePackages = "ikama.bauta.cli,ikama.bauta.ui,ikama.bauta.autoconfigure,ikama.bauta.config")
public class BautaAutoConfiguration {

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

}
