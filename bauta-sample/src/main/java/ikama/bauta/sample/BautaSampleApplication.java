package ikama.bauta.sample;

import ikama.bauta.core.BautaManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@EnableAutoConfiguration
public class BautaSampleApplication {

    @Autowired
    private BautaManager bautaManager;

    public static void main(String[] args) {

        SpringApplication.run(BautaSampleApplication.class, args);
    }


    public void run(String... args) throws Exception {
        System.out.println("Running");

    }
}