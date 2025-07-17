package com.dbbackup.service;

import com.dbbackup.model.BackupRequest;
import com.dbbackup.utility.DownloadResponse;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class BackupService {


    public String performBackup(BackupRequest request) throws IOException, InterruptedException {
        String dbType = request.getDbType().toLowerCase();

        String backupDir = "backups";
        Files.createDirectories(Paths.get(backupDir));

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String zipFileName = request.getDbName() + "_" + timestamp + ".zip";
        String zipFilePath = backupDir + File.separator + zipFileName;

        ProcessBuilder pb;
        File tempBackupDir = new File(backupDir + File.separator + request.getDbName() + "_" + timestamp);
        Files.createDirectories(tempBackupDir.toPath());

        if ("mysql".equals(dbType)) {
            // Create MySQL dump
            List<String> command = Arrays.asList(
                    "mysqldump",
                    "-h", request.getHost(),
                    "-P", String.valueOf(request.getPort()),
                    "-u", request.getUsername(),
                    "-p" + request.getPassword(),
                    request.getDbName()
            );

            File sqlFile = new File(tempBackupDir, request.getDbName() + ".sql");
            pb = new ProcessBuilder(command);
            pb.redirectOutput(sqlFile);
            // Discard stderr to prevent warnings from polluting .sql
            String nullDevice = System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null";
            pb.redirectError(new File(nullDevice));

        } else if ("mongodb".equals(dbType)) {
            // Create MongoDB dump
            List<String> command = new ArrayList<>();
            command.add("mongodump");
            command.add("--host=" + request.getHost());
            command.add("--port=" + request.getPort());
            command.add("--db=" + request.getDbName());
            if (request.getUsername() != null && !request.getUsername().isBlank()) {
                command.add("--username=" + request.getUsername());
                command.add("--password=" + request.getPassword());
            }
            command.add("--out=" + tempBackupDir.getAbsolutePath());

            pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

        } else {
            throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }

        // Run backup command
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Backup failed. Exit code: " + exitCode);
        }

        // Compress the backup folder
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipDirectory(tempBackupDir, tempBackupDir.getName(), zos);
        }

        // Clean up temp folder
        deleteDirectory(tempBackupDir);

        return "Backup successful. File: " + zipFilePath;
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                deleteDirectory(file);
            }
        }
        Files.deleteIfExists(directory.toPath());
    }


    private void zipDirectory(File folder, String parentFolder, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(parentFolder + "/" + file.getName());
                    zos.putNextEntry(zipEntry);
                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zos.write(bytes, 0, length);
                    }
                }
            }
        }
    }

    public boolean testConnection(BackupRequest request) {
        if (request.getDbType().equalsIgnoreCase("mysql")) {
            return testMySqlConnection(request);
        } else if (request.getDbType().equalsIgnoreCase("mongodb")) {
            return testMongoConnection(request);
        }
        return false;
    }

    private boolean testMongoConnection(BackupRequest request) {

        String uri;
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            // Use authentication
            uri = String.format("mongodb://%s:%s@%s:%d/%s",
                    request.getUsername(),
                    request.getPassword(),
                    request.getHost(),
                    request.getPort(),
                    request.getDbName());
        } else {
            // No authentication required
            uri = String.format("mongodb://%s:%d/%s",
                    request.getHost(),
                    request.getPort(),
                    request.getDbName());
        }
//        String uri = "mongodb://" + request.getUsername() + ":" + request.getPassword()
//                + "@" + request.getHost() + ":" + request.getPort() + "/" + request.getDbName();
        try (MongoClient mongoClient = MongoClients.create(uri)) {
            mongoClient.getDatabase(request.getDbName()).runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            log.error("Mongodb connection failed: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean testMySqlConnection(BackupRequest request) {
        String url = "jdbc:mysql://" + request.getHost() + ":" + request.getPort() + "/" + request.getDbName();
        try (Connection conn = DriverManager.getConnection(url, request.getUsername(), request.getPassword())) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            log.error("MySQL connection failed: {}", e.getMessage(), e);
            return false;
        }
    }


    public DownloadResponse backupAndDownload(
            String dbType,
            String host,
            int port,
            String username,
            String password,
            String dbName) throws IOException, InterruptedException {

        Path tempDir = Files.createTempDirectory("backup_");
        File backupDir = tempDir.toFile();
        File dumpTarget = new File(backupDir, dbName);

        List<String> command;
        if ("mysql".equalsIgnoreCase(dbType)) {
            File sqlFile = new File(backupDir, dbName + ".sql");
            command = List.of("mysqldump", "-h", host, "-P", String.valueOf(port), "-u", username, "-p" + password, dbName);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectOutput(sqlFile);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (process.waitFor() != 0) throw new RuntimeException("MySQL dump failed");

        } else if ("mongodb".equalsIgnoreCase(dbType)) {
            command = new ArrayList<>(List.of("mongodump", "--host=" + host, "--port=" + port, "--db=" + dbName));
            if (!username.isBlank()) {
                command.add("--username=" + username);
                command.add("--password=" + password);
            }
            command.add("--out=" + dumpTarget.getAbsolutePath());
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (process.waitFor() != 0) throw new RuntimeException("MongoDB dump failed");

        } else {
            throw new IllegalArgumentException("Unsupported DB type");
        }

        // Create the zip
        String finalFilename = dbName + "_" + System.currentTimeMillis() + ".zip";
        File zipFile = new File(tempDir.toFile(), finalFilename);
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipDirectory(backupDir, backupDir.getName(), zos);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));

        // Optional: schedule cleanup
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                Files.deleteIfExists(zipFile.toPath());
                Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (Exception ignored) {}
        }).start();

        return new DownloadResponse(resource, finalFilename);
    }


}
