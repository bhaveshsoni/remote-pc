package io.orbion.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** Upper bound on a single incoming frame (64 KB) to cap per-message memory use. */
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;

    /** Drop idle sockets after 5 minutes of inactivity. */
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ControlWebSocketHandler handler;
    private final TokenHandshakeInterceptor interceptor;

    public WebSocketConfig(ControlWebSocketHandler handler, TokenHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Origin is explicitly validated in TokenHandshakeInterceptor (same-origin check
        // + session token), so we allow the pattern here and let the interceptor decide.
        registry.addHandler(handler, "/ws")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxSessionIdleTimeout(IDLE_TIMEOUT_MS);
        return container;
    }
}
