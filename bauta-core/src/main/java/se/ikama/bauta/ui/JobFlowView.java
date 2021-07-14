package se.ikama.bauta.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.svg.Svg;
import com.vaadin.flow.component.svg.elements.AbstractPolyElement;
import com.vaadin.flow.component.svg.elements.Circle;
import com.vaadin.flow.component.svg.elements.Ellipse;
import com.vaadin.flow.component.svg.elements.Image;
import com.vaadin.flow.component.svg.elements.Line;
import com.vaadin.flow.component.svg.elements.Path;
import com.vaadin.flow.component.svg.elements.Polygon;
import com.vaadin.flow.component.svg.elements.Polyline;
import com.vaadin.flow.component.svg.elements.Rect;
import com.vaadin.flow.component.svg.elements.Text;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.launch.NoSuchJobInstanceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.scheduling.JobTriggerDao;

import javax.annotation.PostConstruct;


@Component
@UIScope
public class JobFlowView extends VerticalLayout {

    @Autowired
    JobTriggerDao jobTriggerDao;
    @Autowired
    BautaManager bautaManager;


    HashMap<String, String> jobConnector;
    Svg svg = new Svg();

    double jobWidth = 150;
    double jobHeight = 75;
    double gridSize = 150;
    String completedColor = "rgb(37, 177, 95)";
    String runningColor = "rgb(42, 127, 239)";
    String failedColor = "rgb(246, 84, 76)";
    String lineColor = "rgb(177, 177, 177)";

    public JobFlowView() {
        svg.viewbox(0, 0, 1000, 1000);
        svg.setWidth("100%");
        svg.setHeight("500px");

        HorizontalLayout controlButtons = new HorizontalLayout();
        this.add(controlButtons);


    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        update();
    }

    @PostConstruct
    public void init() {
        update();
    }

    public void renderJobs(List<String> jobs) {
        System.out.println("success");
    }

    public void update() {
        SortedSet<String> allJobs = bautaManager.listJobNames();
        allJobs.forEach(System.out::println);
    }



