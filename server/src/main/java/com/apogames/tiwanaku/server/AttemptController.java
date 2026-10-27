package com.apogames.tiwanaku.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1")
public class AttemptController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttemptController.class);
    private final AttemptService attempts;

    public AttemptController(AttemptService attempts) {
        this.attempts = attempts;
    }

    @PostMapping("/attempts")
    public AttemptSnapshot create(@Valid @RequestBody CreateAttemptRequest request) {
        AttemptSnapshot snapshot = attempts.create(request);
        LOGGER.info("Attempt created attempt={} job={} revision={}",
                snapshot.attemptId(), request.generationJobId(), snapshot.revision());
        return snapshot;
    }

    @GetMapping("/attempts/{id}")
    public AttemptSnapshot get(
            @PathVariable("id") UUID id,
            @RequestParam("editorSessionId") @Size(min = 16, max = 128) String editorSessionId) {
        return attempts.get(id, editorSessionId);
    }

    @PostMapping("/attempts/{id}/commands")
    public AttemptSnapshot command(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AttemptCommand command) {
        AttemptSnapshot snapshot = attempts.command(id, command);
        LOGGER.info("Command accepted attempt={} command={} type={} cell={} value={} expectedRevision={} revision={} status={}",
                id, command.commandId(), command.type(), command.cellIndex(), command.value(), command.expectedRevision(),
                snapshot.revision(), snapshot.status());
        return snapshot;
    }

    @GetMapping("/attempts/{id}/result")
    public ResultSnapshot result(@PathVariable("id") UUID id) {
        return attempts.result(id);
    }

    @GetMapping("/attempts/{id}/commands/{commandId}")
    public AttemptSnapshot receipt(
            @PathVariable("id") UUID id,
            @PathVariable("commandId") @Size(min = 1, max = 128) String commandId,
            @RequestParam("editorSessionId") @Size(min = 16, max = 128) String editorSessionId) {
        return attempts.receipt(id, commandId, editorSessionId);
    }
}
