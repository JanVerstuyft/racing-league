package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.model.CarDamageData;
import be.jabapage.racingleague.f1telemetry.model.CarTelemetryData;
import be.jabapage.racingleague.f1telemetry.service.Broadcaster;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Live Dashboard | F1 Telemetry")
@Route(value = "dashboard", layout = MainLayout.class)
public class DashboardView extends VerticalLayout {

    private final Broadcaster broadcaster;
    
    // Telemetry Components
    private final Span speedSpan = new Span("0 km/h");
    private final Span gearSpan = new Span("N");
    private final Span rpmSpan = new Span("0 RPM");
    private final ProgressBar throttleBar = new ProgressBar();
    private final ProgressBar brakeBar = new ProgressBar();
    private final Span engineTempSpan = new Span("0 °C");

    // Damage Components
    private final Span[] tyreWearSpans = new Span[]{new Span("0%"), new Span("0%"), new Span("0%"), new Span("0%")};
    private final Span frontWingSpan = new Span("L: 0% | R: 0%");
    private final Span rearWingSpan = new Span("0%");
    private final Span engineDamageSpan = new Span("0%");
    private final Span gearboxDamageSpan = new Span("0%");

    public DashboardView(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        
        addClassName("dashboard-view");
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        
        speedSpan.getStyle().set("font-size", "4em").set("font-weight", "bold");
        gearSpan.getStyle().set("font-size", "6em").set("color", "red").set("font-weight", "bold");
        
        HorizontalLayout mainStats = new HorizontalLayout(gearSpan, speedSpan);
        mainStats.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        
        throttleBar.getStyle().set("--lumo-primary-color", "green");
        brakeBar.getStyle().set("--lumo-primary-color", "red");
        
        VerticalLayout telemetryLayout = new VerticalLayout(
                new H3("TELEMETRY"),
                mainStats, rpmSpan,
                new Span("Throttle"), throttleBar,
                new Span("Brake"), brakeBar,
                new Span("Engine Temp"), engineTempSpan
        );
        telemetryLayout.setWidth("400px");
        telemetryLayout.setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        HorizontalLayout tyreLayout = new HorizontalLayout(
                new VerticalLayout(new Span("FL"), tyreWearSpans[2]),
                new VerticalLayout(new Span("FR"), tyreWearSpans[3]),
                new VerticalLayout(new Span("RL"), tyreWearSpans[0]),
                new VerticalLayout(new Span("RR"), tyreWearSpans[1])
        );

        VerticalLayout damageLayout = new VerticalLayout(
                new H3("CAR DAMAGE"),
                new Span("Tyre Wear (FL, FR, RL, RR)"), tyreLayout,
                new Span("Front Wing Damage"), frontWingSpan,
                new Span("Rear Wing Damage"), rearWingSpan,
                new Span("Engine Damage"), engineDamageSpan,
                new Span("Gearbox Damage"), gearboxDamageSpan
        );
        damageLayout.setWidth("400px");
        damageLayout.setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        HorizontalLayout mainLayout = new HorizontalLayout(telemetryLayout, damageLayout);
        add(new Span("LIVE DASHBOARD"), mainLayout);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        broadcaster.registerTelemetry(data -> ui.access(() -> updateTelemetry(data)));
        broadcaster.registerDamage(data -> ui.access(() -> updateDamage(data)));
    }

    private void updateTelemetry(CarTelemetryData data) {
        speedSpan.setText(data.getSpeed() + " km/h");
        gearSpan.setText(data.getGear() == 0 ? "N" : data.getGear() == -1 ? "R" : String.valueOf(data.getGear()));
        rpmSpan.setText(data.getEngineRPM() + " RPM");
        throttleBar.setValue(data.getThrottle());
        brakeBar.setValue(data.getBrake());
        engineTempSpan.setText(data.getEngineTemperature() + " °C");
    }

    private void updateDamage(CarDamageData data) {
        for (int i = 0; i < 4; i++) {
            tyreWearSpans[i].setText(String.format("%.0f%%", data.getTyresWear()[i]));
        }
        frontWingSpan.setText(String.format("L: %d%% | R: %d%%", data.getFrontLeftWingDamage(), data.getFrontRightWingDamage()));
        rearWingSpan.setText(data.getRearWingDamage() + "%");
        engineDamageSpan.setText(data.getEngineDamage() + "%");
        gearboxDamageSpan.setText(data.getGearBoxDamage() + "%");
    }
}
