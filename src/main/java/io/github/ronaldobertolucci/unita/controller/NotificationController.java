package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.service.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterService sseEmitterService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return sseEmitterService.createEmitter(currentUser.getId());
    }
}