package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.repository.*;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EventUiPlaywrightTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private TierRepository tierRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionResultRepository sessionResultRepository;
    @Autowired
    private DriverResultRepository driverResultRepository;
    @Autowired
    private ManualPenaltyRepository manualPenaltyRepository;
    @Autowired
    private EventLineupEntryRepository eventLineupEntryRepository;
    @Autowired
    private DriverStandingRepository driverStandingRepository;
    @Autowired
    private TeamStandingRepository teamStandingRepository;
    @Autowired
    private LapTelemetryRepository lapTelemetryRepository;
    @Autowired
    private LapResultRepository lapResultRepository;
    @Autowired
    private SessionPointConfigRepository sessionPointConfigRepository;
    @Autowired
    private ExtraPointRuleRepository extraPointRuleRepository;
    @Autowired
    private DriverMappingRepository driverMappingRepository;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        boolean headless = true;
        // Detect if running inside IntelliJ IDEA
        if (System.getProperty("idea.version") != null || System.getProperty("idea.active") != null) {
            headless = false;
        }
        // Allow overriding via system property
        String override = System.getProperty("playwright.headless");
        if (override != null) {
            headless = Boolean.parseBoolean(override);
        }
        System.out.println("Running Playwright tests in " + (headless ? "headless" : "non-headless") + " mode");
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    public void cleanDatabase() {
        manualPenaltyRepository.deleteAll();
        eventLineupEntryRepository.deleteAll();
        driverStandingRepository.deleteAll();
        teamStandingRepository.deleteAll();
        lapTelemetryRepository.deleteAll();
        lapResultRepository.deleteAll();
        driverResultRepository.deleteAll();
        sessionResultRepository.deleteAll();
        eventRepository.deleteAll();
        sessionPointConfigRepository.deleteAll();
        extraPointRuleRepository.deleteAll();
        driverMappingRepository.deleteAll();
        tierRepository.deleteAll();
        leagueRepository.deleteAll();
    }

    @Test
    public void testEventResultsViewLoadsAndShowsTabs() {
        // 1. Seed database with test event/results
        League league = new League();
        league.setName("Playwright Test League");
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier 1");
        tier.setToken("playwright-token");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        Event event = new Event();
        event.setEventName("Playwright GP");
        event.setTrackId("17");
        event.setTier(tier);
        event.setFinalized(false);
        event = eventRepository.saveAndFlush(event);

        SessionResult session = new SessionResult();
        session.setSessionType(15); // Race
        session.setSessionUID(99991111L);
        session.setEvent(event);
        session = sessionResultRepository.saveAndFlush(session);

        DriverResult dr = new DriverResult();
        dr.setDriverName("Playwright Driver");
        dr.setPosition(1);
        dr.setSessionResult(session);
        dr.setResultStatus(3);
        dr.setPenalties(0);
        dr.setWarnings(0);
        dr = driverResultRepository.saveAndFlush(dr);

        // Navigate to the event results page
        String url = "http://localhost:" + port + "/event/" + event.getId();
        page.navigate(url);

        // Verify page loads by checking the header text
        page.waitForSelector("h2");
        String headerText = page.locator("h2").first().innerText();
        assertTrue(headerText.contains("Playwright GP"));

        // Verify the tabs are displayed
        assertTrue(page.locator("vaadin-tab:has-text('Results')").isVisible());
        assertTrue(page.locator("vaadin-tab:has-text('Stats')").isVisible());
        assertTrue(page.locator("vaadin-tab:has-text('Lineup')").isVisible());
        assertTrue(page.locator("vaadin-tab:has-text('Infographics')").isVisible());

        // Click the Stats tab and verify its content loads
        page.click("vaadin-tab:has-text('Stats')");
        page.waitForSelector("vaadin-tab:has-text('Pure Race Pace')");
        assertTrue(page.locator("vaadin-tab:has-text('Pure Race Pace')").isVisible());

        // Click the Infographics tab and verify its content loads
        page.click("vaadin-tab:has-text('Infographics')");
        page.waitForSelector("h2:has-text('Results Poster'):visible");
        assertTrue(page.locator("h2:has-text('Results Poster'):visible").isVisible());
    }
}
