package be.jabapage.racingleague.f1telemetry.controller;

import be.jabapage.racingleague.f1telemetry.model.PacketHeader;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.ByteBuffer;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    @Autowired
    private TelemetryProcessingService telemetryProcessingService;

    @PostMapping
    public void receiveTelemetry(@RequestBody byte[] payload) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(payload);
        PacketHeader header = PacketHeader.fromByteBuffer(byteBuffer);
        telemetryProcessingService.processPacket(header, byteBuffer);
    }
}
