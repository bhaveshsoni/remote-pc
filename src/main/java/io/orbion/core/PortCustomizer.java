package io.orbion.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Ensures Orbion always starts even when its preferred port is occupied.
 *
 * <p>The default port (8080) is frequently taken by other software – NVIDIA
 * Broadcast, for example, listens on 8080. Rather than fail to launch, we probe
 * the configured port and transparently fall back to a free ephemeral port.
 * The pairing URL and QR code are built from the actually-bound port
 * (see {@code TrayManager}), so the fallback is invisible to the user.
 */
@Component
public class PortCustomizer implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    private static final Logger log = LoggerFactory.getLogger(PortCustomizer.class);
    private static final int DEFAULT_PORT = 8080;

    private final Environment env;

    public PortCustomizer(Environment env) {
        this.env = env;
    }

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        int preferred = env.getProperty("server.port", Integer.class, DEFAULT_PORT);
        if (preferred == 0) {
            return; // explicit "random port" request – leave as-is
        }
        if (isAvailable(preferred)) {
            factory.setPort(preferred);
            return;
        }
        int fallback = findAvailablePort();
        log.warn("Preferred port {} is in use (another app is bound to it); "
                + "starting Orbion on free port {} instead.", preferred, fallback);
        factory.setPort(fallback);
    }

    /** True if a server socket can be bound to the given port on all interfaces. */
    private boolean isAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Asks the OS for any free port. */
    private int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to find a free port for Orbion", e);
        }
    }
}
