package com.tbdev.teaneckminyanim.migration;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.tools.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time migration script to generate URL slugs for existing organizations.
 * 
 * This script will:
 * 1. Find all organizations without a URL slug
 * 2. Generate a slug from the organization name
 * 3. Ensure slug uniqueness by appending numbers if needed
 * 4. Save the organization with the new slug
 * 
 * To run this migration, add the following to application.properties:
 *   migration.generate-slugs.enabled=true
 * 
 * After running once, set it back to false or remove the property to prevent re-running.
 * 
 * Usage:
 *   1. Add to application.properties: migration.generate-slugs.enabled=true
 *   2. Start the application: mvn spring-boot:run
 *   3. Check the logs for migration results
 *   4. Set migration.generate-slugs.enabled=false (or remove the property)
 *   5. Restart the application
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "migration.generate-slugs.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class GenerateOrganizationSlugsRunner implements CommandLineRunner {

    private final OrganizationService organizationService;

    @Override
    public void run(String... args) {
        log.info("=================================================================");
        log.info("Starting migration: Generate URL slugs for existing organizations");
        log.info("=================================================================");

        try {
            List<Organization> allOrganizations = organizationService.getAll();
            int totalOrgs = allOrganizations.size();
            int updatedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            log.info("Found {} organization(s) in database", totalOrgs);

            for (Organization org : allOrganizations) {
                try {
                    // Check if organization already has a slug
                    if (org.getUrlSlug() != null && !org.getUrlSlug().trim().isEmpty()) {
                        log.info("  [SKIP] Organization '{}' (ID: {}) already has slug: '{}'", 
                                org.getName(), org.getId(), org.getUrlSlug());
                        skippedCount++;
                        continue;
                    }

                    // Generate slug from organization name
                    String baseSlug = SlugGenerator.generateSlug(org.getName());
                    
                    if (baseSlug.isEmpty()) {
                        log.warn("  [ERROR] Could not generate slug for organization '{}' (ID: {})", 
                                org.getName(), org.getId());
                        errorCount++;
                        continue;
                    }

                    // Ensure uniqueness
                    String uniqueSlug = organizationService.generateUniqueSlug(baseSlug);
                    
                    // Update organization with slug
                    org.setUrlSlug(uniqueSlug);
                    
                    // Save to database
                    boolean saved = organizationService.update(org);
                    
                    if (saved) {
                        log.info("  [OK] Generated slug for '{}' (ID: {}): '{}'", 
                                org.getName(), org.getId(), uniqueSlug);
                        updatedCount++;
                    } else {
                        log.error("  [ERROR] Failed to save slug for organization '{}' (ID: {})", 
                                org.getName(), org.getId());
                        errorCount++;
                    }
                    
                } catch (Exception e) {
                    log.error("  [ERROR] Exception processing organization '{}' (ID: {}): {}", 
                            org.getName(), org.getId(), e.getMessage(), e);
                    errorCount++;
                }
            }

            log.info("=================================================================");
            log.info("Migration completed!");
            log.info("  Total organizations: {}", totalOrgs);
            log.info("  Updated: {}", updatedCount);
            log.info("  Skipped (already had slug): {}", skippedCount);
            log.info("  Errors: {}", errorCount);
            log.info("=================================================================");
            
            if (updatedCount > 0) {
                log.info("");
                log.info("SUCCESS! {} organization(s) now have URL slugs.", updatedCount);
                log.info("");
                log.info("NEXT STEPS:");
                log.info("1. Set 'migration.generate-slugs.enabled=false' in application.properties");
                log.info("2. Restart the application to prevent re-running this migration");
                log.info("");
            }

        } catch (Exception e) {
            log.error("=================================================================");
            log.error("Migration FAILED with exception: {}", e.getMessage(), e);
            log.error("=================================================================");
        }
    }
}
