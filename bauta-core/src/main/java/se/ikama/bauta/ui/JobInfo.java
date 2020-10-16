package se.ikama.bauta.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.dom.ElementFactory;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import se.ikama.bauta.core.BasicJobInstanceInfo;

import java.util.Locale;

@Tag("div")
public class JobInfo extends Component {

    public JobInfo(BasicJobInstanceInfo job) {

        getElement().setAttribute("class", "job-info");
        update(job);
    }
    public void update(BasicJobInstanceInfo job) {
        String startTime = job.getStartTime() != null ? DateFormatUtils.format(job.getStartTime(), "yyMMdd HH:mm:ss", Locale.US):"-";

        String endTime = job.getEndTime() != null ? DateFormatUtils.format(job.getEndTime(), "yyMMdd HH:mm:ss", Locale.US) : "-";
               String duration = job != null ? DurationFormatUtils.formatDuration(job.getDuration(), "HH:mm:ss") : "";
                String latestDuration=  job != null ? DurationFormatUtils.formatDuration(job.getLatestDuration(), "HH:mm:ss") : "";
                String params = job.getJobParameters() != null ? job.getJobParameters().toString() : "";

        String html =
        "<span>Instance ID: "+job.getInstanceId()+"<br>" +
                "ExecutionID: "+job.getLatestExecutionId()+"<br>" +
                "Executions: "+job.getExecutionCount()+"<br>" +
                "Status: <div class='batch_status batch_status_label' data-status="+job.getExecutionStatus()+">"+job.getExecutionStatus()+"</div><br>" +
                "Started: "+startTime+"<br>" +
                "Ended: "+endTime+"<br>" +
                "Latest Duration: "+latestDuration+"<br>" +
                "Total Duration: "+duration+"<br>" +
                "Exit status: <div class='batch_status batch_status_label' data-status="+job.getExitStatus()+">"+job.getExitStatus()+"</div><br>" +
                "Params: "+params+"<br></span>";
        this.getElement().removeAllChildren();
        this.getElement().appendChild(new Html(html).getElement());
    }
}
