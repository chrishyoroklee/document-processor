package dev.hyorok.docproc.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController = @Controller + @ResponseBody: whatever a method returns
// is written to the HTTP response as JSON (Jackson does the conversion).
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
