package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
@PageTitle("Privacy Policy | F1 Telemetry")
@Route(value = "privacy")
public class PrivacyView extends VerticalLayout {

    public PrivacyView(SecurityService securityService) {
        setSpacing(true);
        setPadding(true);
        setMaxWidth("900px");
        getElement().getStyle().set("margin", "0 auto");

        if (securityService.getAuthenticatedUser().isPresent()) {
            add(new RouterLink("← Back to Seasons", SeasonListView.class));
        } else {
            HorizontalLayout nav = new HorizontalLayout(
                new RouterLink("← Back to Login", LoginView.class),
                new RouterLink("Login", LoginView.class)
            );
            add(nav);
        }

        try {
            ClassPathResource resource = new ClassPathResource("privacy.md");
            String markdownContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            Markdown markdown = new Markdown(markdownContent);
            markdown.setWidthFull();
            markdown.getStyle().set("line-height", "1.6");
            
            add(markdown);
        } catch (IOException e) {
            add(new H1("Privacy Policy"));
            add(new Span("Error loading privacy policy from resources: " + e.getMessage()));
        }
    }
}
