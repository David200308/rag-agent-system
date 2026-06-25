package com.agentsystem.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationInnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationInnerApplication.class, args);
    }
}
