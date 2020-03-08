package se.ikama.bauta.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.PropertySource;

// TODO: Move this to a better place. But has to be set early on
@EnableAutoConfiguration
@PropertySource("git.properties")
///@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class BautaSampleOracleApplication {

    public static void main(String[] args) {

        SpringApplication.run(BautaSampleOracleApplication.class, args);
    }
}