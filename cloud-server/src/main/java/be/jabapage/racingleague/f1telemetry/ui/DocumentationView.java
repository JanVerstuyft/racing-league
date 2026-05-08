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
@PageTitle("Documentation | F1 Telemetry")
@Route(value = "docs")
public class DocumentationView extends VerticalLayout {

    public DocumentationView(SecurityService securityService) {
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
            ClassPathResource resource = new ClassPathResource("documentation.md");
            String markdownContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            // Fix anchor links to stay on the current page instead of redirecting to root (/)
            // Vaadin's <base href="/"> causes (#anchor) to resolve to /#anchor
            markdownContent = markdownContent.replace("(#", "(docs#");
            
            Markdown markdown = new Markdown(markdownContent);
            markdown.setWidthFull();
            
            // Add some basic styling to make it look like the previous version
            markdown.getStyle().set("line-height", "1.6");
            
            add(markdown);

            // Robust fix for anchor links in Vaadin:
            // 1. Generate IDs for all headings
            // 2. Intercept clicks on links starting with '#'
            getElement().executeJs(
                "const root = this;" +
                "const generateIds = () => {" +
                "  root.querySelectorAll('h1, h2, h3, h4, h5, h6').forEach(h => {" +
                "    if (!h.id) {" +
                "      h.id = h.textContent.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');" +
                "    }" +
                "  });" +
                "};" +
                "generateIds();" +
                "setTimeout(generateIds, 500);" + // Retry after a bit to catch late renders
                "const handleAnchorClick = (e) => {" +
                "  const a = e.target.closest('a');" +
                "  if (a && a.hash && (a.pathname.includes('/docs') || a.pathname === window.location.pathname || a.pathname === '')) {" +
                "    const id = decodeURIComponent(a.hash.substring(1));" +
                "    const el = document.getElementById(id);" +
                "    if (el) {" +
                "      e.preventDefault();" +
                "      el.scrollIntoView({behavior: 'smooth'});" +
                "      history.pushState(null, null, window.location.pathname + '#' + id);" +
                "    }" +
                "  }" +
                "};" +
                "root.addEventListener('click', handleAnchorClick);" +
                "if (window.location.hash) setTimeout(() => {" +
                "  const id = decodeURIComponent(window.location.hash.substring(1));" +
                "  const el = document.getElementById(id);" +
                "  if (el) el.scrollIntoView({behavior: 'smooth'});" +
                "}, 1000);"
            );
            
        } catch (IOException e) {
            add(new H1("Racing League Documentation"));
            add(new Span("Error loading documentation from resources: " + e.getMessage()));
        }
    }
}
