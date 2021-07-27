package se.ikama.bauta.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.svg.Svg;
import com.vaadin.flow.component.svg.elements.*;
import org.springframework.batch.core.Job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class JobFlowGraphics extends VerticalLayout{
    private JobFlowGraph jobFlowGraph;
    private Svg svg = new Svg();
    private HashMap<String, String> statusToColor = new HashMap<>();

    double size = 100;
    double space = size + 10;
    double x = 20;
    double y = 20;

    private double jobWidth = 125;
    private double jobHeight = 75;
    private double gridSize = 200;

    public JobFlowGraphics(JobFlowGraph jobFlowGraph) {
        this.jobFlowGraph = jobFlowGraph;

        statusToColor.put("COMPLETED", "var(--lumo-success-color)");
        statusToColor.put("STARTED", "var(--lumo-primary-color)");
        statusToColor.put("FAILED", "var(--lumo-error-color)");
        statusToColor.put("UNKNOWN", "var(--lumo-contrast-30pct)");
        statusToColor.put("LINE", "rgb(177, 177, 177)");

        svg.viewbox(0, 0, 1000, 1000);
        svg.setWidth("100%");
        svg.setHeight("1000px");
        HorizontalLayout controlButtons = new HorizontalLayout();
        add(controlButtons, svg);
        controlButtons.add(new Button("Toggle Zoom", e -> {
            svg.setZoomEnabled(!svg.isZoomEnabled());
        }));
    }

    public void render() {
        x = 20;
        y = 20;
        clear();
        for (JobFlowNode node : jobFlowGraph.getRoots()) {
            x = 20;
            recursiveDraw(node);
            y += space;
        }
    }

    public void recursiveDraw(JobFlowNode node) {
        Iterator<JobFlowNode> iterator = jobFlowGraph.getNodes(node).iterator();
        if (iterator.hasNext()) {

        }
        Rect rect = drawRect(node);
        Line line = drawLine(iterator.next(), node);
        svg.add(rect);
        svg.add(line);
        svg.update(rect);
        x += space + 40;

        while (iterator.hasNext()) {

        }
        for (JobFlowNode nextNode : jobFlowGraph.getNodes(node)) {
            drawLine(nextNode, node);
            recursiveDraw(nextNode);
            if (jobFlowGraph.getNodes(node).size() > 1) {
                y += space;
                x -= space + 40;
            }
        }
    }

    public Line drawLine(JobFlowNode to, JobFlowNode from) {
        Line line = new Line("line",
                new AbstractPolyElement.PolyCoordinatePair(gridSize, gridSize),
                new AbstractPolyElement.PolyCoordinatePair(gridSize + gridSize, gridSize));
        line.setStroke("beige", 10, Path.LINE_CAP.ROUND, Path.LINE_JOIN.ROUND);
        return line;
    }

    public Rect drawRect(JobFlowNode node) {
        Rect rect = new Rect(node.getName(), jobWidth, jobHeight);
        rect.setFillColor(statusToColor.get(node.getStatus()));
        rect.move(x, y);
        return rect;
    }

    public void clear() {
        List<SvgElement> elements = new ArrayList<>();
        svg.getSvgElements().forEach(elements::add);
        elements.forEach(svg::remove);
    }
}
