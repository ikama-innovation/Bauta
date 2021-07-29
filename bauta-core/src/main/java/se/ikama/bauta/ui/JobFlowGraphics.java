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

import static org.apache.commons.lang3.math.NumberUtils.max;

public class JobFlowGraphics extends VerticalLayout{
    private JobFlowGraph jobFlowGraph;
    private Svg svg = new Svg();
    private HashMap<String, String> statusToColor = new HashMap<>();

    double spaceX = 200;
    double spaceY = 100;

    private double jobWidth = 120;
    private double jobHeight = 70;
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
        clear();
        int roots = 0;
        for (JobFlowNode node : jobFlowGraph.getRoots()) {
            recursiveDraw(node, 20 ,20 + spaceY * roots);
            roots++;
        }
    }

    public int recursiveDraw(JobFlowNode node, double x, double y) {
        List<JobFlowNode> list = jobFlowGraph.getNodes(node);
        System.out.println(list + " size = " + list.size());
        for (int i = 0; i < list.size(); i++) {
            System.out.println(i);
            System.out.println("xFrom = "+ (x+60) + " " + "yFrom = " + (y+35) + " " + "xTo = " +(x+60+spaceX) +" "+ "yTo = " + (y+ 35 + spaceY * i));
            drawLine(x + 60, y + 35, x + 60 + spaceX, y + 35 + spaceY * i);
        }

        drawRect(node, x, y);
        int lines = 0;
        for (JobFlowNode nextNode : list) {
            recursiveDraw(nextNode, x + spaceX, y + spaceY * lines);
            lines++;
        }

        return lines;
//        if (iterator.hasNext()) {
//
//        }
//        Rect rect = drawRect(node);
//        Line line = drawLine(iterator.next(), node);
//        svg.add(rect);
//        svg.add(line);
//        svg.update(rect);
//        x += space + 40;
//
//        while (iterator.hasNext()) {
//
//        }
//        for (JobFlowNode nextNode : jobFlowGraph.getNodes(node)) {
//            drawLine(nextNode, node);
//            recursiveDraw(nextNode);
//            if (jobFlowGraph.getNodes(node).size() > 1) {
//                y += space;
//                x -= space + 40;
//            }
//        }
    }

    public void drawLine(double xFrom, double yFrom, double xTo, double yTo) {
        Line line = new Line("line",
                new AbstractPolyElement.PolyCoordinatePair(xFrom, yFrom),
                new AbstractPolyElement.PolyCoordinatePair(xTo, yTo));
        line.setStroke("beige", 10, Path.LINE_CAP.ROUND, Path.LINE_JOIN.ROUND);
        svg.add(line);
    }

    public void drawRect(JobFlowNode node, double x, double y) {
        Rect rect = new Rect(node.getName(), jobWidth, jobHeight);
        rect.setFillColor(statusToColor.get(node.getStatus()));
        rect.move(x, y);
        svg.add(rect);
    }

    public void clear() {
        List<SvgElement> elements = new ArrayList<>();
        svg.getSvgElements().forEach(elements::add);
        elements.forEach(svg::remove);
    }
}
