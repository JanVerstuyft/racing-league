package be.jabapage.racingleague.f1telemetry.security;

import be.jabapage.racingleague.f1telemetry.entity.User;
import be.jabapage.racingleague.f1telemetry.repository.UserRepository;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityService {

    private final AuthenticationContext authenticationContext;
    private final UserRepository userRepository;

    public SecurityService(AuthenticationContext authenticationContext, UserRepository userRepository) {
        this.authenticationContext = authenticationContext;
        this.userRepository = userRepository;
    }

    public Optional<UserDetails> getAuthenticatedUser() {
        return authenticationContext.getAuthenticatedUser(UserDetails.class);
    }

    public Optional<User> getAuthenticatedUserEntity() {
        return getAuthenticatedUser().flatMap(u -> userRepository.findByUsername(u.getUsername()));
    }

    public void logout() {
        authenticationContext.logout();
    }
}
