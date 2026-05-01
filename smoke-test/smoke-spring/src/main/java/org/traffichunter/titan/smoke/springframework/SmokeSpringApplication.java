package org.traffichunter.titan.smoke.springframework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.traffichunter.titan.springframework.stomp.annotation.EnableTitan;

@EnableTitan
@SpringBootApplication
public class SmokeSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmokeSpringApplication.class, args);
    }

}
