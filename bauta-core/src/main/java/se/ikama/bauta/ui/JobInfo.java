package se.ikama.bauta.ui;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Tag;

import se.ikama.bauta.core.BasicJobInstanceInfo;

@Tag("div")
public class JobInfo extends Component {
    private static final long serialVersionUID = 1L;
    boolean expanded;
    String executionStatus;
    BasicJobInstanceInfo jobInstanceInfo;

    public JobInfo(BasicJobInstanceInfo job) {
	this.jobInstanceInfo = job;
	getElement().setAttribute("class", "job-info");
	update();
    }

    public void update(BasicJobInstanceInfo job)  {
	this.jobInstanceInfo = job;
	update();
    }
    public void update() {
	this.executionStatus = jobInstanceInfo.getExecutionStatus();
	StringBuilder html = new StringBuilder("<ul style='list-style: none;padding-inline-start: 0'>");

	if (jobInstanceInfo.getInstanceId() != null && expanded)
	    html.append("<li>Instance/Execution: " + jobInstanceInfo.getInstanceId() + "/" + jobInstanceInfo.getLatestExecutionId());
	if (jobInstanceInfo.getExecutionCount() > 0 && expanded)
	    html.append("<li>Executions: " + jobInstanceInfo.getExecutionCount());
	if (jobInstanceInfo.getExecutionStatus() != null)
	    html.append("<li>Status: <div class='batch_status batch_status_label' data-status=" + jobInstanceInfo.getExecutionStatus() + ">" + jobInstanceInfo.getExecutionStatus() + "</div>");
	if (jobInstanceInfo.getStartTime() != null)
	    html.append("<li>Started: ").append(formatDate(jobInstanceInfo.getStartTime()));
	if (jobInstanceInfo.getEndTime() != null)
	    html.append("<li>Ended: ").append(formatDate(jobInstanceInfo.getEndTime()));
	if (jobInstanceInfo.getLatestDuration() > 0 && expanded)
	    html.append("<li>Latest Duration: ").append(DurationFormatUtils.formatDuration(jobInstanceInfo.getLatestDuration(), "HH:mm:ss"));
	if (jobInstanceInfo.getDuration() > 0 && expanded)
	    html.append("<li>Total Duration: ").append(DurationFormatUtils.formatDuration(jobInstanceInfo.getDuration(), "HH:mm:ss"));
	if (jobInstanceInfo.getExitStatus() != null && !"UNKNOWN".equals(jobInstanceInfo.getExitStatus()) && expanded)
	    html.append("<li>Exit status: <div class='batch_status batch_status_label' data-status=" + jobInstanceInfo.getExitStatus() + ">" + jobInstanceInfo.getExitStatus() + "</div>");
	if (jobInstanceInfo.getJobParameters() != null && jobInstanceInfo.getJobParameters().size() > 0 && expanded)
	    html.append("<li>Params: ").append(jobInstanceInfo.getJobParameters().toString());
	html.append("</ul>");
	this.getElement().removeAllChildren();
	this.getElement().appendChild(new Html(html.toString()).getElement());
    }

    private static String formatDate(Date date) {
	if (DateUtils.isSameDay(date, new Date())) {
	    return "Today " + DateFormatUtils.format(date, "HH:mm:ss", Locale.US);
	}
	else if (DateUtils.isSameDay(DateUtils.addDays(new Date(), -1), date)) {
	    return "Yesterday " + DateFormatUtils.format(date, "HH:mm:ss", Locale.US);
	} else {
	    return DateFormatUtils.format(date, "yyMMdd HH:mm:ss", Locale.US);
	}
    }
    
    public void setExpanded(boolean expanded) {
	this.expanded = expanded;
    }
}
