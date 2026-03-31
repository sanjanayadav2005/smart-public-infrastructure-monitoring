package com.smartinfra.smart_public_infra;

import com.smartinfra.smart_public_infra.config.AppStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppStorageProperties.class)
public class SmartPublicInfraApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPublicInfraApplication.class, args);
    }
}
