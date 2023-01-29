package com.tbdev.teaneckminyanim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class TeaneckMinyanimApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeaneckMinyanimApplication.class, args);
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }
}
