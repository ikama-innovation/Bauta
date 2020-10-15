package se.ikama.bauta.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.Element;
import se.ikama.bauta.core.JobInstanceInfo;


@Tag("div")
public class StepProgressBar extends Component {
    Element unknownSection = new Element("div");
    Element completedSection = new Element("div");
    Element failedSection = new Element("div");
    Element stoppedSection = new Element("div");
    Element runningSection = new Element("div");

    public StepProgressBar() {
        getElement().setAttribute("class","step-progress-bar");
        unknownSection.setAttribute("class","step-progress-section step-progress-unknown");
        failedSection.setAttribute("class","step-progress-section step-progress-failed");
        stoppedSection.setAttribute("class","step-progress-section step-progress-stopped");
        runningSection.setAttribute("class","step-progress-section step-progress-running");
        completedSection.setAttribute("class","step-progress-section step-progress-completed");

        getElement().appendChild(completedSection, failedSection, stoppedSection, runningSection, unknownSection);
    }
    private static void updateSection(Element section, int progress) {
        if (progress > 0) {
            section.getStyle().set("flex-grow", Integer.toString(progress));
            section.setText(Integer.toString(progress));
        }
        else {
            section.setVisible(false);
        }
    }
    public void update(JobInstanceInfo jii) {
        updateSection(unknownSection, jii.getUnknownCount());
        updateSection(completedSection, jii.getCompletedCount());
        updateSection(failedSection, jii.getFailedCount());
        updateSection(stoppedSection, jii.getStoppedCount());
        updateSection(runningSection, jii.getRunningCount());
    }
}
