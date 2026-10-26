package com.apogames.tiwanaku.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:server-smoke;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver"
        })
class LocalServerBootstrapIntegrationTest {
    @LocalServerPort
    private int port;

    @Test
    void servesBootstrapFromTheLoopbackOnlySpringServer() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/bootstrap")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"apiVersion\":\"v1\""));
        assertTrue(response.body().contains("\"requestToken\":\""));
        assertFalse(response.body().contains("\"requestToken\":\"\""));
    }

    @Test
    void blocksMutationWithoutLocalOriginAndToken() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/attempts"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode());
    }
}
