package com.apogames.tiwanaku.server;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/generation-jobs")
public class GenerationController {
    private final GenerationJobService jobs;

    public GenerationController(GenerationJobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerationJobService.JobSnapshot submit(@Valid @RequestBody GenerationRequest request) {
        return jobs.submit(request);
    }

    @GetMapping("/{id}")
    public GenerationJobService.JobSnapshot get(@PathVariable("id") UUID id) {
        return jobs.get(id);
    }

    @DeleteMapping("/{id}")
    public GenerationJobService.JobSnapshot cancel(@PathVariable("id") UUID id) {
        return jobs.cancel(id);
    }
}
