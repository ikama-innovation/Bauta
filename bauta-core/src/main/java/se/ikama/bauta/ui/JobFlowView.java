package se.ikama.bauta.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.svg.Svg;
import com.vaadin.flow.component.svg.elements.*;
import org.springframework.beans.factory.annotation.Autowired;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.core.JobFlowGraph;
import se.ikama.bauta.scheduling.JobTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class JobFlowView extends Svg {

    @Autowired
    BautaManager bautaManager;

    double spaceX = 400;
    double spaceY = 200;
    double jobHeight = 120;
    double jobWidth = 200;
    double radialX = 80;
    double radialY = 50;

    private int level;

    public JobFlowView() {
        viewbox(0, 0, 1000, 1000);
        setWidth("100%");
        setHeight("650px");
    }

    @Override
    public void onAttach(AttachEvent attachEvent) {
        update();
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        update();
    }

    private void draw(JobFlowGraph jobFlowGraph) {
        clear();
        level = 0;
        jobFlowGraph.printGraph();
        for (JobFlowNode node : jobFlowGraph.getRootNodes()) {
            recursiveDraw(node, 20 ,20 + spaceY * level, jobFlowGraph);
        }
    }

    private void recursiveDraw(JobFlowNode node, double x, double y, JobFlowGraph jobFlowGraph) {
        List<JobFlowNode> list = jobFlowGraph.getNodesFor(node);
        if (node.getTriggerType() == JobTrigger.TriggerType.CRON) {
            drawEllipse(node, x - (spaceX/2 + radialX), y + (jobHeight - radialY * 2)/2);
        }

        for (int i = 0; i < list.size(); i++) {
            drawLine(x + jobWidth/2, y + jobHeight/2,
                    x + jobWidth/2 + spaceX, y + (jobHeight/2) + (spaceY * i),
                    node.getTriggerType());
        }

        drawRect(node, x, y);
        int lines = 0;
        for (JobFlowNode nextNode : list) {
            recursiveDraw(nextNode, x + spaceX, y + spaceY * lines, jobFlowGraph);
            lines++;
            if (level <= lines) {
                level = lines;
            }
        }
        if (list.isEmpty()) {
            level++;
        }
    }

    private void drawEllipse(JobFlowNode node, double x, double y) {
        drawLine(x + radialX, y + radialY, x + spaceX, y + radialY, JobTrigger.TriggerType.CRON);
        Ellipse ellipse = new Ellipse(UUID.randomUUID().toString(), radialX, radialY);
        ellipse.setFillColor("#1aa3ff");
        ellipse.move(x  , y );
        add(ellipse);

        Text cron = new Text(UUID.randomUUID().toString(), node.getCron());
        cron.setFontFamily("'Roboto', 'Noto', sans-serif");
        cron.setFillColor("white");
        cron.setFontSize("14");
        cron.move(x + radialX*2/node.getCron().length(), y + radialY - Double.parseDouble(cron.getFontSize()) + 5);
        add(cron);
    }

    private void drawLine(double xFrom, double yFrom, double xTo, double yTo, JobTrigger.TriggerType triggerType) {
        Line line = new Line(UUID.randomUUID().toString(),
                new AbstractPolyElement.PolyCoordinatePair(xFrom, yFrom),
                new AbstractPolyElement.PolyCoordinatePair(xTo, yTo));
        line.setStroke("beige", 10, Path.LINE_CAP.ROUND, Path.LINE_JOIN.ROUND);
        add(line);
    }

    private void drawRect(JobFlowNode node, double x, double y) {
        String name = node.getName();
        Rect rect = new Rect(node.getName(), jobWidth, jobHeight);
        rect.setFillColor("#1aa3ff");
        rect.move(x, y);

        if (name.length() > 21 ) {
            for (int i = 14; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (ch == '-' || ch == '_' || ch == '.') {
                    StringBuilder sb = new StringBuilder(name);
                    sb.insert(i+1, "\n");
                    name = sb.toString();
                    break;
                }
            }
        }
        if (!name.contains("\n") && name.length() > 21) {
            StringBuilder sb = new StringBuilder(name);
            sb.insert(20, "\n");
            name = sb.toString();
        }

        Text text = new Text(UUID.randomUUID().toString(), name);
        text.setFontFamily("'Roboto', 'Noto', sans-serif");
        text.setFillColor("white");
        text.setFontSize("14");
        text.move(x + 8, y + 8);

        add(rect);
        add(text);
    }

    private void clear() {
        List<SvgElement> elements = new ArrayList<>(getSvgElements());
        elements.forEach(this::remove);
    }

    public void update(JobFlowGraph jobFlowGraph) {
        draw(jobFlowGraph);
    }
}
