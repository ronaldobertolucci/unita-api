package io.github.ronaldobertolucci.unita.service.sse;

import io.github.ronaldobertolucci.unita.dto.group.InvitationNotificationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SseEmitterServiceTest {

    private SseEmitterService sseEmitterService;
    private Map<Long, SseEmitter> emitters;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        sseEmitterService = new SseEmitterService();

        Field field = SseEmitterService.class.getDeclaredField("emitters");
        field.setAccessible(true);
        emitters = (Map<Long, SseEmitter>) field.get(sseEmitterService);
    }

    // -------------------------------------------------------------------------
    // createEmitter
    // -------------------------------------------------------------------------

    @Test
    void createEmitter_ShouldReturnEmitterAndStoreIt() {
        SseEmitter emitter = sseEmitterService.createEmitter(1L);

        assertNotNull(emitter);
        assertTrue(emitters.containsKey(1L));
    }

    @Test
    void createEmitter_ShouldReplaceExistingEmitterForSameUser() {
        SseEmitter first = sseEmitterService.createEmitter(1L);
        SseEmitter second = sseEmitterService.createEmitter(1L);

        assertNotSame(first, second);
        assertEquals(second, emitters.get(1L));
    }

    @Test
    void createEmitter_OnCompletion_ShouldRemoveEmitter() throws Exception {
        sseEmitterService.createEmitter(1L);
        assertTrue(emitters.containsKey(1L));

        SseEmitter emitter = emitters.get(1L);

        Field callbackField = ResponseBodyEmitter.class.getDeclaredField("completionCallback");
        callbackField.setAccessible(true);
        Runnable completionCallback = (Runnable) callbackField.get(emitter);
        completionCallback.run();

        assertFalse(emitters.containsKey(1L));
    }

    // -------------------------------------------------------------------------
    // sendInvitationNotification
    // -------------------------------------------------------------------------

    @Test
    void sendInvitationNotification_WhenUserNotConnected_ShouldDoNothing() {
        InvitationNotificationDto dto = buildNotificationDto();

        assertDoesNotThrow(() -> sseEmitterService.sendInvitationNotification(99L, dto));
    }

    @Test
    void sendInvitationNotification_WhenUserConnected_ShouldSendEvent() throws Exception {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        emitters.put(1L, mockEmitter);

        InvitationNotificationDto dto = buildNotificationDto();
        sseEmitterService.sendInvitationNotification(1L, dto);

        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendInvitationNotification_WhenIOExceptionOccurs_ShouldRemoveEmitter() throws Exception {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Connection lost")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        emitters.put(1L, mockEmitter);

        InvitationNotificationDto dto = buildNotificationDto();
        sseEmitterService.sendInvitationNotification(1L, dto);

        assertFalse(emitters.containsKey(1L));
    }

    @Test
    void sendInvitationNotification_WhenIOExceptionOccurs_ShouldNotThrow() throws Exception {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Connection lost")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        emitters.put(1L, mockEmitter);

        InvitationNotificationDto dto = buildNotificationDto();

        assertDoesNotThrow(() -> sseEmitterService.sendInvitationNotification(1L, dto));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private InvitationNotificationDto buildNotificationDto() {
        return new InvitationNotificationDto(1L, "Família Silva", "João", "Silva");
    }
}