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


    private File generateBackupZip(BackupRequest request, boolean storePermanently) throws IOException, InterruptedException {
        String dbType = request.getDbType().toLowerCase();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = request.getDbName() + "_" + timestamp + ".zip";

        File baseDir = storePermanently
                ? new File("backups")
                : Files.createTempDirectory("backup_").toFile();

        if (storePermanently) {
            Files.createDirectories(baseDir.toPath());
        }

        File tempBackupDir = new File(baseDir, request.getDbName() + "_" + timestamp);
        Files.createDirectories(tempBackupDir.toPath());

        ProcessBuilder pb;

        if ("mysql".equals(dbType)) {
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
            String nullDevice = System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null";
            pb.redirectError(new File(nullDevice));

        } else if ("mongodb".equals(dbType)) {
            List<String> command = new ArrayList<>(List.of(
                    "mongodump",
                    "--host=" + request.getHost(),
                    "--port=" + request.getPort(),
                    "--db=" + request.getDbName()
            ));
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

        Process process = pb.start();
        if (process.waitFor() != 0) {
            throw new RuntimeException("Backup failed.");
        }

        File zipFile = new File(baseDir, filename);
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipDirectory(tempBackupDir, tempBackupDir.getName(), zos);
        }

        deleteDirectory(tempBackupDir); // Clean temp

        return zipFile;
    }

    public String performBackup(BackupRequest request) throws IOException, InterruptedException {
        File zipFile = generateBackupZip(request, true);
        return "Backup successful. File: " + zipFile.getAbsolutePath();
    }

    public DownloadResponse backupAndDownload(
            String dbType,
            String host,
            int port,
            String username,
            String password,
            String dbName) throws IOException, InterruptedException {

        BackupRequest request = new BackupRequest();
        request.setDbType(dbType);
        request.setHost(host);
        request.setPort(port);
        request.setUsername(username);
        request.setPassword(password);
        request.setDbName(dbName);

        File zipFile = generateBackupZip(request, false); // store temporarily

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));

        // Auto delete zip after some time (5s)
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                zipFile.delete();
                File parent = zipFile.getParentFile();
                if (parent.getName().startsWith("backup_")) {
                    Files.walk(parent.toPath())
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            } catch (Exception ignored) {}
        }).start();

        return new DownloadResponse(resource, zipFile.getName());
    }

}
