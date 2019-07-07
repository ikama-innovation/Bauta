package ikama.batchc3.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "batchc3")
public class C3Properties {

    private String homeDir;
    private String jobBeansDir;
    private String reportDir;

    public String getHomeDir() {
        return homeDir;
    }

    public void setHomeDir(String homeDir) {
        this.homeDir = homeDir;
    }

    public String getJobBeansDir() {
        return jobBeansDir;
    }

    public void setJobBeansDir(String jobBeansDir) {
        this.jobBeansDir = jobBeansDir;
    }

    public String getReportDir() {
        return reportDir;
    }

    public void setReportDir(String reportDir) {
        this.reportDir = reportDir;
    }
}
