package se.ikama.bauta.ui;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SchedulerLab {
    public static void main(String args[]) throws InterruptedException {
        Date date = new Date();
        DateFormat tstampFormat = new SimpleDateFormat("YYMMdd HH:mm:ss");
        System.out.println(tstampFormat.format(date));
        ThreadPoolTaskScheduler s
                = new ThreadPoolTaskScheduler();
        s.setPoolSize(5);
        s.setThreadNamePrefix(
                "ThreadPoolTaskScheduler");
        s.initialize();
        Runnable jobStarter = new Runnable()  {
            public void run() {
                System.out.println("Starting job");
            }
        };
        for (int i=0;i<10;i++) {
            s.initialize();
            CronTrigger cronTrigger = new CronTrigger("* * * * * *");
            s.schedule(jobStarter, cronTrigger);
            System.out.println("Done");
            Thread.sleep(15000);
            System.out.println("Shutting down");
            s.shutdown();
            System.out.println("Done");
        }
    }
}
