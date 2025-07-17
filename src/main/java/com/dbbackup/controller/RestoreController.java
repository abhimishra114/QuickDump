package com.dbbackup.controller;

import com.dbbackup.model.BackupRequest;
import com.dbbackup.service.RestoreService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class RestoreController {

    private final RestoreService restoreService;

    public RestoreController(RestoreService restoreService) {
        this.restoreService = restoreService;
    }

    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> restoreBackup(
            @RequestPart("zipFile") MultipartFile zipFile,
            @RequestPart("request") String request) {
        try {
            BackupRequest backupRequest = new ObjectMapper().readValue(request, BackupRequest.class);
            System.out.println("Received zip: " + zipFile.getOriginalFilename());
            System.out.println("Request DB Type: " + backupRequest.getDbType());

            String result = restoreService.restoreBackup(zipFile, backupRequest);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Restore failed: " + e.getMessage());
        }
    }
}
