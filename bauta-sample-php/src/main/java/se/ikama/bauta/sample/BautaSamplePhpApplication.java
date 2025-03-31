package se.ikama.bauta.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.PropertySource;

import se.ikama.bauta.BautaApplication;

@PropertySource("git.properties")
public class BautaSamplePhpApplication extends BautaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BautaSamplePhpApplication.class, args);
    }
}