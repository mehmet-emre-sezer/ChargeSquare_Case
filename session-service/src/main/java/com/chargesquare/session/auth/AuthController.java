package com.chargesquare.session.auth;

import com.chargesquare.session.security.JwtService;
import com.chargesquare.session.web.dto.LoginRequest;
import com.chargesquare.session.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kimlik doğrulama girişi: geçerli kimlik bilgisinde rol taşıyan bir JWT üretir. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final DemoUsers demoUsers;
    private final JwtService jwtService;

    public AuthController(DemoUsers demoUsers, JwtService jwtService) {
        this.demoUsers = demoUsers;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        DemoUsers.Account account = demoUsers.authenticate(request.username(), request.password())
                .orElseThrow(InvalidCredentialsException::new);
        String token = jwtService.issueToken(account.username(), account.role());
        return new LoginResponse(token, account.role());
    }
}
