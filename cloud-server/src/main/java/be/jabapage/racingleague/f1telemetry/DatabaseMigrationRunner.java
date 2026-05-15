package be.jabapage.racingleague.f1telemetry;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            log.info("Attempting to drop old unique constraint uk9xym3lw5woboaw1pciliq9rt9 from session_result...");
            jdbcTemplate.execute("ALTER TABLE session_result DROP CONSTRAINT IF EXISTS uk9xym3lw5woboaw1pciliq9rt9");
            log.info("Successfully dropped old constraint (if it existed).");
        } catch (Exception e) {
            log.warn("Could not drop constraint uk9xym3lw5woboaw1pciliq9rt9: {}. This might be expected if it was already dropped.", e.getMessage());
        }
    }
}
