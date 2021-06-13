package se.ikama.bauta.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.PropertySource;

// TODO: Move this to a better place. But has to be set early on
//@EnableAutoConfiguration
@PropertySource(value = "classpath:git.properties", ignoreResourceNotFound = true)
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class, BatchAutoConfiguration.class, ErrorMvcAutoConfiguration.class})
public class BautaSampleOracleApplication {

    public static void main(String[] args) {

        SpringApplication.run(BautaSampleOracleApplication.class, args);
    }
}