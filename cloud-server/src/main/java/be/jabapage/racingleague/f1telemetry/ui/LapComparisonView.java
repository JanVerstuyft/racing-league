package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.LapTelemetry;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.repository.DriverResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapTelemetryRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@AnonymousAllowed
@PageTitle("Lap Telemetry Comparison | F1 Telemetry")
@Route(value = "lap-comparison")
public class LapComparisonView extends VerticalLayout implements HasUrlParameter<Long> {

    private final SessionResultRepository sessionResultRepository;
    private final LapResultRepository lapResultRepository;
    private final LapTelemetryRepository lapTelemetryRepository;
    private final DriverResultRepository driverResultRepository;

    private SessionResult currentSession;
    private final H1 title = new H1("Lap Telemetry Comparison");
    private final H3 sessionSub = new H3("Session Info");
    private final RouterLink backLink = new RouterLink("← Back to Event Results", EventResultsView.class, 0L);

    private final ComboBox<DriverResult> driverACombo = new ComboBox<>("Driver A");
    private final ComboBox<DriverResult> driverBCombo = new ComboBox<>("Driver B");
    
    private final Div telemetryReplayContainer = new Div();

    public LapComparisonView(SessionResultRepository sessionResultRepository,
                             LapResultRepository lapResultRepository,
                             LapTelemetryRepository lapTelemetryRepository,
                             DriverResultRepository driverResultRepository) {
        this.sessionResultRepository = sessionResultRepository;
        this.lapResultRepository = lapResultRepository;
        this.lapTelemetryRepository = lapTelemetryRepository;
        this.driverResultRepository = driverResultRepository;

        setSpacing(true);
        setPadding(true);
        setSizeFull();
        getStyle().set("background-color", "#0e0e10");
        getStyle().set("color", "#ececf1");

        HorizontalLayout header = new HorizontalLayout(backLink, title);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);
        
        HorizontalLayout selectorRow = new HorizontalLayout(driverACombo, driverBCombo);
        selectorRow.setSpacing(true);
        selectorRow.setPadding(false);

        driverACombo.setWidth("250px");
        driverBCombo.setWidth("250px");

        driverACombo.addValueChangeListener(e -> updateComparison());
        driverBCombo.addValueChangeListener(e -> updateComparison());

        add(header, sessionSub, selectorRow, telemetryReplayContainer);
        expand(telemetryReplayContainer);

        // Inject custom CSS styling for telemetry dashboard
        telemetryReplayContainer.setSizeFull();
        telemetryReplayContainer.getStyle().set("border", "1px solid #27272a");
        telemetryReplayContainer.getStyle().set("background-color", "#121214");
        telemetryReplayContainer.getStyle().set("border-radius", "8px");
        telemetryReplayContainer.getStyle().set("padding", "15px");
        telemetryReplayContainer.getStyle().set("overflow", "hidden");
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        if (parameter == null) {
            event.forwardTo(SeasonListView.class);
            return;
        }

        Optional<SessionResult> sessionOpt = sessionResultRepository.findById(parameter);
        if (sessionOpt.isEmpty()) {
            event.forwardTo(SeasonListView.class);
            return;
        }

        this.currentSession = sessionOpt.get();
        backLink.setRoute(EventResultsView.class, currentSession.getEvent().getId());

        String trackName = TelemetryProcessingService.TRACK_NAMES.getOrDefault(
                currentSession.getTrackId() != null ? Integer.parseInt(currentSession.getTrackId()) : -1, "Unknown Track");
        String sessionName = TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(
                currentSession.getSessionType(), "Session " + currentSession.getSessionType());
        
        sessionSub.setText(trackName + " — " + sessionName + " (" + currentSession.getCarType() + ")");

        // Fetch all driver results in this session that have at least one lap with telemetry
        List<DriverResult> driversWithTelemetry = currentSession.getDriverResults().stream()
                .filter(dr -> lapResultRepository.findByDriverResult(dr).stream()
                        .anyMatch(lr -> lapTelemetryRepository.findByLapResultId(lr.getId()).isPresent()))
                .sorted(Comparator.comparing(DriverResult::getDriverName))
                .collect(Collectors.toList());

        driverACombo.setItems(driversWithTelemetry);
        driverACombo.setItemLabelGenerator(dr -> dr.getDriverName() + " (" + formatTime(dr.getBestLapTime()) + ")");

        driverBCombo.setItems(driversWithTelemetry);
        driverBCombo.setItemLabelGenerator(dr -> dr.getDriverName() + " (" + formatTime(dr.getBestLapTime()) + ")");

