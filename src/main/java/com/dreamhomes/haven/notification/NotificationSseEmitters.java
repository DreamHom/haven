package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.notification.model.NotificationKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-user in-memory registry of {@link SseEmitter}s for {@code GET /api/notifications/stream}.
 * Persona audit (Temi): every poll of {@code /notifications/mine} is wasted bandwidth when
 * the server already knows when a row appeared. SSE pushes the event the moment
 * {@link NotificationService#recordSync} commits.
 *
 * <p>Single-instance only — if the app ever scales horizontally, a Redis pub-sub layer
 * would fan events across nodes. That's deferred until we have a real reason to scale out.
 */
@Component
@Slf4j
public class NotificationSseEmitters {

    private static final long DEFAULT_TIMEOUT_MS = 30L * 60L * 1000L;

    private final ConcurrentHashMap<Long, List<SseEmitter>> byUser = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        byUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(t -> remove(userId, emitter));
        // Initial comment line keeps the connection open through buffering proxies.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    public void push(Long userId, Long notificationId, NotificationKind kind, Map<String, Object> payload) {
        List<SseEmitter> list = byUser.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(kind.name())
                        .data(Map.of(
                                "id", notificationId,
                                "kind", kind.name(),
                                "payload", payload == null ? Map.of() : payload)));
            } catch (Exception e) {
                log.debug("SSE send failed for user {}, dropping emitter: {}", userId, e.toString());
                emitter.complete();
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = byUser.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                byUser.remove(userId, list);
            }
        }
    }
}
