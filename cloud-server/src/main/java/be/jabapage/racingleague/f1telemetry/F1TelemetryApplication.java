package be.jabapage.racingleague.f1telemetry;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Theme(value = "racing-league", variant = Lumo.DARK)
@Push(value = com.vaadin.flow.shared.communication.PushMode.AUTOMATIC, transport = com.vaadin.flow.shared.ui.Transport.WEBSOCKET_XHR)
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
public class F1TelemetryApplication implements AppShellConfigurator {

	public static void main(String[] args) {
		SpringApplication.run(F1TelemetryApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner initData(be.jabapage.racingleague.f1telemetry.repository.UserRepository userRepository,
															  org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
		return args -> {
			if (userRepository.count() == 0) {
				be.jabapage.racingleague.f1telemetry.entity.User user = new be.jabapage.racingleague.f1telemetry.entity.User();
				user.setUsername("user");
				user.setPassword(passwordEncoder.encode("password"));
				userRepository.save(user);
			}
		};
	}

}
