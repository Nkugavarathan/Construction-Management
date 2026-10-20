package com.constructflow.auth.controller;

import com.constructflow.auth.dto.AuthDtos.AuthResponse;
import com.constructflow.auth.dto.AuthDtos.ChangePasswordRequest;
import com.constructflow.auth.dto.AuthDtos.LoginRequest;
import com.constructflow.auth.dto.AuthDtos.MeResponse;
import com.constructflow.auth.dto.AuthDtos.RegisterRequest;
import com.constructflow.auth.dto.AuthDtos.SwitchOrganizationRequest;
import com.constructflow.auth.service.AuthService;
import com.constructflow.auth.service.AuthService.AuthResult;
import com.constructflow.auth.service.ClientInfo;
import com.constructflow.security.AuthProperties;
import com.constructflow.security.CurrentUser;
import com.constructflow.security.OriginValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    static final String REFRESH_COOKIE = "cf_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final AuthProperties properties;
    private final OriginValidator originValidator;

    public AuthController(AuthService authService, AuthProperties properties, OriginValidator originValidator) {
        this.authService = authService;
        this.properties = properties;
        this.originValidator = originValidator;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new organization and its first administrator")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return withSession(HttpStatus.CREATED, authService.register(request, ClientInfo.from(http)));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in; returns an access token and sets the HttpOnly refresh cookie")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.login(request, ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie and get a new access token")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                                HttpServletRequest http) {
        originValidator.assertAllowed(http);
        return withSession(HttpStatus.OK, authService.refresh(refreshToken, ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the session and clear the refresh cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                       HttpServletRequest http) {
        originValidator.assertAllowed(http);
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal CurrentUser user) {
        return authService.me(user);
    }

    @PostMapping("/switch-organization")
    public ResponseEntity<AuthResponse> switchOrganization(@AuthenticationPrincipal CurrentUser user,
                                                           @Valid @RequestBody SwitchOrganizationRequest request,
                                                           @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                                           HttpServletRequest http) {
        originValidator.assertAllowed(http);
        return withSession(HttpStatus.OK,
                authService.switchOrganization(user, request.organizationId(), refreshToken, ClientInfo.from(http)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal CurrentUser user,
                                                       @Valid @RequestBody ChangePasswordRequest request,
                                                       HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.changePassword(user, request, ClientInfo.from(http)));
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), properties.refreshTokenTtl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthResponse(result.accessToken().value(), result.accessToken().expiresAt(), result.me()));
    }

    /** HttpOnly: unreadable by JavaScript. SameSite=Strict: never sent on cross-site requests. Path: only auth endpoints. */
    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
