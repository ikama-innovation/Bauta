package se.ikama.bauta.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("scheduling")
public class Scheduling extends VerticalLayout {

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        this.add(new Label("hello"));
    }

}
