package com.tbdev.teaneckminyanim;

import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableJpaRepositories
@EnableScheduling
public class TeaneckMinyanimApplication {

    public static void main(String[] args) {
        // Set default timezone before starting application
        // This will be updated from settings after ApplicationContext is ready
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        
        ConfigurableApplicationContext context = SpringApplication.run(TeaneckMinyanimApplication.class, args);
        
        // Update timezone from settings after context is initialized
        ApplicationSettingsService settingsService = context.getBean(ApplicationSettingsService.class);
        TimeZone.setDefault(settingsService.getTimeZone());
    }
}
