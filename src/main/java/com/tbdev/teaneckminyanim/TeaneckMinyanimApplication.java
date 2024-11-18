package com.tbdev.teaneckminyanim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication
@EnableJpaRepositories
public class TeaneckMinyanimApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeaneckMinyanimApplication.class, args);
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }
}
