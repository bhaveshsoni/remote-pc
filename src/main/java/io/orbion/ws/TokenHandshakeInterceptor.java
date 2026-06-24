package io.orbion.ws;

import io.orbion.core.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/** Rejects WebSocket handshakes that do not carry the valid session token. */
@Component
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TokenHandshakeInterceptor.class);

    private final SessionService sessionService;

    public TokenHandshakeInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Cross-Site WebSocket Hijacking defense: when a browser presents an Origin,
        // it must match the host this server is being reached on. Non-browser clients
        // (no Origin header) are still gated by the token check below.
        if (!isSameOrigin(request)) {
            log.warn("Rejected WebSocket handshake from {} (origin mismatch)", request.getRemoteAddress());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("token");
        if (sessionService.isValidToken(token)) {
            return true;
        }
        log.warn("Rejected WebSocket handshake from {} (invalid token)", request.getRemoteAddress());
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    /** True if there is no Origin header, or its host:port matches the request's Host header. */
    private boolean isSameOrigin(ServerHttpRequest request) {
        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return true; // non-browser client; token check still applies
        }
        String host = request.getHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        try {
            URI originUri = URI.create(origin);
            int originPort = originUri.getPort();
            String originHostPort = originUri.getPort() == -1
                    ? originUri.getHost()
                    : originUri.getHost() + ":" + originPort;
            return host.equalsIgnoreCase(originHostPort)
                    || host.equalsIgnoreCase(originUri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
