package com.dbbackup.utility;

import org.springframework.core.io.Resource;

public class DownloadResponse {
    private final Resource resource;
    private final String filename;

    public DownloadResponse(Resource resource, String filename) {
        this.resource = resource;
        this.filename = filename;
    }

    public Resource getResource() {
        return resource;
    }

    public String getFilename() {
        return filename;
    }
}
