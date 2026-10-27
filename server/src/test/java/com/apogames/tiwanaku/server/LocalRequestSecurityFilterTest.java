package com.apogames.tiwanaku.server;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRequestSecurityFilterTest {
    @Test
    void allowsSameOriginMutationWithProcessToken() throws Exception {
        LocalRequestToken token = new LocalRequestToken();
        LocalRequestSecurityFilter filter = new LocalRequestSecurityFilter(token);
        MockHttpServletRequest request = request("POST", "localhost:8080", "http://localhost:8080");
        request.addHeader("X-Tiwanaku-Token", token.value());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsForeignHostAndOrigin() throws Exception {
        LocalRequestToken token = new LocalRequestToken();
        LocalRequestSecurityFilter filter = new LocalRequestSecurityFilter(token);
        MockHttpServletResponse foreignHostResponse = new MockHttpServletResponse();
        MockHttpServletRequest foreignHost = request("GET", "example.com:8080", null);
        filter.doFilter(foreignHost, foreignHostResponse, new MockFilterChain());
        assertEquals(403, foreignHostResponse.getStatus());

        MockHttpServletResponse foreignOriginResponse = new MockHttpServletResponse();
        MockHttpServletRequest foreignOrigin = request("GET", "localhost:8080", "http://evil.example");
        filter.doFilter(foreignOrigin, foreignOriginResponse, new MockFilterChain());
        assertEquals(403, foreignOriginResponse.getStatus());
    }

    @Test
    void rejectsMutationsWithoutOriginOrToken() throws Exception {
        LocalRequestSecurityFilter filter = new LocalRequestSecurityFilter(new LocalRequestToken());
        MockHttpServletResponse missingOriginResponse = new MockHttpServletResponse();

        filter.doFilter(
                request("POST", "localhost:8080", null),
                missingOriginResponse,
                new MockFilterChain());

        assertEquals(403, missingOriginResponse.getStatus());

        MockHttpServletResponse missingTokenResponse = new MockHttpServletResponse();
        filter.doFilter(
                request("POST", "localhost:8080", "http://localhost:8080"),
                missingTokenResponse,
                new MockFilterChain());
        assertEquals(403, missingTokenResponse.getStatus());
    }

    @Test
    void retainsLocalBoundariesForMalformedAuthoritiesPortsTokensAndRemoteClients() throws Exception {
        LocalRequestToken token = new LocalRequestToken();
        LocalRequestSecurityFilter filter = new LocalRequestSecurityFilter(token);
        for (String host : new String[]{"localhost:8081", "localhost:8080@evil.example",
                "localhost:8080/path", "localhost.evil.example:8080"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", host, null), response, new MockFilterChain());
            assertEquals(403, response.getStatus(), host);
        }
        for (String origin : new String[]{"null", "http://localhost:8081",
                "https://localhost:8080", "http://localhost:8080?foreign=1",
                "http://localhost:8080@evil.example"}) {
            MockHttpServletRequest request = request("POST", "localhost:8080", origin);
            request.addHeader("X-Tiwanaku-Token", token.value());
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(403, response.getStatus(), origin);
        }
        MockHttpServletRequest remote = request("GET", "localhost:8080", null);
        remote.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse remoteResponse = new MockHttpServletResponse();
        filter.doFilter(remote, remoteResponse, new MockFilterChain());
        assertEquals(403, remoteResponse.getStatus());

        MockHttpServletRequest staleToken = request("POST", "localhost:8080", "http://localhost:8080");
        staleToken.addHeader("X-Tiwanaku-Token", new LocalRequestToken().value());
        MockHttpServletResponse staleResponse = new MockHttpServletResponse();
        filter.doFilter(staleToken, staleResponse, new MockFilterChain());
        assertEquals(403, staleResponse.getStatus());
    }

    @Test
    void sendsLocalOnlyContentPolicyOnPagesAndRejectedRequests() throws Exception {
        LocalRequestSecurityFilter filter = new LocalRequestSecurityFilter(new LocalRequestToken());
        for (String host : new String[]{"localhost:8080", "foreign.example:8080"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", host, null), response, new MockFilterChain());
            String policy = response.getHeader("Content-Security-Policy");
            assertTrue(policy.contains("script-src 'self'"));
            assertTrue(policy.contains("connect-src 'self'"));
            assertTrue(policy.contains("object-src 'none'"));
            assertTrue(policy.contains("frame-ancestors 'none'"));
            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
            assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        }
    }

    private static MockHttpServletRequest request(String method, String host, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/commands");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setLocalPort(8080);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Host", host);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
