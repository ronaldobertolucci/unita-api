package io.github.ronaldobertolucci.unita.service.sse;

import io.github.ronaldobertolucci.unita.dto.group.InvitationNotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        emitters.put(userId, emitter);
        return emitter;
    }

    public void sendInvitationNotification(Long userId, InvitationNotificationDto dto) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("invitation")
                    .data(dto));
        } catch (IOException e) {
            log.warn("Failed to send SSE event to user {}: {}", userId, e.getMessage());
            emitters.remove(userId);
        }
    }
}