package se.ikama.bauta.sample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import se.ikama.bauta.core.BautaManager;

/**
 * Very basic sample application
 */
@EnableAutoConfiguration
public class BautaSampleApplication {

    @Autowired
    private BautaManager bautaManager;

    public static void main(String[] args) {

        SpringApplication.run(BautaSampleApplication.class, args);
    }


    public void run(String... args) throws Exception {


    }
}