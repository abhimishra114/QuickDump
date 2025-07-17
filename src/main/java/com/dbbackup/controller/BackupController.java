package com.dbbackup.controller;

import com.dbbackup.model.BackupRequest;
import com.dbbackup.service.BackupService;
import com.dbbackup.utility.DownloadResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }
    @GetMapping("/test")
    public ResponseEntity<Boolean> testConnection(@RequestBody BackupRequest request) {
        boolean isConnected = backupService.testConnection(request);
        if (isConnected) {
            return new ResponseEntity<>(isConnected, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(isConnected, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping
    public ResponseEntity<String> backup(@RequestBody BackupRequest request) {
        try {
            String result = backupService.performBackup(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Backup failed: " + e.getMessage());
        }
    }


    @GetMapping("/download")
    public ResponseEntity<Resource> backupAndDownload(@RequestParam String dbType,
                                                      @RequestParam String host,
                                                      @RequestParam int port,
                                                      @RequestParam String username,
                                                      @RequestParam String password,
                                                      @RequestParam String dbName) {
        try {
            DownloadResponse response = backupService.backupAndDownload(dbType, host, port, username, password, dbName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + response.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getResource());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


}
