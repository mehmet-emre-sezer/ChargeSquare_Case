package com.chargesquare.session.web;

import com.chargesquare.session.service.SessionService;
import com.chargesquare.session.web.dto.SessionReceipt;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bir kullanıcının oturum geçmişini listelemek için HTTP giriş noktası. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final SessionService sessionService;

    public UserController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{userId}/sessions")
    public List<SessionReceipt> getUserSessions(@PathVariable Long userId) {
        return sessionService.getUserSessions(userId);
    }
}
