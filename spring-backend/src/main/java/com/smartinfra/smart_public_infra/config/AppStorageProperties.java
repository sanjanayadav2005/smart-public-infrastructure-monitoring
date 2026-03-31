package com.smartinfra.smart_public_infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppStorageProperties {

    /**
     * Folder where uploaded complaint images are stored.
     */
    private String uploadDir = "../static/uploads";

    /**
     * Folder containing the built frontend that should be served for the SPA.
     */
    private String frontendDir = "../static/frontend";

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getFrontendDir() {
        return frontendDir;
    }

    public void setFrontendDir(String frontendDir) {
        this.frontendDir = frontendDir;
    }
}
