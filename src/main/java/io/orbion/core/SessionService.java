package io.orbion.core;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the per-launch session state: the secure pairing token,
 * connected device count and the recent command log.
 */
@Service
public class SessionService {

    private static final int MAX_RECENT_COMMANDS = 50;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String token;
    private final String localIp;
    private final AtomicInteger deviceCount = new AtomicInteger(0);
    private final Deque<String> recentCommands = new ArrayDeque<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public SessionService() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        this.token = HexFormat.of().formatHex(bytes);
        this.localIp = NetworkUtils.findLocalIp();
    }

    public String getToken() {
        return token;
    }

    public String getLocalIp() {
        return localIp;
    }

    public boolean isValidToken(String candidate) {
        if (candidate == null) {
            return false;
        }
        // Constant-time comparison to avoid leaking the token via timing differences.
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    public String pairingUrl(String scheme, int port) {
        return scheme + "://" + localIp + ":" + port + "/?token=" + token;
    }

    public int deviceConnected() {
        int count = deviceCount.incrementAndGet();
        record("Device connected");
        return count;
    }

    public int deviceDisconnected() {
        int count = deviceCount.updateAndGet(c -> Math.max(0, c - 1));
        record("Device disconnected");
        return count;
    }

    public int getDeviceCount() {
        return deviceCount.get();
    }

    public void recordCommand(String description) {
        record(description);
    }

    public synchronized List<String> getRecentCommands() {
        return new ArrayList<>(recentCommands);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void record(String description) {
        synchronized (this) {
            recentCommands.addFirst("[" + LocalTime.now().format(TIME) + "] " + description);
            while (recentCommands.size() > MAX_RECENT_COMMANDS) {
                recentCommands.removeLast();
            }
        }
        listeners.forEach(Runnable::run);
    }
}
