package com.smartinfra.smart_public_infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppStorageProperties storageProperties;

    public WebConfig(AppStorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadsPath = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path frontendPath = Paths.get(storageProperties.getFrontendDir()).toAbsolutePath().normalize();

        registry.addResourceHandler("/static/uploads/**")
                .addResourceLocations(uploadsPath.toUri().toString());

        // Serves frontend assets such as /assets/*.js and /assets/*.css.
        registry.addResourceHandler("/**")
                .addResourceLocations(frontendPath.toUri().toString());
    }
}
