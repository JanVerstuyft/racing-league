package be.jabapage.racingleague.f1telemetry;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Theme(value = "racing-league", variant = Lumo.DARK)
@Push
public class F1TelemetryApplication implements AppShellConfigurator {

	public static void main(String[] args) {
		SpringApplication.run(F1TelemetryApplication.class, args);
	}

}
