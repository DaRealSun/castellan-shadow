package com.sunhomelab.castellanshadow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CastellanShadowApplication {
    public static void main(String[] args) {
        SpringApplication.run(CastellanShadowApplication.class, args);
    }
}
