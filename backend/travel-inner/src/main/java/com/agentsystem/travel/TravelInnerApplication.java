package com.agentsystem.travel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TravelInnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelInnerApplication.class, args);
    }
}
