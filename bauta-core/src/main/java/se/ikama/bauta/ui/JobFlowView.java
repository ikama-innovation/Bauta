package se.ikama.bauta.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.svg.Svg;
import com.vaadin.flow.component.svg.elements.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import se.ikama.bauta.core.BautaManager;
import se.ikama.bauta.core.JobFlowGraph;
import se.ikama.bauta.scheduling.JobTrigger;
import java.util.ArrayList;
import java.util.List;


public class JobFlowView extends Svg {
    Logger log = LoggerFactory.getLogger(this.getClass());

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

    public void draw(JobFlowGraph jobFlowGraph) {
        clear();
        level = 0;
        jobFlowGraph.printGraph();
        for (JobFlowNode node : jobFlowGraph.getRootNodes()) {
            System.out.println("level before draw=" +level + "\ncurrent root = " + node.getName());
            recursiveDraw(node, 20 ,20 + spaceY * level, jobFlowGraph);
        }
    }

    public void recursiveDraw(JobFlowNode node, double x, double y, JobFlowGraph jobFlowGraph) {
//        System.out.println("node in recursive step: " + node.getName());
//        System.out.println("y_pos = " + y);
        List<JobFlowNode> list = jobFlowGraph.getNodesFor(node);
        if (node.getTriggerType() == JobTrigger.TriggerType.CRON) {
            drawLine(x - jobWidth, y + jobHeight/2, x + jobWidth/2, y + jobHeight/2, JobTrigger.TriggerType.CRON);
            Ellipse ellipse = new Ellipse("cronEllipse", radialX, radialY);
            ellipse.setFillColor("#1aa3ff");
            ellipse.move(x - (spaceX/2 + radialX) , y + (jobHeight - radialY * 2)/2);
            add(ellipse);

            Text cron = new Text("cronText", node.getCron());
            cron.setFontFamily("'Roboto', 'Noto', sans-serif");
            cron.setFillColor("white");
            cron.setFontSize("14");
            cron.move(x - (jobWidth + radialX/2), y + jobHeight/2 - Double.parseDouble(cron.getFontSize()));
            add(cron);
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
                System.out.println("hej");
            }
//            System.out.println("level=" + level + "lines=" + lines );
        }
        if (list.isEmpty()) {
            level++;
        }
    }

    public void drawLine(double xFrom, double yFrom, double xTo, double yTo, JobTrigger.TriggerType triggerType) {
        Line line = new Line("line",
                new AbstractPolyElement.PolyCoordinatePair(xFrom, yFrom),
                new AbstractPolyElement.PolyCoordinatePair(xTo, yTo));
        line.setStroke("beige", 10, Path.LINE_CAP.ROUND, Path.LINE_JOIN.ROUND);
        add(line);
    }

    public void drawRect(JobFlowNode node, double x, double y) {
        String name = node.getName();
        Rect rect = new Rect(node.getName(), jobWidth, jobHeight);
        rect.setFillColor("#1aa3ff");
        rect.move(x, y);

        if (name.length() > 21 ) {
            for (int i = 14; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (ch == '-' || ch == '_' || ch == '.') {
                    StringBuilder sb = new StringBuilder(name);
                    sb.insert(i, "\n");
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

        Text text = new Text("text", name);
        text.setFontFamily("'Roboto', 'Noto', sans-serif");
        text.setFillColor("white");
        text.setFontSize("14");
        text.move(x + 8, y + 8);

        add(rect);
        add(text);
    }

    public void clear() {
        List<SvgElement> elements = new ArrayList<>(getSvgElements());
        elements.forEach(this::remove);
    }

    public void update(JobFlowGraph jobFlowGraph) {
        draw(jobFlowGraph);
        jobFlowGraph.printGraph();
    }
}



/**
    public void renderJobs() {
        x = 20;
        y = 20;
        clear();
        try {
            for (List<String> listOfJobs : connectedJobs) {
                for (String job : listOfJobs) {
                    BasicJobInstanceInfo jobInfo = bautaManager.jobDetails(job);
                    createRect(x, y, job, statusToColor.get(jobInfo.getExecutionStatus()));
                    x += 150;
                }
                x = 20;
                y += 110;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
//        try {
//
//            x = 20;
//            y = 20;
//            for (String jobName : allJobs) {
//                BasicJobInstanceInfo jobInfo = bautaManager.jobDetails(jobName);
////                System.out.println("executionstatus for " + jobName + " " + jobInfo.getExecutionStatus());
////                System.out.println("exitStatus for " + jobName + " " + jobInfo.getExitStatus());
//                createRect(x, y, jobName, statusToColor.get(jobInfo.getExecutionStatus()));
//                x += 0;
//                y += 110;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    private void findConnectedComponents() {
        Set<String> visited = new HashSet<>();
        connectedJobs = new HashSet<>();
        for (String job : allJobs) {
            if (!visited.contains(job))  {
                connectedJobs.add(DFS(job, visited));
            }
        }
    }

    private List<String> DFS(String job, Set<String> visited) {
        visited.add(job);
        List<String> connected = new LinkedList<>();
        connected.add(job);
        DFSUtil(job, visited, connected);
        return connected;
    }

    private void DFSUtil(String job, Set<String> visited, List<String> connected) {
        visited.add(job);
        if (!connected.contains(job)) {
            connected.add(job);
        }
        if (undirectedGraph.containsKey(job)) {
            for (String nextJob : undirectedGraph.get(job)) {
                if (!visited.contains(nextJob)) {
                    DFSUtil(nextJob, visited, connected);
                }
            }
        }
    }

    private void updateGraph() {
        // vill att de ska va såhär typ
        // jobFlowViewGraph.update


//        if (bautaManager != null) {
//            if (bautaManager.listJobNames() != null) {
//                bautaManager.listJobNames().forEach(job -> {
//                    allJobs.add(job);
//                    jobTriggerDao.getJobCompletionTriggersFor(job).forEach(triggeredJob -> {
//                        if (directedGraph.containsKey(job)) {
//                            directedGraph.get(job).add(triggeredJob.getJobName());
//                            undirectedGraph.get(job).add(triggeredJob.getJobName());
//                            undirectedGraph.get(triggeredJob.getJobName()).add(job);
//                        } else {
//                            Set<String> set = new HashSet<>();
//                            set.add(triggeredJob.getJobName());
//                            directedGraph.put(job, set);
//                            undirectedGraph.put(job, set);
//                            set = new HashSet<>();
//                            set.add((job));
//                            undirectedGraph.put(triggeredJob.getJobName(), set);
//                        }
//                    });
//                });
//                renderJobs();
//            }
//        }
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        update();
    }

    private void createRelationBetween(String job1, String job2) {

    }

    private void createRect(double x, double y, String name, String color) {
        Rect rect = new Rect(name, jobWidth, jobHeight);
        //kanske lägga färger i en map med status så du lätt kan hämta vilken här
        rect.setFillColor(color);
        rect.move(x, y);
        this.nameToRect.put(name, rect);
        this.svg.add(rect);
    }

    private void printGraph() {
        for (String job : directedGraph.keySet()) {
            System.out.print(job + "  -->  ");
            directedGraph.get(job).forEach(nextJob -> {
                System.out.println(nextJob);
            });
        }
    }

    public void testMove() {
        for (Rect r : nameToRect.values()) {
            svg.update(r);
        }
    }

    public void update() {
        //jobFlowViewGraph.update();
        //jobFlowViewRenderer.render();
        updateGraph();
        findConnectedComponents();
        System.out.println(connectedJobs);
//        printGraph();
    }
}
//        HorizontalLayout controlButtons = new HorizontalLayout();
//        this.add(controlButtons);
//        Span dragDetail = new Span();
//        this.add(dragDetail);
//
//        Svg svg = new Svg();
//        svg.viewbox(0, 0, 1000, 1000);
//        svg.setWidth("100%");
//        svg.setHeight("500px");
//
//        double jobWidth = 150;
//        double jobHeight = 75;
//        double gridSize = 150;
//        String completedColor = "rgb(37, 177, 95)";
//        String lineColor = "rgb(177, 177, 177)";
//
//
//        //Rect
//        Rect rect = new Rect("Job.100.Setup", jobWidth, jobHeight);
//        rect.setFillColor(completedColor);
//        rect.move(gridSize-jobWidth/2, gridSize-jobHeight/2);
//        svg.add(rect);
//
//        Rect rect2 = new Rect("Job.110.Aaa", jobWidth, jobHeight);
//        rect2.setFillColor(completedColor);
//        rect2.move(gridSize*2-jobWidth/2, gridSize-jobHeight/2);
//        svg.add(rect2);
//
//
//        //Line
//        Line line = new Line("line",
//                new AbstractPolyElement.PolyCoordinatePair(gridSize, gridSize),
//                new AbstractPolyElement.PolyCoordinatePair(gridSize + gridSize,
//                        gridSize));
//        line.setStroke(lineColor, 10, Path.LINE_CAP.ROUND, Path.LINE_JOIN.ROUND);
//        svg.add(line);
//
//
//        //Circle
//        double circleRadial = size / 2;
//        Circle circle = new Circle("circle1", circleRadial);
//        circle.setFillColor(lineColor);
//        circle.move(x += space, y);
//        svg.add(circle);
//
//        //Ellipse
//        Ellipse ellipse =
//          new Ellipse("ellipse1", circleRadial, circleRadial * 0.5);
//        ellipse.setFillColor(fillColor);
//        ellipse.move(x += space, y);
//        svg.add(ellipse);
//
//        //Polyline
//        List<AbstractPolyElement.PolyCoordinatePair> points = new ArrayList<>();
//        points.add(new Polyline.PolyCoordinatePair(50, 0));
//        points.add(new Polyline.PolyCoordinatePair(60, 40));
//        points.add(new Polyline.PolyCoordinatePair(100, 50));
//        points.add(new Polyline.PolyCoordinatePair(60, 60));
//        points.add(new Polyline.PolyCoordinatePair(50, 100));
//        points.add(new Polyline.PolyCoordinatePair(40, 60));
//        points.add(new Polyline.PolyCoordinatePair(0, 50));
//        points.add(new Polyline.PolyCoordinatePair(40, 40));
//
//        Polyline polyline = new Polyline("polyline", points);
//        polyline.setFillColor("none");
//        polyline.setStroke(fillColor, 4, Path.LINE_CAP.ROUND,
//          Path.LINE_JOIN.ROUND);
//        polyline.move(x += space, y);
//        svg.add(polyline);
//
//        //Polygon
//        Polygon polygon = new Polygon("polygon", points);
//        polygon.setFillColor(fillColor);
//        polygon.move(x += space, y);
//        svg.add(polygon);
//
//        //Path
//        Path path = new Path("path",
//          "M0 0 H50 A20 20 0 1 0 100 50 v25 C50 125 0 85 0 85 z");
//        path.setFillColor("none");
//        path.setStroke(fillColor, 4, Path.LINE_CAP.ROUND,
//          Path.LINE_JOIN.ROUND);
//        path.move(x = 20, y += space);
//        svg.add(path);
//
//        //Text
//        Text text = new Text("text", "Sample text.");
//        text.setFontFamily("'Roboto', 'Noto', sans-serif");
//        text.setFillColor(fillColor);
//        text.move(x += space, y);
//        svg.add(text);
//
//        //Image
//        Image image = new Image("image",
//          "https://vaadin.com/images/hero-reindeer.svg");
//        image.size(size, size);
//        image.move(x += space, y);
//        image.setDraggable(false);
//        svg.add(image);
//
//
//        this.add(svg);
//
//        //Add control buttons
//        ;
//
//        controlButtons.add(new Button("Toggle draggable", e -> {
//              svg.getSvgElements().forEach(el ->
//                  el.setDraggable(!el.isDraggable()));
//
//              svg.getSvgElements().forEach(el -> {
//
//                  // Due to the nature of how the polygons are written (M0 0),
//                  // if we do an update to them they will move back to 0,0.
//                  // Hence, we have to move them back to their desired
//                  // location when we do an update.
//
//                  if (el == polyline) {
//                      el.move(20 + space, 20 + space);
//                  }
//
//                  if (el == polygon) {
//                      el.move(20 + space * 2, 20 + space);
//                  }
//
//
//                  svg.update(el);
//              });
//          }));
//
//
//          controlButtons.add(new Button("Stroke black", e -> {
//              svg.getSvgElements().forEach(el ->
//                  el.setStroke("#000000", 4, Path.LINE_CAP.ROUND,
//                      Path.LINE_JOIN.ROUND));
//              svg.getSvgElements().forEach(el -> {
//
//                  // Due to the nature of how the polygons are written (M0 0),
//                  // if we do an update to them they will move back to 0,0.
//                  // Hence, we have to move them back to their desired
//                  // location when we do an update.
//                  if (el == polyline) {
//                      el.move(20 + space, 20 + space);
//                  }
//
//                  if (el == polygon) {
//                      el.move(20 + space * 2, 20 + space);
//                  }
//
//                  svg.update(el);
//              });
//          }));
//
//          controlButtons.add(new Button("Display drag events", e -> {
//              svg.addDragStartListener(event -> {
//                  Notification.show("Drag start: " +
//                          event.getElement().getId(),2500,
//                      Notification.Position.MIDDLE);
//                  dragDetail.setText("Drag Start for: " +
//                      event.getElement().getId() +
//                      " X: " + event.getElementX() +
//                      " Y: " + event.getElementY());
//              });
//
//              svg.addDragEndListener(event -> {
//                  Notification.show("Drag End: " +
//                      event.getElement().getId(), 2500,
//                      Notification.Position.MIDDLE);
//                  dragDetail.setText("Drag End for: " +
//                      event.getElement().getId() +
//                      " X: " + event.getElementX() +
//                      " Y: " + event.getElementY());
//              });
//
//                svg.addDragMoveListener(event -> {
//                    dragDetail.setText("Drag Move for: " +
//                        event.getElement().getId() +
//                        " X: " + event.getElementX() +
//                        " Y: " + event.getElementY());
//              });
//
//              e.getSource().setEnabled(false);
//          }));
//
//
//          controlButtons.add(new Button("Remove Line", e -> {
//              svg.remove(line);
//              e.getSource().setEnabled(false);
//          }));

**/