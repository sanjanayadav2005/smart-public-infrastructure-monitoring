package com.smartinfra.smart_public_infra.controller;

import com.smartinfra.smart_public_infra.config.AppStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class SpaController {

    private final Path frontendIndex;

    public SpaController(AppStorageProperties storageProperties) {
        this.frontendIndex = Paths.get(storageProperties.getFrontendDir())
                .toAbsolutePath()
                .normalize()
                .resolve("index.html");
    }

    @GetMapping({"/", "/report-issue", "/admin/login", "/admin/dashboard"})
    public ResponseEntity<Resource> index() {
        Resource resource = new FileSystemResource(frontendIndex);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}
