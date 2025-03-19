package se.ikama.bauta.cli;

import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobInstanceAlreadyExistsException;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Quit;
import se.ikama.bauta.core.BautaManager;

import jakarta.annotation.PostConstruct;
import java.util.Collection;

/**
 *
 * Provides command shell support for Bauta. Supports job management tasks such as starting/stopping jobs etc.
 */
@ShellComponent()
public class CommandShell implements Quit.Command {

    @Autowired
    BautaManager bautaManager;


    @PostConstruct
    public void init() {

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
    public String start(String jobName) throws JobInstanceAlreadyExistsException, JobParametersInvalidException, NoSuchJobException {
        Long executionId = null;
        executionId = bautaManager.startJob(jobName, (String)null);
        return "Started: " + jobName + " with execution id " + executionId;
    }

    @ShellMethod("Provides information about this instance")
    public String info() {
        StringBuilder sb = new StringBuilder();
        for(String s: bautaManager.getServerInfo()) {
            sb.append(s).append(System.lineSeparator());
        }
        return sb.toString();
    }
    @ShellMethod("Quit.")
    public void quit() {
        // TODO: Better way to shutdown gracefully?
        System.exit(0);
    }

}
