package com.dreamhomes.haven.dreamai;

import java.util.ArrayList;
import java.util.List;

/** Parses minimal SSE frames from MockMvc / integration responses (Spring {@code SseEmitter} output). */
final class DreamAiSseTestSupport {

    record SseEvent(String name, String dataJson) {}

    private DreamAiSseTestSupport() {}

    static List<SseEvent> parse(String raw) {
        List<SseEvent> events = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return events;
        }
        String eventName = "message";
        StringBuilder data = new StringBuilder();
        for (String line : raw.split("\r?\n")) {
            if (line.isEmpty()) {
                if (!data.isEmpty()) {
                    events.add(new SseEvent(eventName, data.toString().trim()));
                    data.setLength(0);
                }
                eventName = "message";
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring(5).trim());
            }
        }
        if (!data.isEmpty()) {
            events.add(new SseEvent(eventName, data.toString().trim()));
        }
        return events;
    }
}
