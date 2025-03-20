package se.ikama.bautastandalone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import se.ikama.bauta.BautaApplication;

@SpringBootApplication
public class BautaStandaloneApplication extends BautaApplication{

    public static void main(String[] args) {
        SpringApplication.run(BautaStandaloneApplication.class, args);
    }

}
