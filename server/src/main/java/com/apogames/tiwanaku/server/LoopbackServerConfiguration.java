package com.apogames.tiwanaku.server;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

@Configuration
public class LoopbackServerConfiguration {
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> bindToLoopback() {
        return factory -> factory.setAddress(InetAddress.getLoopbackAddress());
    }
}
