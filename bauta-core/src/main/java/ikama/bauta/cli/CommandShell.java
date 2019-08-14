package ikama.bauta.cli;

import ikama.bauta.core.BautaManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import javax.annotation.PostConstruct;
import java.util.Collection;

@ShellComponent
public class CommandShell {

    @Autowired
    BautaManager bautaManager;

    @PostConstruct
    public void init() {
        System.out.println("Starting shell");
    }


    @ShellMethod("Job status")
    public String status() throws Exception {
        // invoke service
        StringBuilder sb = new StringBuilder();
        Collection<String> jobs = bautaManager.listJobSummaries();

        int jobShortId = 0;
        for (String job : jobs) {
            sb.append(job);
            sb.append(" [").append(jobShortId++).append("]");
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    @ShellMethod("List job names")
    public String list() throws Exception {
        // invoke service
        StringBuilder sb = new StringBuilder();
        Collection<String> jobs = bautaManager.listJobNames();

        int jobShortId = 0;
        for (String job : jobs) {
            sb.append(job);
            sb.append(" [").append(jobShortId++).append("]");
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    @ShellMethod("Start a job")
    public String start(String jobName) {
        Long executionId = null;
        executionId = bautaManager.startJob(jobName);
        return "Started: " + jobName + " with execution id " + executionId;
    }

    @ShellMethod("Exit")
    public String exit(String jobName) {
        
        System.exit(0);
    }
}
