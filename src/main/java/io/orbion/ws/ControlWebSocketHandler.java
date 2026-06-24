package io.orbion.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orbion.core.CommandExecutor;
import io.orbion.core.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the phone-to-desktop control channel.
 * Messages are JSON: {"type":"key","command":"ENTER"} or {"type":"text","value":"..."}.
 */
@Component
public class ControlWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ControlWebSocketHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final SessionService sessionService;
    private final CommandExecutor executor;

    /** Max messages accepted per session per sliding 1-second window. */
    private static final int MAX_MESSAGES_PER_SECOND = 200;
    private final Map<String, long[]> rateWindows = new ConcurrentHashMap<>();

    public ControlWebSocketHandler(SessionService sessionService, CommandExecutor executor) {
        this.sessionService = sessionService;
        this.executor = executor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        int devices = sessionService.deviceConnected();
        log.info("Device connected: {} (total: {})", session.getRemoteAddress(), devices);
        send(session, Map.of("type", "status", "connected", true, "devices", devices));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!withinRateLimit(session)) {
            // Silently drop bursts above the limit instead of executing them.
            log.warn("Rate limit exceeded for {}; dropping message", session.getRemoteAddress());
            return;
        }
        JsonNode json = mapper.readTree(message.getPayload());
        String type = json.path("type").asText("");
        switch (type) {
            case "key" -> {
                String command = json.path("command").asText("");
                boolean ok = executor.pressKey(command);
                sessionService.recordCommand("KEY " + command + (ok ? "" : " (failed)"));
                send(session, Map.of("type", "ack", "command", command, "ok", ok));
            }
            case "text" -> {
                String value = json.path("value").asText("");
                boolean ok = executor.pasteText(value);
                String preview = value.length() > 40 ? value.substring(0, 40) + "..." : value;
                sessionService.recordCommand("TEXT \"" + preview + "\"" + (ok ? "" : " (failed)"));
                send(session, Map.of("type", "ack", "command", "TEXT", "ok", ok));
            }
            case "mouse_move" -> {
                int dx = json.path("dx").asInt(0);
                int dy = json.path("dy").asInt(0);
                executor.mouseMove(dx, dy);
            }
            case "mouse_click" -> {
                String button = json.path("button").asText("left");
                boolean ok = executor.mouseClick(button);
                sessionService.recordCommand("MOUSE_CLICK " + button + (ok ? "" : " (failed)"));
                send(session, Map.of("type", "ack", "command", "MOUSE_CLICK", "ok", ok));
            }
            case "scroll" -> {
                int dy = json.path("dy").asInt(0);
                executor.scroll(dy);
            }
            case "ping" -> send(session, Map.of("type", "pong"));
            default -> log.warn("Unknown message type: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        rateWindows.remove(session.getId());
        int devices = sessionService.deviceDisconnected();
        log.info("Device disconnected: {} (total: {})", session.getRemoteAddress(), devices);
    }

    /** Simple fixed-window limiter: at most MAX_MESSAGES_PER_SECOND per session per second. */
    private boolean withinRateLimit(WebSocketSession session) {
        long now = System.currentTimeMillis();
        long[] window = rateWindows.computeIfAbsent(session.getId(), k -> new long[]{now, 0});
        synchronized (window) {
            if (now - window[0] >= 1000) {
                window[0] = now;
                window[1] = 0;
            }
            window[1]++;
            return window[1] <= MAX_MESSAGES_PER_SECOND;
        }
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.warn("Failed to send message: {}", e.getMessage());
        }
    }
}
