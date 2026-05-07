package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import io.github.ronaldobertolucci.unita.service.sse.SseEmitterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private SseEmitterService sseEmitterService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .enabled(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /notifications/stream
    // -------------------------------------------------------------------------

    @Test
    void streamNotifications_WhenAuthenticated_ShouldReturn200WithSseContentType() throws Exception {
        when(sseEmitterService.createEmitter(any())).thenReturn(new SseEmitter());

        mockMvc.perform(get("/notifications/stream")
                        .with(authentication(buildAuthentication())))
                .andExpect(status().isOk());
    }

    @Test
    void streamNotifications_WhenAuthenticated_ShouldCreateEmitterForCurrentUser() throws Exception {
        when(sseEmitterService.createEmitter(any())).thenReturn(new SseEmitter());

        mockMvc.perform(get("/notifications/stream")
                .with(authentication(buildAuthentication())));

        verify(sseEmitterService, times(1)).createEmitter(testUser.getId());
    }

    @Test
    void streamNotifications_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/notifications/stream"))
                .andExpect(status().isForbidden());

        verify(sseEmitterService, never()).createEmitter(any());
    }

    private Authentication buildAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                testUser,
                null,
                List.of(new SimpleGrantedAuthority("USER"))
        );
    }
}