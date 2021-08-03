package se.ikama.bauta.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Tag;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import se.ikama.bauta.core.BasicJobInstanceInfo;

import java.util.Locale;

@Tag("div")
public class JobInfo extends Component {
    boolean expanded;


	public JobInfo(BasicJobInstanceInfo job) {

        getElement().setAttribute("class", "job-info");
        update(job);
    }
    public void update(BasicJobInstanceInfo job) {

        StringBuilder html = new StringBuilder("<ul style='list-style: none'>");
        if (job.getInstanceId() != null && expanded) html.append("<li>Instance/Execution: "+job.getInstanceId()+"/"+job.getLatestExecutionId());
        if (job.getExecutionCount() > 0 && expanded)  html.append("<li>Executions: " + job.getExecutionCount());
        if (job.getExecutionStatus() != null ) html.append("<li>Status: <div class='batch_status batch_status_label' data-status="+job.getExecutionStatus()+">"+job.getExecutionStatus()+"</div>");
        if (job.getStartTime() != null) html.append("<li>Started: ").append(DateFormatUtils.format(job.getStartTime(), "yyMMdd HH:mm:ss", Locale.US));
        if (job.getEndTime() != null && expanded)  html.append("<li>Ended: ").append(DateFormatUtils.format(job.getEndTime(), "yyMMdd HH:mm:ss", Locale.US));
        if (job.getLatestDuration()  > 0 && expanded) html.append("<li>Latest Duration: ").append(DurationFormatUtils.formatDuration(job.getLatestDuration(), "HH:mm:ss"));
        if (job.getDuration() > 0 && expanded) html.append("<li>Total Duration: ").append(DurationFormatUtils.formatDuration(job.getDuration(), "HH:mm:ss"));
        if (job.getExitStatus() != null && !"UNKNOWN".equals(job.getExitStatus()) && expanded) html.append("<li>Exit status: <div class='batch_status batch_status_label' data-status="+job.getExitStatus()+">"+job.getExitStatus()+"</div>");
        if (job.getJobParameters() != null && job.getJobParameters().size() > 0) html.append("<li>Params: ").append(job.getJobParameters().toString());
        this.getElement().removeAllChildren();
        this.getElement().appendChild(new Html(html.toString()).getElement());
    }
}
