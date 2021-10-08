package se.ikama.bauta.ui;

import com.vaadin.flow.component.html.Div;

import de.xinblue.cytoscape.Cytoscape;
import de.xinblue.cytoscape.model.Edge;
import de.xinblue.cytoscape.model.Node;
import de.xinblue.cytoscape.styles.GeneralGraphStyles;
import de.xinblue.cytoscape.styles.GraphStyles;

public class SchedulingGraphView extends Div {

    private void init() {
	// Create Cytoscape Canvas
	Cytoscape cy = new Cytoscape("schedling");
	cy.setWidth("100%");
	cy.setHeight("600px");
	cy.addClassName("cy");

	// Define an array of styles for different selectors
	GraphStyles gs = new GraphStyles();

	// Define the style for nodes
	GeneralGraphStyles node = new GeneralGraphStyles.Builder().background_color("#c00").label("data(id)").build();
	gs.addStyle("node", node);

	// Define the style for edges
	GeneralGraphStyles edge = new GeneralGraphStyles.Builder().width("2").line_color("#00c").line_style("dotted").build();
	gs.addStyle("edge", edge);

	// Define the style for selected nodes
	GeneralGraphStyles node_selected = new GeneralGraphStyles.Builder().background_color("#0c0").build();
	gs.addStyle("node:selected", node_selected);

	// Define the style for edgeHandling
	GeneralGraphStyles eh_handle = new GeneralGraphStyles.Builder().background_color("#00C").width("8").height("8").text_opacity("0").build();
	gs.addStyle(".eh-handle", eh_handle);

	Node node1 = new Node();
	node1.getPosition().put("x", 100);
	node1.getPosition().put("y", 100);
	node1.getData().put("id", "x1");
	node1.getData().put("myname", "martin");

	Node node2 = new Node();
	node2.getPosition().put("x", 200);
	node2.getPosition().put("y", 200);
	node2.getData().put("id", "x2");

	Edge edge1 = new Edge();
	edge1.getData().put("id", "x1-x2");
	edge1.getData().put("source", "x1");
	edge1.getData().put("target", "x2");
	cy.addNode(node1);
	cy.addNode(node2);
	cy.addEdge(edge1);
	this.add(cy);

    }

    public SchedulingGraphView() {

	init();

    }

}