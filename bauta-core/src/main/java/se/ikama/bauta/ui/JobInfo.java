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

    public JobInfo(BasicJobInstanceInfo job) {

	getElement().setAttribute("class", "job-info");
	update(job);
    }

    public void update(BasicJobInstanceInfo job) {
	this.executionStatus = job.getExecutionStatus();
	StringBuilder html = new StringBuilder("<ul style='list-style: none;padding-inline-start: 0'>");

	if (job.getInstanceId() != null && expanded)
	    html.append("<li>Instance/Execution: " + job.getInstanceId() + "/" + job.getLatestExecutionId());
	if (job.getExecutionCount() > 0 && expanded)
	    html.append("<li>Executions: " + job.getExecutionCount());
	if (job.getExecutionStatus() != null)
	    html.append("<li>Status: <div class='batch_status batch_status_label' data-status=" + job.getExecutionStatus() + ">" + job.getExecutionStatus() + "</div>");
	if (job.getStartTime() != null)
	    html.append("<li>Started: ").append(formatDate(job.getStartTime()));
	if (job.getEndTime() != null)
	    html.append("<li>Ended: ").append(formatDate(job.getEndTime()));
	if (job.getLatestDuration() > 0 && expanded)
	    html.append("<li>Latest Duration: ").append(DurationFormatUtils.formatDuration(job.getLatestDuration(), "HH:mm:ss"));
	if (job.getDuration() > 0 && expanded)
	    html.append("<li>Total Duration: ").append(DurationFormatUtils.formatDuration(job.getDuration(), "HH:mm:ss"));
	if (job.getExitStatus() != null && !"UNKNOWN".equals(job.getExitStatus()) && expanded)
	    html.append("<li>Exit status: <div class='batch_status batch_status_label' data-status=" + job.getExitStatus() + ">" + job.getExitStatus() + "</div>");
	if (job.getJobParameters() != null && job.getJobParameters().size() > 0 && expanded)
	    html.append("<li>Params: ").append(job.getJobParameters().toString());
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
