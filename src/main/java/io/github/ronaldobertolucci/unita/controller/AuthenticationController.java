package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.security.ResendVerificationDto;
import io.github.ronaldobertolucci.unita.dto.security.TokenDto;
import io.github.ronaldobertolucci.unita.dto.user.LoginDto;
import io.github.ronaldobertolucci.unita.dto.user.UserDto;
import io.github.ronaldobertolucci.unita.dto.user.UserRegistrationDto;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.service.email.EmailVerificationService;
import io.github.ronaldobertolucci.unita.service.security.RefreshSessionService;
import io.github.ronaldobertolucci.unita.config.security.RefreshCookie;
import io.github.ronaldobertolucci.unita.exception.RefreshRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import java.util.Map;
import io.github.ronaldobertolucci.unita.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final RefreshSessionService sessions;
    private final RefreshCookie cookies;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;


    @PostMapping("/login")
    public ResponseEntity<TokenDto> login(@RequestBody @Valid LoginDto loginDto, HttpServletRequest request) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(
                loginDto.email(),
                loginDto.password()
        );

        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        User user = (User) authentication.getPrincipal();

        return issued(request, sessions.login(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        try {
            return issued(request, sessions.refresh(cookies.read(request)));
        } catch (RefreshRejectedException e) {
            // The transactional service has already committed any replay revocation.
            return ResponseEntity.status(401).header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .header(HttpHeaders.SET_COOKIE, cookies.clear(request))
                    .body(Map.of("status", 401, "code", "SESSION_EXPIRED"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.logout(cookies.read(request));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookies.clear(request)).build();
    }

    private ResponseEntity<TokenDto> issued(HttpServletRequest request, RefreshSessionService.Issued issued) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookies.issue(request, issued.refreshToken(), issued.expiresAt()))
                .body(issued.access());
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody @Valid UserRegistrationDto registrationDto) {
        UserDto userDto = userService.register(registrationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userDto);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        UserDto userDto = new UserDto(user);

        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestBody @Valid ResendVerificationDto dto) {
        emailVerificationService.resendVerificationEmail(dto.email());
        return ResponseEntity.ok().build();
    }
}