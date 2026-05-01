package be.jabapage.racingleague.f1telemetry.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@AnonymousAllowed
@PageTitle("Documentation | F1 Telemetry")
@Route(value = "docs")
public class DocumentationView extends VerticalLayout {

    public DocumentationView() {
        setSpacing(true);
        setPadding(true);
        setMaxWidth("900px");
        getElement().getStyle().set("margin", "0 auto");

        add(new RouterLink("← Back to Seasons", SeasonListView.class));

        try {
            ClassPathResource resource = new ClassPathResource("documentation.md");
            String markdownContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            Markdown markdown = new Markdown(markdownContent);
            markdown.setWidthFull();
            
            // Add some basic styling to make it look like the previous version
            markdown.getStyle().set("line-height", "1.6");
            
            add(markdown);
            
        } catch (IOException e) {
            add(new H1("Racing League Documentation"));
            add(new Span("Error loading documentation from resources: " + e.getMessage()));
        }
    }
}
