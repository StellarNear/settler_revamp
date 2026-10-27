package com.apogames.tiwanaku.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class SpaController {
    @GetMapping({"/setup", "/play", "/history", "/settings"})
    public String page() {
        return "forward:/index.html";
    }
}
