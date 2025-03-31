package se.ikama.bauta.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.PropertySource;

import se.ikama.bauta.BautaApplication;

@PropertySource(value = "classpath:git.properties", ignoreResourceNotFound = true)
public class BautaSampleOracleApplication extends BautaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BautaSampleOracleApplication.class, args);
    }
}