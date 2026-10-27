package com.apogames.tiwanaku.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Component
public final class LocalRequestSecurityFilter extends OncePerRequestFilter {
    private static final String TOKEN_HEADER = "X-Tiwanaku-Token";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private final LocalRequestToken token;

    public LocalRequestSecurityFilter(LocalRequestToken token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/")
                || Set.of("/", "/index.html", "/setup", "/play", "/history", "/settings")
                        .contains(request.getRequestURI())) {
            response.setHeader("Cache-Control", "no-store");
        }
        if (!isLoopbackClient(request) || !isAllowedHost(request) || !isAllowedOrigin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Request must use the local application origin");
            return;
        }
        if (isMutation(request.getMethod()) && !token.matches(request.getHeader(TOKEN_HEADER))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "A valid local request token is required");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLoopbackClient(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isAllowedHost(HttpServletRequest request) {
        URI hostUri = parseAuthority(request.getHeader("Host"));
        if (hostUri == null) {
            return false;
        }
        String host = normalizeHost(hostUri.getHost());
        int port = hostUri.getPort() < 0 ? 80 : hostUri.getPort();
        return LOOPBACK_HOSTS.contains(host) && port == request.getLocalPort();
    }

    private boolean isAllowedOrigin(HttpServletRequest request) {
        String originHeader = request.getHeader("Origin");
        if (originHeader == null) {
            return !isMutation(request.getMethod());
        }
        URI origin;
        try {
            origin = new URI(originHeader);
        } catch (URISyntaxException exception) {
            return false;
        }
        if (!"http".equalsIgnoreCase(origin.getScheme())
                || origin.getRawUserInfo() != null
                || origin.getRawPath() != null && !origin.getRawPath().isEmpty() && !"/".equals(origin.getRawPath())
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null) {
            return false;
        }
        String host = normalizeHost(origin.getHost());
        int port = origin.getPort() < 0 ? 80 : origin.getPort();
        return LOOPBACK_HOSTS.contains(host) && port == request.getLocalPort();
    }

    private URI parseAuthority(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return null;
        }
        try {
            URI parsed = new URI("http://" + hostHeader);
            if (!hostHeader.equals(parsed.getRawAuthority())
                    || parsed.getHost() == null
                    || parsed.getRawUserInfo() != null
                    || parsed.getRawPath() != null && !parsed.getRawPath().isEmpty()
                    || parsed.getRawQuery() != null
                    || parsed.getRawFragment() != null) {
                return null;
            }
            return parsed;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String normalizeHost(String host) {
        return host == null ? "" : host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
    }

    private boolean isMutation(String method) {
        return switch (method) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }
}
