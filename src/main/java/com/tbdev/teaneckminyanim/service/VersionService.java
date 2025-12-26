package com.tbdev.teaneckminyanim.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
@Service
public class VersionService {
    
    private String version;
    
    public VersionService() {
        this.version = loadVersionFromMavenProperties();
    }
    
    private String loadVersionFromMavenProperties() {
        Properties properties = new Properties();
        String pomPropertiesPath = "/META-INF/maven/com.tbdev/Teaneck-Minyanim/pom.properties";
        
        try (InputStream inputStream = getClass().getResourceAsStream(pomPropertiesPath)) {
            if (inputStream != null) {
                properties.load(inputStream);
                String version = properties.getProperty("version");
                if (version != null) {
                    // Remove -SNAPSHOT suffix for display
                    return version.replace("-SNAPSHOT", "");
                }
            }
        } catch (IOException e) {
            // Log the error but don't throw - return default version
            log.warn("Could not load version from pom.properties: {}", e.getMessage());
        }
        
        // Fallback to reading from package if pom.properties not available (dev mode)
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion().replace("-SNAPSHOT", "");
        }
        
        // Ultimate fallback
        return "1.1.1";
    }
    
    public String getVersion() {
        return version;
    }
}
