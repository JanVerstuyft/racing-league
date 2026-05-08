package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.User;
import be.jabapage.racingleague.f1telemetry.repository.UserRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.crypto.password.PasswordEncoder;

@Route("register")
@PageTitle("Register | F1 Telemetry")
@AnonymousAllowed
public class RegistrationView extends VerticalLayout {

    public RegistrationView(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        addClassName("registration-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        TextField username = new TextField("Username");
        PasswordField password = new PasswordField("Password");
        PasswordField confirmPassword = new PasswordField("Confirm Password");

        Button registerButton = new Button("Register", e -> {
            if (username.isEmpty() || password.isEmpty()) {
                Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            if (!password.getValue().equals(confirmPassword.getValue())) {
                Notification.show("Passwords do not match", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            if (userRepository.findByUsername(username.getValue()).isPresent()) {
                Notification.show("Username already exists", 5000, Notification.Position.TOP_CENTER);
                return;
            }

            User user = new User();
            user.setUsername(username.getValue());
            user.setPassword(passwordEncoder.encode(password.getValue()));
            userRepository.save(user);

            Notification.show("Registration successful! You can now log in.", 3000, Notification.Position.TOP_CENTER);
            getUI().ifPresent(ui -> ui.navigate(LoginView.class));
        });
        registerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(
            new H1("Create Account"),
            username,
            password,
            confirmPassword,
            registerButton,
            new RouterLink("Back to Login", LoginView.class)
        );
    }
}
