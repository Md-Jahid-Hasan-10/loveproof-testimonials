package com.loveproof.testimonials;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the LoveProof API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LoveProofApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoveProofApplication.class, args);
    }
}
