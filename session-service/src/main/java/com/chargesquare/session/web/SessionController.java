package com.chargesquare.session.web;

import com.chargesquare.session.service.SessionService;
import com.chargesquare.session.web.dto.SessionReceipt;
import com.chargesquare.session.web.dto.StartSessionRequest;
import com.chargesquare.session.web.dto.StartedSessionResponse;
import com.chargesquare.session.web.dto.StopSessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oturum start/stop ve okuma için HTTP giriş noktaları. Bilerek ince tutulur. */
@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<StartedSessionResponse> start(@Valid @RequestBody StartSessionRequest request) {
        StartedSessionResponse response = sessionService.start(request.userId(), request.connectorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/stop")
    public SessionReceipt stop(@PathVariable Long id, @Valid @RequestBody StopSessionRequest request) {
        return sessionService.stop(id, request.energyKwh());
    }

    @GetMapping("/{id}")
    public SessionReceipt getSession(@PathVariable Long id) {
        return sessionService.getSession(id);
    }
}
