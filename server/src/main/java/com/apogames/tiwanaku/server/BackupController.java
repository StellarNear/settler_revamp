package com.apogames.tiwanaku.server;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;

@RestController
@RequestMapping("/api/v1/data")
public final class BackupController {
    private final DataSource source;
    private final ObjectProvider<DataDirectoryLock> directory;
    public BackupController(DataSource source, ObjectProvider<DataDirectoryLock> directory) {
        this.source = source; this.directory = directory;
    }
    @PostMapping("/backup")
    public ResponseEntity<FileSystemResource> backup(@RequestParam("confirmed") boolean confirmed) {
        if (!confirmed) throw ApiException.badRequest("Confirm creation of a physical backup");
        var lock = directory.getIfAvailable();
        if (lock == null) throw ApiException.conflict("Physical backups require file-mode storage");
        var path = StoreBackups.create(source, lock.directory(), "manual");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(path.getFileName().toString()).build().toString())
                .body(new FileSystemResource(path));
    }
}
