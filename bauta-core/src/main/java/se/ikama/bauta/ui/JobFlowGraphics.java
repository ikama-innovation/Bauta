package se.ikama.bauta.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.svg.Svg;
import com.vaadin.flow.component.svg.elements.Rect;
import com.vaadin.flow.component.svg.elements.SvgElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JobFlowGraphics extends VerticalLayout{
    private JobFlowGraph jobFlowGraph;
    private Svg svg = new Svg();
    private HashMap<String, String> statusToColor = new HashMap<>();

    double size = 100;
    double space = size + 20;
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
        Rect rect = new Rect("hej", 300, 300);
        rect.setFillColor("red");
        rect.move(0,0);
        svg.add(rect);
    }

    public void clear() {
        List<SvgElement> elements = new ArrayList<>();
        svg.getSvgElements().forEach(elements::add);
        elements.forEach(svg::remove);
    }
}
