package be.jabapage.racingleague.f1telemetry;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

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
            log.info("Starting data migration for Tier functionality...");

            // 1. Drop the old restrictive unique constraint if it exists
            jdbcTemplate.execute("ALTER TABLE session_result DROP CONSTRAINT IF EXISTS uk9xym3lw5woboaw1pciliq9rt9");

            // 2. Ensure every league has at least one tier
            jdbcTemplate.query("SELECT id FROM league WHERE id NOT IN (SELECT DISTINCT league_id FROM tier)", (rs, rowNum) -> {
                long leagueId = rs.getLong("id");
                String token = UUID.randomUUID().toString();
                jdbcTemplate.update("INSERT INTO tier (league_id, name, token) VALUES (?, ?, ?)", leagueId, "Tier 1", token);
                log.info("Created default 'Tier 1' for League ID: {}", leagueId);
                return null;
            });

            // 3. Migrate SessionResults (link to the first tier of their league)
            int sessionsMigrated = jdbcTemplate.update(
                "UPDATE session_result sr SET tier_id = (" +
                "  SELECT t.id FROM tier t WHERE t.league_id = (" +
                "    SELECT e.league_id FROM event e WHERE e.id = sr.event_id" +
                "  ) LIMIT 1" +
                ") WHERE sr.tier_id IS NULL"
            );
            if (sessionsMigrated > 0) log.info("Migrated {} session results to default tiers.", sessionsMigrated);

            // 4. Migrate DriverMappings
            boolean dmHasLeagueId = columnExists("driver_mapping", "league_id");
            if (dmHasLeagueId) {
                jdbcTemplate.update(
                    "UPDATE driver_mapping dm SET tier_id = (" +
                    "  SELECT t.id FROM tier t WHERE t.league_id = dm.league_id LIMIT 1" +
                    ") WHERE dm.tier_id IS NULL AND dm.league_id IS NOT NULL"
                );
            } else {
                // Fallback: Reconstruct mapping-league link from existing race results
                log.info("league_id column missing in driver_mapping, attempting reconstruction from results...");
                jdbcTemplate.update(
                    "UPDATE driver_mapping dm SET tier_id = (" +
                    "  SELECT t.id FROM tier t " +
                    "  JOIN event e ON e.league_id = t.league_id " +
                    "  JOIN session_result sr ON sr.event_id = e.id " +
                    "  JOIN driver_result dr ON dr.session_result_id = sr.id " +
                    "  WHERE dr.telemetry_name = dm.telemetry_name " +
                    "  AND dr.race_number = dm.race_number " +
                    "  AND dr.driver_id = dm.driver_id " +
                    "  LIMIT 1" +
                    ") WHERE dm.tier_id IS NULL"
                );
            }

            // 5. Migrate Driver Standings
            boolean dsHasLeagueId = columnExists("driver_standing", "league_id");
            if (dsHasLeagueId) {
                jdbcTemplate.update(
                    "UPDATE driver_standing ds SET tier_id = (" +
                    "  SELECT t.id FROM tier t WHERE t.league_id = ds.league_id LIMIT 1" +
                    ") WHERE ds.tier_id IS NULL AND ds.league_id IS NOT NULL"
                );
            } else {
                // Fallback: Driver standings are derived data, we can re-link them by name if they are in a tier
                log.info("league_id column missing in driver_standing, re-linking by name from mappings...");
                jdbcTemplate.update(
                    "UPDATE driver_standing ds SET tier_id = (" +
                    "  SELECT dm.tier_id FROM driver_mapping dm " +
                    "  WHERE dm.overridden_name = ds.driver_name " +
                    "  OR dm.telemetry_name = ds.driver_name " +
                    "  LIMIT 1" +
                    ") WHERE ds.tier_id IS NULL"
                );
            }

            log.info("Data migration completed successfully.");
        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
