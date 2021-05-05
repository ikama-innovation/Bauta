package se.ikama.bauta.batch.listeners;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.internet.MimeMessage;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import net.bytebuddy.asm.Advice.This;

/**
 * Sends email notifications when jobs finish.
 *
 * @author arbinmat
 */
public class MailNotificationJobListener implements JobExecutionListener, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationJobListener.class);

    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("#{'${bauta.mail.notifications.recipients:}'.split(',')}")
    private String[] recipients;
    
    @Value("${bauta.mail.notifications.success:false}")
    private boolean sendSuccessNotifications;
    
    @Value("${bauta.mail.notifications.failure:false}")
    private boolean sendFailureNotifications;

    /**
     * Will only send mail notifications for jobs with a name matching this filter. Defaults to wildcard.
     */
    @Value("${bauta.mail.notifications.jobNameFilter:.*}")
    private String jobFilter;

    @Value("${bauta.reportDir}")
    String reportDir;

    @Value("${bauta.version}")
    String version;

    @Value("${spring.profiles.active}")
    String profile;

    ExecutorService sendMailExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.debug("After job '{}', exitStatus: {}", jobExecution.getJobInstance().getJobName(), jobExecution.getExitStatus());
        log.debug("Enabled. success: {}, failure: {}",sendSuccessNotifications, sendFailureNotifications);
        String jobName = jobExecution.getJobInstance().getJobName();
        if (!jobName.matches(this.jobFilter)) {
        	log.debug("Job name '{}' does not match job filter '{}'. Will not send mail", jobName, this.jobFilter);
        	return;
        }
        if (jobExecution.getExitStatus().compareTo(ExitStatus.FAILED) == 0 && sendFailureNotifications) {
            // Send email
            log.debug("Sending status email: Job '{}' failed at {}", jobExecution.getJobInstance().getJobName(), jobExecution.getEndTime());
            sendMailExecutor.submit(() -> {
                sendMail(jobExecution, ExitStatus.FAILED);
            });

        } else if (jobExecution.getExitStatus().compareTo(ExitStatus.COMPLETED) == 0 && sendSuccessNotifications) {
            // Send email
            log.debug("Sending status email: Job '{}' succeeded at {}", jobExecution.getJobInstance().getJobName(), jobExecution.getEndTime());
            sendMailExecutor.submit(() -> {
                sendMail(jobExecution, ExitStatus.COMPLETED);
            });
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {

    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

    }

    public void sendMail(JobExecution jobExecution, ExitStatus exitStatus) {
        try {
            // Prepare the evaluation context
            final Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("jobExecution", jobExecution);
            ctx.setVariable("profile", profile);
            ctx.setVariable("version", version);
            ctx.setVariable("jobParameters", jobExecution.getJobParameters().toProperties().toString());

            // Prepare message using a Spring helper
            final MimeMessage mimeMessage = this.javaMailSender.createMimeMessage();

            final MimeMessageHelper message
                    = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            String status = "succeeded";
            String template = "jobsuccess";
            if (exitStatus.compareTo(ExitStatus.FAILED) == 0) {
                status = "failed";
                template = "jobfailure";
            }
            message.setSubject("Job " + jobExecution.getJobInstance().getJobName() + " " + status);
            message.setTo(recipients);

            // If there is a failed step and it contains a log, add it to the context
            try {
                for (StepExecution se : jobExecution.getStepExecutions()) {
                    if (se.getExitStatus().compareTo(ExitStatus.FAILED) == 0) {
                        List<String> reportUrls = (List<String>) se.getExecutionContext().get("reportUrls");
                        if (reportUrls != null) {
                            for (String reportUrl : reportUrls) {
                                if (reportUrl.endsWith(".log")) {
                                    log.debug("Found .log file for failed step. Adding it to the context");
                                    String filePath = reportDir + reportUrl.substring("reports".length());
                                    File logFile = new File(filePath);
                                    String logContent = FileUtils.readFileToString(logFile, "UTF-8");
                                    log.debug("log: {}", logContent);
                                    ctx.setVariable("log", logContent);
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to attach log to template context", e);
            }
            // Create the HTML body using Thymeleaf
            final String htmlContent = this.templateEngine.process(template, ctx);
            message.setText(htmlContent, true);
            log.debug("Sending mail..");
            javaMailSender.send(mimeMessage);
            log.debug("Sending mail. Done!");
        } catch (Exception e) {
            log.warn("Failed to send email", e);
        }
    }
}
