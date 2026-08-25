package com.agentsystem.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StorageInnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageInnerApplication.class, args);
    }
}
