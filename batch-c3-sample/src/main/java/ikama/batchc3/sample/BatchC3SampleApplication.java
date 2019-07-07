package ikama.batchc3.sample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import ikama.batchc3.core.C3Manager;
import org.springframework.context.annotation.ComponentScan;

@EnableAutoConfiguration
public class BatchC3SampleApplication {

    @Autowired
    private C3Manager c3Manager;

    public static void main(String[] args) {

        SpringApplication.run(BatchC3SampleApplication.class, args);
    }


    public void run(String... args) throws Exception {
        System.out.println("Running");

    }
}