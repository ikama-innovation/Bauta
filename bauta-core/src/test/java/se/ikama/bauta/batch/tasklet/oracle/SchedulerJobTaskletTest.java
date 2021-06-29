package se.ikama.bauta.batch.tasklet.oracle;

import org.junit.Assert;
import org.junit.Test;


public class SchedulerJobTaskletTest {

    @Test
    public void testSchedulerName() {
        ScheduledJobTasklet sjt = new ScheduledJobTasklet();
        String jobName = sjt.createSchedulerJobName("demo_using_a_very_long_step_name");
        Assert.assertTrue(jobName.length() <= 255);
        System.out.println(jobName);
        sjt.setSchedulerNameMaxLength(30);
        jobName = sjt.createSchedulerJobName("demo_using_a_very_long_step_name");
        Assert.assertTrue(jobName.length() <= 30);
        System.out.println(jobName);
    }

}
