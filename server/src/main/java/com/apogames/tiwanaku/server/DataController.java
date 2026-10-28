package com.apogames.tiwanaku.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public final class DataController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DataController.class);
    private final HistoryService history;
    public DataController(HistoryService history) { this.history = history; }
    public record SettingsRequest(@NotNull @Min(0) Long revision, @Valid @NotNull LocalSettings settings) { }
    public record RenameRequest(@NotNull @Pattern(regexp="[A-Za-z]{0,3}") String playerTag) { }
    public record ImportRequest(@NotNull @Size(max=HistoryService.MAX_SOURCE) String source,
                                @NotNull @Pattern(regexp="legacy|logical") String format,
                                String fingerprint, Boolean confirmed, Boolean restoreSettings) { }
    public record DesktopSource(String source) { }

    @GetMapping("/settings") public HistoryService.SettingsSnapshot settings() { return history.settings(); }
    @PostMapping("/settings") public HistoryService.SettingsSnapshot save(@Valid @RequestBody SettingsRequest request) {
        var saved = history.saveSettings(request.revision(), request.settings());
        LOGGER.info("Settings saved revision={}", saved.revision());
        return saved;
    }
    @GetMapping("/history") public HistoryService.History history(
            @RequestParam(value="player",defaultValue="") String player, @RequestParam(value="mapSize",defaultValue="") String mapSize,
            @RequestParam(value="scoring",defaultValue="") String scoring, @RequestParam(value="assistance",defaultValue="") String assistance,
            @RequestParam(value="sort",defaultValue="date-desc") String sort,
            @RequestParam(value="shape",defaultValue="") String shape) {
        return history.history(player, mapSize, scoring, assistance, sort, shape);
    }
    @PostMapping("/history/{id}/player") public HistoryService.History rename(@PathVariable("id") UUID id, @Valid @RequestBody RenameRequest request) {
        history.rename(id, request.playerTag());
        LOGGER.info("History player updated record={}", id);
        return history.history("", "", "", "", "date-desc");
    }
    @DeleteMapping("/history/{id}") public HistoryService.History delete(@PathVariable("id") UUID id, @RequestParam("confirmed") boolean confirmed) {
        if (!confirmed) throw ApiException.badRequest("History deletion requires confirmation");
        history.delete(id);
        LOGGER.info("History entry hidden record={}", id);
        return history.history("", "", "", "", "date-desc");
    }
    @GetMapping("/data/export") public HistoryService.Archive export() { return history.exportArchive(); }
    @PostMapping("/data/preview") public HistoryService.ImportPreview preview(@Valid @RequestBody ImportRequest request) {
        return history.preview(request.source(), request.format());
    }
    @PostMapping("/data/import") public HistoryService.ImportOutcome importData(@Valid @RequestBody ImportRequest request) {
        if (!Boolean.TRUE.equals(request.confirmed()) || request.fingerprint() == null) throw ApiException.badRequest("Preview and confirm the import first");
        var outcome = history.importSource(request.source(), request.format(), request.fingerprint(), Boolean.TRUE.equals(request.restoreSettings()));
        LOGGER.info("History imported format={} fingerprint={} count={} rejected={} repeated={}",
                request.format(), outcome.fingerprint(), outcome.imported(), outcome.rejected().size(), outcome.alreadyImported());
        return outcome;
    }
    @GetMapping("/data/imports/{fingerprint}") public DesktopSource raw(@PathVariable("fingerprint") String fingerprint) {
        return new DesktopSource(history.rawImport(fingerprint));
    }
    @PostMapping("/data/desktop-preview") public DesktopSource desktop(@RequestParam("confirmed") boolean confirmed) throws IOException {
        if (!confirmed) throw ApiException.badRequest("Confirm reading the known desktop preferences file");
        Path file = Path.of(System.getProperty("user.home"), ".prefs", "TiwanakuScoreboard");
        if (!Files.isRegularFile(file)) throw ApiException.notFound("No desktop TiwanakuScoreboard preferences file found");
        if (Files.size(file) > HistoryService.MAX_SOURCE) throw ApiException.badRequest("Desktop preferences file is too large");
        try (var stream = Files.newInputStream(file)) {
            byte[] data = stream.readNBytes(HistoryService.MAX_SOURCE + 1);
            if (data.length > HistoryService.MAX_SOURCE) throw ApiException.badRequest("Desktop preferences file is too large");
            return new DesktopSource(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