        // Pre-select fastest two drivers if available
        if (driversWithTelemetry.size() >= 1) {
            driverACombo.setValue(driversWithTelemetry.get(0));
        }
        if (driversWithTelemetry.size() >= 2) {
            driverBCombo.setValue(driversWithTelemetry.get(1));
        }

        updateComparison();
    }

    private String formatTime(Float timeInSec) {
        if (timeInSec == null || timeInSec == 0.0f) return "-";
        int minutes = (int) (timeInSec / 60);
        float seconds = timeInSec % 60;
        return String.format("%d:%06.3f", minutes, seconds);
    }

    private void updateComparison() {
        telemetryReplayContainer.removeAll();

        DriverResult drA = driverACombo.getValue();
        DriverResult drB = driverBCombo.getValue();

        if (drA == null) {
            telemetryReplayContainer.add(new Span("Select Driver A to begin telemetry comparison."));
            return;
        }

        // Fetch fastest lap for Driver A
        LapResult lapA = getFastestLapWithTelemetry(drA);
        LapTelemetry telemA = lapA != null ? lapTelemetryRepository.findByLapResultId(lapA.getId()).orElse(null) : null;

        // Fetch fastest lap for Driver B
        LapResult lapB = drB != null ? getFastestLapWithTelemetry(drB) : null;
        LapTelemetry telemB = lapB != null ? lapTelemetryRepository.findByLapResultId(lapB.getId()).orElse(null) : null;

        if (telemA == null) {
            telemetryReplayContainer.add(new Span("No telemetry data found for " + drA.getDriverName()));
            return;
        }

        // Setup HTML/CSS/JS container structure for comparison
        String telemetryDataA = telemA.getTelemetryData();
        String telemetryDataB = telemB != null ? telemB.getTelemetryData() : null;

        String nameA = drA.getDriverName();
        String nameB = drB != null ? drB.getDriverName() : "None";

        String timeA = formatTime(drA.getBestLapTime());
        String timeB = drB != null ? formatTime(drB.getBestLapTime()) : "-";

        // Generate dynamic DOM inside container
        telemetryReplayContainer.getElement().setProperty("innerHTML",
                "<div class='comparison-wrapper'>" +
                "  <div class='left-panel'>" +
                "    <div class='canvas-card'>" +
                "      <canvas id='trackCanvas' width='550' height='380'></canvas>" +
                "      <div class='track-legend'>" +
                "        <div class='legend-item'><span class='dot cyan-dot'></span> <span class='legend-name'>" + nameA + " (" + timeA + ")</span></div>" +
                "        " + (drB != null ? "<div class='legend-item'><span class='dot magenta-dot'></span> <span class='legend-name'>" + nameB + " (" + timeB + ")</span></div>" : "") +
                "      </div>" +
                "    </div>" +
                "    <div class='controls-card'>" +
                "      <button id='playPauseBtn' class='control-btn'>▶ Play</button>" +
                "      <input type='range' id='playbackTimeline' min='0' max='100' value='0' class='timeline-slider'>" +
                "      <span id='timeLabel' class='time-display'>0.00 / 0.00s</span>" +
                "      <select id='speedSelect' class='selector-dropdown'>" +
                "        <option value='0.25'>0.25x</option>" +
                "        <option value='0.5'>0.5x</option>" +
                "        <option value='1.0' selected>1.0x</option>" +
                "        <option value='2.0'>2.0x</option>" +
                "      </select>" +
                "      <select id='alignSelect' class='selector-dropdown'>" +
                "        <option value='distance' selected>Distance Align</option>" +
                "        <option value='time'>Time Align</option>" +
                "      </select>" +
                "    </div>" +
                "    <div class='gauges-card'>" +
                "      <div class='gauge-panel panel-a'>" +
                "        <h4 class='cyan-text'>" + nameA + "</h4>" +
                "        <div class='gauge-row'>" +
                "          <div class='dial'><span id='spdValA'>0</span> <small>km/h</small></div>" +
                "          <div class='gear'><small>Gear</small> <span id='gearValA'>N</span></div>" +
                "        </div>" +
                "        <div class='pedal-container'>" +
                "          <div class='pedal-label'>THR</div>" +
                "          <div class='pedal-track'><div id='thrBarA' class='pedal-bar thr-bar' style='height: 0%'></div></div>" +
                "          <div class='pedal-track'><div id='brkBarA' class='pedal-bar brk-bar' style='height: 0%'></div></div>" +
                "          <div class='pedal-label'>BRK</div>" +
                "        </div>" +
                "        <div class='status-row'>" +
                "          <div class='ers-label'>ERS Mode: <span id='ersValA'>0</span></div>" +
                "          <span id='drsBadgeA' class='drs-badge'>DRS</span>" +
                "        </div>" +
                "      </div>" +
                "      " + (drB != null ? 
                "      <div class='gauge-panel panel-b'>" +
                "        <h4 class='magenta-text'>" + nameB + "</h4>" +
                "        <div class='gauge-row'>" +
                "          <div class='dial'><span id='spdValB'>0</span> <small>km/h</small></div>" +
                "          <div class='gear'><small>Gear</small> <span id='gearValB'>N</span></div>" +
                "        </div>" +
                "        <div class='pedal-container'>" +
                "          <div class='pedal-label'>THR</div>" +
                "          <div class='pedal-track'><div id='thrBarB' class='pedal-bar thr-bar' style='height: 0%'></div></div>" +
                "          <div class='pedal-track'><div id='brkBarB' class='pedal-bar brk-bar' style='height: 0%'></div></div>" +
                "          <div class='pedal-label'>BRK</div>" +
                "        </div>" +
                "        <div class='status-row'>" +
                "          <div class='ers-label'>ERS Mode: <span id='ersValB'>0</span></div>" +
                "          <span id='drsBadgeB' class='drs-badge'>DRS</span>" +
                "        </div>" +
                "      </div>" : "") +
                "    </div>" +
                "  </div>" +
                "  <div class='right-panel'>" +
                "    <div class='charts-card'>" +
                "      <canvas id='chartsCanvas' width='550' height='1000'></canvas>" +
                "    </div>" +
                "  </div>" +
                "</div>"
        );

        // Inject Stylesheet and Replay Logic directly into the view page
        UI.getCurrent().getPage().executeJs(
                "const style = document.createElement('style');" +
                "style.innerHTML = `" +
                "  .comparison-wrapper { display: flex; flex-direction: row; gap: 15px; width: 100%; height: 100%; box-sizing: border-box; }" +
                "  .left-panel { display: flex; flex-direction: column; gap: 12px; flex: 1.1; min-width: 400px; }" +
                "  .right-panel { display: flex; flex-direction: column; flex: 1.2; min-width: 400px; overflow-y: auto; }" +
                "  .right-panel::-webkit-scrollbar { width: 6px; }" +
                "  .right-panel::-webkit-scrollbar-track { background: #161619; }" +
                "  .right-panel::-webkit-scrollbar-thumb { background: #3f3f46; border-radius: 3px; }" +
                "  .right-panel::-webkit-scrollbar-thumb:hover { background: #52525b; }" +
                "  .canvas-card, .controls-card, .gauges-card, .charts-card {" +
                "    background-color: #161619; border: 1px solid #27272a; border-radius: 6px; padding: 12px; display: flex; flex-direction: column;" +
                "  }" +
                "  .canvas-card { position: relative; align-items: center; justify-content: center; background-color: #0d0d0f; }" +
                "  .canvas-card canvas { max-width: 100%; height: auto; }" +
                "  .track-legend { display: flex; gap: 20px; margin-top: 8px; font-size: 0.9em; }" +
                "  .legend-item { display: flex; align-items: center; gap: 6px; }" +
                "  .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }" +
                "  .cyan-dot { background-color: #00f3ff; box-shadow: 0 0 6px #00f3ff; }" +
                "  .magenta-dot { background-color: #ff00a0; box-shadow: 0 0 6px #ff00a0; }" +
                "  .legend-name { color: #ececf1; font-weight: 500; }" +
                "  .controls-card { flex-direction: row; align-items: center; justify-content: space-between; gap: 10px; }" +
                "  .control-btn { background-color: #27272a; border: 1px solid #3f3f46; color: #ececf1; padding: 8px 16px; border-radius: 4px; font-weight: 600; cursor: pointer; transition: 0.2s; }" +
                "  .control-btn:hover { background-color: #3f3f46; }" +
                "  .timeline-slider { flex: 1; accent-color: #00f3ff; height: 6px; cursor: pointer; }" +
                "  .time-display { font-family: monospace; font-size: 0.95em; color: #a1a1aa; width: 110px; text-align: center; }" +
                "  .selector-dropdown { background-color: #27272a; border: 1px solid #3f3f46; color: #ececf1; padding: 6px; border-radius: 4px; outline: none; cursor: pointer; }" +
                "  .gauges-card { flex-direction: row; gap: 12px; justify-content: space-around; }" +
                "  .gauge-panel { flex: 1; border: 1px solid #27272a; border-radius: 6px; padding: 10px; background-color: #0d0d0f; display: flex; flex-direction: column; gap: 8px; }" +
                "  .panel-a { border-left: 3px solid #00f3ff; }" +
                "  .panel-b { border-left: 3px solid #ff00a0; }" +
                "  .cyan-text { color: #00f3ff; font-weight: 700; margin: 0; }" +
                "  .magenta-text { color: #ff00a0; font-weight: 700; margin: 0; }" +
                "  .gauge-row { display: flex; align-items: flex-end; justify-content: space-between; margin-top: 4px; }" +
                "  .dial { font-size: 1.8em; font-weight: 800; font-family: 'Outfit', sans-serif; color: #f4f4f5; }" +
                "  .dial small { font-size: 0.4em; color: #a1a1aa; font-weight: 400; }" +
                "  .gear { font-size: 1.3em; font-weight: 700; text-align: right; color: #ececf1; display: flex; flex-direction: column; align-items: flex-end; }" +
                "  .gear small { font-size: 0.5em; color: #71717a; text-transform: uppercase; font-weight: 500; }" +
                "  .pedal-container { display: flex; align-items: center; justify-content: center; gap: 12px; background-color: #161619; border-radius: 4px; padding: 6px; }" +
                "  .pedal-label { font-size: 0.75em; font-weight: bold; color: #71717a; }" +
                "  .pedal-track { width: 15px; height: 60px; background-color: #27272a; border-radius: 2px; position: relative; overflow: hidden; }" +
                "  .pedal-bar { width: 100%; position: absolute; bottom: 0; left: 0; transition: height 0.05s ease-out; }" +
                "  .thr-bar { background: linear-gradient(to top, #10b981, #34d399); box-shadow: 0 0 6px #10b981; }" +
                "  .brk-bar { background: linear-gradient(to top, #ef4444, #f87171); box-shadow: 0 0 6px #ef4444; }" +
                "  .status-row { display: flex; align-items: center; justify-content: space-between; font-size: 0.85em; color: #a1a1aa; }" +
                "  .ers-label { font-weight: 500; }" +
                "  .drs-badge { background-color: #27272a; border: 1px solid #3f3f46; border-radius: 3px; padding: 2px 6px; font-size: 0.8em; font-weight: bold; color: #71717a; }" +
                "  .drs-badge.active { background-color: #eab308; border-color: #facc15; color: #09090b; box-shadow: 0 0 8px #eab308; }" +
                "  .charts-card { background-color: #0d0d0f; flex: 1; align-items: center; justify-content: center; }" +
                "  .charts-card canvas { width: 100%; height: auto; }" +
                "`;" +
                "document.head.appendChild(style);"
        );

        // Inject Replay Script definition
        UI.getCurrent().getPage().executeJs(getReplayScriptJs());

        // Inject Comparison logic and canvas update loop
        UI.getCurrent().getPage().executeJs(
                "const dataA = $0;" +
                "const dataB = $1;" +
                "setupTelemetryLoop(dataA, dataB);",
                telemetryDataA, telemetryDataB
        );
    }

    private String getReplayScriptJs() {
        return """
window.setupTelemetryLoop = function(dataA, dataB) {
    if (!dataA) return;
    
    // Elements
    const trackCanvas = document.getElementById('trackCanvas');
    const chartsCanvas = document.getElementById('chartsCanvas');
    const playPauseBtn = document.getElementById('playPauseBtn');
    const timeline = document.getElementById('playbackTimeline');
    const timeLabel = document.getElementById('timeLabel');
    const speedSelect = document.getElementById('speedSelect');
    const alignSelect = document.getElementById('alignSelect');
    
    // Driver A gauges
    const spdValA = document.getElementById('spdValA');
    const gearValA = document.getElementById('gearValA');
    const thrBarA = document.getElementById('thrBarA');
    const brkBarA = document.getElementById('brkBarA');
    const drsBadgeA = document.getElementById('drsBadgeA');
    const ersValA = document.getElementById('ersValA');
    
    // Driver B gauges
    const spdValB = document.getElementById('spdValB');
    const gearValB = document.getElementById('gearValB');
    const thrBarB = document.getElementById('thrBarB');
    const brkBarB = document.getElementById('brkBarB');
    const drsBadgeB = document.getElementById('drsBadgeB');
    const ersValB = document.getElementById('ersValB');
    
    // Playback state
    let isPlaying = false;
    let currentPos = 0; // distance (meters) or time (ms)
    let playbackSpeed = 1.0;
    let alignMode = 'distance'; // 'distance' or 'time'
    let lastTime = null;
    
    // Parse data columns
    function parseTelemetry(data) {
        if (!data || data === 'null') return null;
        try {
            const parsed = typeof data === 'string' ? JSON.parse(data) : data;
            if (parsed && typeof parsed === 'object' && Array.isArray(parsed.d) && parsed.d.length > 0) {
                return parsed;
            }
        } catch (e) {
            console.error("Failed to parse telemetry data:", e);
        }
        return null;
    }
    
    const lapA = parseTelemetry(dataA);
    const lapB = parseTelemetry(dataB);
    
    if (!lapA) return;
    
    // Max limits
    const maxDistA = lapA.d[lapA.d.length - 1];
    const maxDistB = lapB ? lapB.d[lapB.d.length - 1] : 0;
    const maxDist = Math.max(maxDistA, maxDistB);
    
    const maxTimeA = lapA.t[lapA.t.length - 1];
    const maxTimeB = lapB ? lapB.t[lapB.t.length - 1] : 0;
    const maxTime = Math.max(maxTimeA, maxTimeB);
    
    // Track bounds for scaling
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
    function updateBounds(lap) {
        if (!lap) return;
        for (let i = 0; i < lap.x.length; i++) {
            const x = lap.x[i];
            const z = lap.z[i];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }
    }
    updateBounds(lapA);
    updateBounds(lapB);
    
    const trackWidth = maxX - minX;
    const trackHeight = maxZ - minZ;
    
    // Interpolation helper
    function getSampleAt(lap, value, mode) {
        const arr = mode === 'distance' ? lap.d : lap.t;
        if (value <= arr[0]) return getSampleIndex(lap, 0);
        if (value >= arr[arr.length - 1]) return getSampleIndex(lap, arr.length - 1);
        
        // binary search
        let low = 0, high = arr.length - 1;
        while (low <= high) {
            let mid = Math.floor((low + high) / 2);
            if (arr[mid] === value) return getSampleIndex(lap, mid);
            if (arr[mid] < value) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        
        const idx0 = Math.max(0, high);
        const idx1 = Math.min(arr.length - 1, low);
        if (idx0 === idx1) return getSampleIndex(lap, idx0);
        
        const val0 = arr[idx0];
        const val1 = arr[idx1];
        const ratio = (value - val0) / (val1 - val0);
        
        return {
            t: lerp(lap.t[idx0], lap.t[idx1], ratio),
            d: lerp(lap.d[idx0], lap.d[idx1], ratio),
            x: lerp(lap.x[idx0], lap.x[idx1], ratio),
            z: lerp(lap.z[idx0], lap.z[idx1], ratio),
            spd: Math.round(lerp(lap.spd[idx0], lap.spd[idx1], ratio)),
            thr: lerp(lap.thr[idx0], lap.thr[idx1], ratio),
            brk: lerp(lap.brk[idx0], lap.brk[idx1], ratio),
            gear: lap.gear[idx0],
            ers: lap.ers[idx0],
            drs: lap.drs[idx0]
        };
    }
    
    function getSampleIndex(lap, idx) {
        return {
            t: lap.t[idx],
            d: lap.d[idx],
            x: lap.x[idx],
            z: lap.z[idx],
            spd: lap.spd[idx],
            thr: lap.thr[idx],
            brk: lap.brk[idx],
            gear: lap.gear[idx],
            ers: lap.ers[idx],
            drs: lap.drs[idx]
        };
    }
    
    function lerp(a, b, r) {
        return a + (b - a) * r;
    }
    
    // Track canvas rendering
    function drawTrack() {
        if (!trackCanvas) return;
        const ctx = trackCanvas.getContext('2d');
        const W = trackCanvas.width;
        const H = trackCanvas.height;
        ctx.clearRect(0, 0, W, H);
        
        const pad = 20;
        const scale = Math.min((W - 2 * pad) / trackWidth, (H - 2 * pad) / trackHeight);
        const offsetX = (W - trackWidth * scale) / 2;
        const offsetY = (H - trackHeight * scale) / 2;
        
        function toCanvas(x, z) {
            return {
                cx: offsetX + (x - minX) * scale,
                cy: offsetY + (z - minZ) * scale
            };
        }
        
        function drawLine(lap, color, width) {
            if (!lap) return;
            ctx.beginPath();
            ctx.strokeStyle = color;
            ctx.lineWidth = width;
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            for (let i = 0; i < lap.x.length; i++) {
                const { cx, cy } = toCanvas(lap.x[i], lap.z[i]);
                if (i === 0) ctx.moveTo(cx, cy);
                else ctx.lineTo(cx, cy);
            }
            ctx.stroke();
        }
        
        drawLine(lapA, 'rgba(0, 243, 255, 0.45)', 4);
        drawLine(lapB, 'rgba(255, 0, 160, 0.45)', 4);
        
        const sampleA = getSampleAt(lapA, currentPos, alignMode);
        const posA = toCanvas(sampleA.x, sampleA.z);
        ctx.beginPath();
        ctx.fillStyle = '#00f3ff';
        ctx.arc(posA.cx, posA.cy, 6, 0, 2 * Math.PI);
        ctx.shadowBlur = 10;
        ctx.shadowColor = '#00f3ff';
        ctx.fill();
        ctx.shadowBlur = 0;
        
        if (lapB) {
            const sampleB = getSampleAt(lapB, currentPos, alignMode);
            const posB = toCanvas(sampleB.x, sampleB.z);
            ctx.beginPath();
            ctx.fillStyle = '#ff00a0';
            ctx.arc(posB.cx, posB.cy, 6, 0, 2 * Math.PI);
            ctx.shadowBlur = 10;
            ctx.shadowColor = '#ff00a0';
            ctx.fill();
            ctx.shadowBlur = 0;
        }
    }
    
    function drawCharts() {
        if (!chartsCanvas) return;
        const ctx = chartsCanvas.getContext('2d');
        const W = chartsCanvas.width;
        const H = chartsCanvas.height;
        ctx.clearRect(0, 0, W, H);
        
        const padLeft = 45;
        const padRight = 15;
        const chartWidth = W - padLeft - padRight;
        
        const numCharts = lapB ? 5 : 4;
        const chartHeight = Math.floor((H - 40 - (numCharts * 15)) / numCharts);
        
        const currentLimit = alignMode === 'distance' ? maxDist : maxTime;
        let yOffset = 15;
        
        // Chart 1: Speed (0 to 360 km/h)
        drawChartGrid(ctx, padLeft, yOffset, chartWidth, chartHeight, 'Speed (km/h)', [0, 100, 200, 300, 360]);
        drawLapLine(ctx, lapA, 'spd', 0, 360, '#00f3ff', padLeft, yOffset, chartWidth, chartHeight);
        if (lapB) drawLapLine(ctx, lapB, 'spd', 0, 360, '#ff00a0', padLeft, yOffset, chartWidth, chartHeight);
        yOffset += chartHeight + 18;
        
        // Chart 2: Throttle %
        drawChartGrid(ctx, padLeft, yOffset, chartWidth, chartHeight, 'Throttle %', [0, 0.5, 1.0]);
        drawLapLine(ctx, lapA, 'thr', 0, 1.0, '#00f3ff', padLeft, yOffset, chartWidth, chartHeight, false);
        if (lapB) drawLapLine(ctx, lapB, 'thr', 0, 1.0, '#ff00a0', padLeft, yOffset, chartWidth, chartHeight, false);
        yOffset += chartHeight + 18;
        
        // Chart 3: Brake %
        drawChartGrid(ctx, padLeft, yOffset, chartWidth, chartHeight, 'Brake %', [0, 0.5, 1.0]);
        drawLapLine(ctx, lapA, 'brk', 0, 1.0, '#00f3ff', padLeft, yOffset, chartWidth, chartHeight, false);
        if (lapB) drawLapLine(ctx, lapB, 'brk', 0, 1.0, '#ff00a0', padLeft, yOffset, chartWidth, chartHeight, false);
        yOffset += chartHeight + 18;
        
        // Chart 3: Gear (0 to 8)
        drawChartGrid(ctx, padLeft, yOffset, chartWidth, chartHeight, 'Gear', [1, 3, 5, 7, 8]);
        drawLapLine(ctx, lapA, 'gear', 0, 8, '#00f3ff', padLeft, yOffset, chartWidth, chartHeight, false, true);
        if (lapB) drawLapLine(ctx, lapB, 'gear', 0, 8, '#ff00a0', padLeft, yOffset, chartWidth, chartHeight, false, true);
        yOffset += chartHeight + 18;
        
        // Chart 4: Delta Time (A vs B)
        if (lapB) {
            drawChartGrid(ctx, padLeft, yOffset, chartWidth, chartHeight, 'Delta Time (A vs B) seconds', [-1.0, -0.5, 0.0, 0.5, 1.0]);
            drawDeltaLine(ctx, '#eab308', padLeft, yOffset, chartWidth, chartHeight);
            yOffset += chartHeight + 18;
        }
        
        // Draw the playhead line
        const playheadX = padLeft + (currentPos / currentLimit) * chartWidth;
        if (playheadX >= padLeft && playheadX <= W - padRight) {
            ctx.beginPath();
            ctx.strokeStyle = '#f4f4f5';
            ctx.lineWidth = 1.5;
            ctx.setLineDash([4, 4]);
            ctx.moveTo(playheadX, 5);
            ctx.lineTo(playheadX, H - 30);
            ctx.stroke();
            ctx.setLineDash([]);
        }
        
        ctx.fillStyle = '#a1a1aa';
        ctx.font = '10px monospace';
        ctx.textAlign = 'right';
        ctx.fillText(alignMode === 'distance' ? 'Distance (meters)' : 'Time (seconds)', W - padRight, H - 10);
    }
    
    function drawChartGrid(ctx, x, y, w, h, title, yTicks) {
        ctx.fillStyle = '#ececf1';
        ctx.font = 'bold 11px sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText(title, x, y - 4);
        
        ctx.strokeStyle = '#27272a';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.rect(x, y, w, h);
        ctx.stroke();
        
        ctx.fillStyle = '#71717a';
        ctx.font = '9px monospace';
        ctx.textAlign = 'right';
        for (let t of yTicks) {
            const tickY = y + h - ((t - yTicks[0]) / (yTicks[yTicks.length - 1] - yTicks[0])) * h;
            ctx.beginPath();
            ctx.strokeStyle = t === 0 ? '#52525b' : '#1f1f23';
            ctx.moveTo(x, tickY);
            ctx.lineTo(x + w, tickY);
            ctx.stroke();
            ctx.fillText(t, x - 5, tickY + 3);
        }
    }
    
    function drawLapLine(ctx, lap, field, minVal, maxVal, color, x, y, w, h, isDotted = false, isStep = false) {
        const arr = alignMode === 'distance' ? lap.d : lap.t;
        const valLimit = alignMode === 'distance' ? maxDist : maxTime;
        
        ctx.beginPath();
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.8;
        if (isDotted) ctx.setLineDash([2, 3]);
        
        for (let i = 0; i < lap.x.length; i++) {
            const val = arr[i];
            const fieldVal = lap[field][i];
            
            const px = x + (val / valLimit) * w;
            const py = y + h - ((fieldVal - minVal) / (maxVal - minVal)) * h;
            
            if (i === 0) ctx.moveTo(px, py);
            else {
                if (isStep) {
                    const prevPx = x + (arr[i-1] / valLimit) * w;
                    ctx.lineTo(px, y + h - ((lap[field][i-1] - minVal) / (maxVal - minVal)) * h);
                }
                ctx.lineTo(px, py);
            }
        }
        ctx.stroke();
        ctx.setLineDash([]);
    }
    
    function drawDeltaLine(ctx, color, x, y, w, h) {
        const steps = 150;
        ctx.beginPath();
        ctx.strokeStyle = color;
        ctx.lineWidth = 2.0;
        
        const range = 1.0;
        
        for (let i = 0; i <= steps; i++) {
            const d = (i / steps) * maxDist;
            if (d > maxDistA || d > maxDistB) break;
            
            const sampleA = getSampleAt(lapA, d, 'distance');
            const sampleB = getSampleAt(lapB, d, 'distance');
            
            const delta = (sampleA.t - sampleB.t) / 1000.0;
            
            const px = x + (d / maxDist) * w;
            const clamped = Math.max(-range, Math.min(range, delta));
            const py = y + h/2 + (clamped / range) * (h/2);
            
            if (i === 0) ctx.moveTo(px, py);
            else ctx.lineTo(px, py);
        }
        ctx.stroke();
        
        ctx.beginPath();
        ctx.strokeStyle = '#52525b';
        ctx.setLineDash([2, 2]);
        ctx.moveTo(x, y + h/2);
        ctx.lineTo(x + w, y + h/2);
        ctx.stroke();
        ctx.setLineDash([]);
    }
    
    function updateGauges() {
        const sampleA = getSampleAt(lapA, currentPos, alignMode);
        
        spdValA.innerText = sampleA.spd;
        gearValA.innerText = sampleA.gear === 0 ? 'N' : (sampleA.gear === -1 ? 'R' : sampleA.gear);
        thrBarA.style.height = (sampleA.thr * 100) + '%';
        brkBarA.style.height = (sampleA.brk * 100) + '%';
        ersValA.innerText = sampleA.ers === 3 ? 'OVTK' : (sampleA.ers === 2 ? 'HOTL' : (sampleA.ers === 1 ? 'MED' : 'NONE'));
        if (sampleA.drs === 1) {
            drsBadgeA.classList.add('active');
            drsBadgeA.style.color = '#09090b';
        } else {
            drsBadgeA.classList.remove('active');
            drsBadgeA.style.color = '#71717a';
        }
        
        if (lapB) {
            const sampleB = getSampleAt(lapB, currentPos, alignMode);
            
            spdValB.innerText = sampleB.spd;
            gearValB.innerText = sampleB.gear === 0 ? 'N' : (sampleB.gear === -1 ? 'R' : sampleB.gear);
            thrBarB.style.height = (sampleB.thr * 100) + '%';
            brkBarB.style.height = (sampleB.brk * 100) + '%';
            ersValB.innerText = sampleB.ers === 3 ? 'OVTK' : (sampleB.ers === 2 ? 'HOTL' : (sampleB.ers === 1 ? 'MED' : 'NONE'));
            if (sampleB.drs === 1) {
                drsBadgeB.classList.add('active');
                drsBadgeB.style.color = '#09090b';
            } else {
                drsBadgeB.classList.remove('active');
                drsBadgeB.style.color = '#71717a';
            }
        }
        
        if (alignMode === 'distance') {
            timeLabel.innerText = Math.round(currentPos) + ' / ' + Math.round(maxDist) + ' m';
        } else {
            timeLabel.innerText = (currentPos / 1000.0).toFixed(2) + ' / ' + (maxTime / 1000.0).toFixed(2) + ' s';
        }
    }
    
    function loop(timestamp) {
        if (!isPlaying) {
            lastTime = null;
            return;
        }
        
        if (!lastTime) lastTime = timestamp;
        const elapsed = timestamp - lastTime;
        lastTime = timestamp;
        
        const limit = alignMode === 'distance' ? maxDist : maxTime;
        
        if (alignMode === 'distance') {
            const sample = getSampleAt(lapA, currentPos, 'distance');
            const speedMS = sample.spd / 3.6;
            const distIncrement = speedMS * (elapsed / 1000.0) * playbackSpeed;
            currentPos += distIncrement;
        } else {
            currentPos += elapsed * playbackSpeed;
        }
        
        if (currentPos >= limit) {
            currentPos = 0;
            isPlaying = false;
            playPauseBtn.innerText = '▶ Play';
        }
        
        timeline.value = (currentPos / limit) * 100;
        
        drawTrack();
        drawCharts();
        updateGauges();
        
        requestAnimationFrame(loop);
    }
    
    playPauseBtn.onclick = () => {
        isPlaying = !isPlaying;
        if (isPlaying) {
            playPauseBtn.innerText = '⏸ Pause';
            requestAnimationFrame(loop);
        } else {
            playPauseBtn.innerText = '▶ Play';
        }
    };
    
    timeline.oninput = () => {
        const limit = alignMode === 'distance' ? maxDist : maxTime;
        currentPos = (timeline.value / 100) * limit;
        drawTrack();
        drawCharts();
        updateGauges();
    };
    
    speedSelect.onchange = () => {
        playbackSpeed = parseFloat(speedSelect.value);
    };
    
    alignSelect.onchange = () => {
        alignMode = alignSelect.value;
        currentPos = 0;
        timeline.value = 0;
        drawTrack();
        drawCharts();
        updateGauges();
    };
    
    chartsCanvas.onclick = (e) => {
        const rect = chartsCanvas.getBoundingClientRect();
        const clickX = e.clientX - rect.left;
        
        const padLeft = 45;
        const padRight = 15;
        const chartWidth = chartsCanvas.width - padLeft - padRight;
        const renderScale = chartsCanvas.width / rect.width;
        const scaledClickX = clickX * renderScale;
        
        if (scaledClickX >= padLeft && scaledClickX <= chartsCanvas.width - padRight) {
            const ratio = (scaledClickX - padLeft) / chartWidth;
            const limit = alignMode === 'distance' ? maxDist : maxTime;
            currentPos = ratio * limit;
            timeline.value = ratio * 100;
            drawTrack();
            drawCharts();
            updateGauges();
        }
    };
    
    drawTrack();
    drawCharts();
    updateGauges();
};
""";
    }

    private LapResult getFastestLapWithTelemetry(DriverResult dr) {
        if (dr == null) return null;
        return lapResultRepository.findByDriverResult(dr).stream()
                .filter(lr -> lapTelemetryRepository.findByLapResultId(lr.getId()).isPresent())
                .min(Comparator.comparingLong(LapResult::getLapTimeInMS))
                .orElse(null);
    }
}