        /**
        HorizontalLayout controlButtons = new HorizontalLayout();
        this.add(controlButtons);
        Span dragDetail = new Span();
        this.add(dragDetail);

        Svg svg = new Svg();
        svg.viewbox(0, 0, 1000, 1000);
        svg.setWidth("100%");
        svg.setHeight("500px");

        double jobWidth = 150;
        double jobHeight = 75;
        double gridSize = 150;
        String completedColor = "rgb(37, 177, 95)";
        String lineColor = "rgb(177, 177, 177)";


        //Rect
        Rect rect = new Rect("Job.100.Setup", jobWidth, jobHeight);
        rect.setFillColor(completedColor);
        rect.move(gridSize-jobWidth/2, gridSize-jobHeight/2);
        svg.add(rect);

        Rect rect2 = new Rect("Job.110.Aaa", jobWidth, jobHeight);
        rect2.setFillColor(completedColor);
        rect2.move(gridSize*2-jobWidth/2, gridSize-jobHeight/2);
        svg.add(rect2);


        //Line
        Line line = new Line("line",
          new AbstractPolyElement.PolyCoordinatePair(gridSize, gridSize),
          new AbstractPolyElement.PolyCoordinatePair(gridSize + gridSize,
              gridSize));
        line.setStroke(lineColor, 10, Path.LINE_CAP.ROUND, Path.LINE_JOIN.ROUND);
        svg.add(line);
        /*

        //Circle
        double circleRadial = size / 2;
        Circle circle = new Circle("circle1", circleRadial);
        circle.setFillColor(lineColor);
        circle.move(x += space, y);
        svg.add(circle);

        //Ellipse
        Ellipse ellipse =
          new Ellipse("ellipse1", circleRadial, circleRadial * 0.5);
        ellipse.setFillColor(fillColor);
        ellipse.move(x += space, y);
        svg.add(ellipse);

        //Polyline
        List<AbstractPolyElement.PolyCoordinatePair> points = new ArrayList<>();
        points.add(new Polyline.PolyCoordinatePair(50, 0));
        points.add(new Polyline.PolyCoordinatePair(60, 40));
        points.add(new Polyline.PolyCoordinatePair(100, 50));
        points.add(new Polyline.PolyCoordinatePair(60, 60));
        points.add(new Polyline.PolyCoordinatePair(50, 100));
        points.add(new Polyline.PolyCoordinatePair(40, 60));
        points.add(new Polyline.PolyCoordinatePair(0, 50));
        points.add(new Polyline.PolyCoordinatePair(40, 40));

        Polyline polyline = new Polyline("polyline", points);
        polyline.setFillColor("none");
        polyline.setStroke(fillColor, 4, Path.LINE_CAP.ROUND,
          Path.LINE_JOIN.ROUND);
        polyline.move(x += space, y);
        svg.add(polyline);

        //Polygon
        Polygon polygon = new Polygon("polygon", points);
        polygon.setFillColor(fillColor);
        polygon.move(x += space, y);
        svg.add(polygon);

        //Path
        Path path = new Path("path",
          "M0 0 H50 A20 20 0 1 0 100 50 v25 C50 125 0 85 0 85 z");
        path.setFillColor("none");
        path.setStroke(fillColor, 4, Path.LINE_CAP.ROUND,
          Path.LINE_JOIN.ROUND);
        path.move(x = 20, y += space);
        svg.add(path);

        //Text
        Text text = new Text("text", "Sample text.");
        text.setFontFamily("'Roboto', 'Noto', sans-serif");
        text.setFillColor(fillColor);
        text.move(x += space, y);
        svg.add(text);

        //Image
        Image image = new Image("image",
          "https://vaadin.com/images/hero-reindeer.svg");
        image.size(size, size);
        image.move(x += space, y);
        image.setDraggable(false);
        svg.add(image);


        this.add(svg);

        //Add control buttons
        controlButtons.add(new Button("Toggle Zoom", e -> {
          svg.setZoomEnabled(!svg.isZoomEnabled());
        }));

        controlButtons.add(new Button("Toggle draggable", e -> {
              svg.getSvgElements().forEach(el ->
                  el.setDraggable(!el.isDraggable()));

              svg.getSvgElements().forEach(el -> {

                  // Due to the nature of how the polygons are written (M0 0),
                  // if we do an update to them they will move back to 0,0.
                  // Hence, we have to move them back to their desired
                  // location when we do an update.
                  /*
                  if (el == polyline) {
                      el.move(20 + space, 20 + space);
                  }

                  if (el == polygon) {
                      el.move(20 + space * 2, 20 + space);
                  }


                  svg.update(el);
              });
          }));

          /*
          controlButtons.add(new Button("Stroke black", e -> {
              svg.getSvgElements().forEach(el ->
                  el.setStroke("#000000", 4, Path.LINE_CAP.ROUND,
                      Path.LINE_JOIN.ROUND));
              svg.getSvgElements().forEach(el -> {

                  // Due to the nature of how the polygons are written (M0 0),
                  // if we do an update to them they will move back to 0,0.
                  // Hence, we have to move them back to their desired
                  // location when we do an update.
                  if (el == polyline) {
                      el.move(20 + space, 20 + space);
                  }

                  if (el == polygon) {
                      el.move(20 + space * 2, 20 + space);
                  }

                  svg.update(el);
              });
          }));

          controlButtons.add(new Button("Display drag events", e -> {
              svg.addDragStartListener(event -> {
                  Notification.show("Drag start: " +
                          event.getElement().getId(),2500,
                      Notification.Position.MIDDLE);
                  dragDetail.setText("Drag Start for: " +
                      event.getElement().getId() +
                      " X: " + event.getElementX() +
                      " Y: " + event.getElementY());
              });

              svg.addDragEndListener(event -> {
                  Notification.show("Drag End: " +
                      event.getElement().getId(), 2500,
                      Notification.Position.MIDDLE);
                  dragDetail.setText("Drag End for: " +
                      event.getElement().getId() +
                      " X: " + event.getElementX() +
                      " Y: " + event.getElementY());
              });

                svg.addDragMoveListener(event -> {
                    dragDetail.setText("Drag Move for: " +
                        event.getElement().getId() +
                        " X: " + event.getElementX() +
                        " Y: " + event.getElementY());
              });

              e.getSource().setEnabled(false);
          }));

          /*
          controlButtons.add(new Button("Remove Line", e -> {
              svg.remove(line);
              e.getSource().setEnabled(false);
          }));

        */
}
