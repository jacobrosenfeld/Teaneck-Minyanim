package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.enums.MinyanClassification;
import com.tbdev.teaneckminyanim.enums.Zman;
import com.tbdev.teaneckminyanim.service.ZmanimHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for classifying imported calendar entries as minyan or non-minyan events.
 * Uses inclusive pattern matching with allow/deny lists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinyanClassifier {

    private final ZmanimHandler zmanimHandler;

    // Allowlist: Patterns that indicate a minyan event
    private static final Set<Pattern> MINYAN_PATTERNS = new HashSet<>();
    
    // Denylist: Patterns that indicate non-minyan events
    private static final Set<Pattern> NON_MINYAN_PATTERNS = new HashSet<>();
    
    // Combined Mincha/Maariv patterns
    private static final Set<Pattern> MINCHA_MAARIV_PATTERNS = new HashSet<>();
    
    static {
        // Combined Mincha/Maariv patterns (check FIRST - most specific)
        MINCHA_MAARIV_PATTERNS.add(Pattern.compile("mincha?h?\\s*[/&-]\\s*ma'?ariv", Pattern.CASE_INSENSITIVE));
        MINCHA_MAARIV_PATTERNS.add(Pattern.compile("mincha?h?\\s+and\\s+ma'?ariv", Pattern.CASE_INSENSITIVE));
        MINCHA_MAARIV_PATTERNS.add(Pattern.compile("mincha?h?\\s*[/&-]\\s*arvit", Pattern.CASE_INSENSITIVE));
        MINCHA_MAARIV_PATTERNS.add(Pattern.compile("mincha?h?\\s+and\\s+arvit", Pattern.CASE_INSENSITIVE));
        
        // Denylist patterns - case insensitive (check SECOND - explicit exclusions)
        // These patterns should be explicit to avoid false positives
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bdaf\\s+yomi\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bshiur\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\blecture\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bclass(?!ification)\\b", Pattern.CASE_INSENSITIVE)); // Exclude "classification"
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\blearning\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bstudy\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bkolel\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bgemara\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bchaburah?\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bdrasha?\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\btalk\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bmeeting\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bkiddush\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bseudah?\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bmelave\\s+malka\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bworkshop\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bseminar\\b", Pattern.CASE_INSENSITIVE));
        NON_MINYAN_PATTERNS.add(Pattern.compile("\\bcandle\\s+lighting\\b", Pattern.CASE_INSENSITIVE));
        
        // Allowlist patterns - case insensitive (check LAST - positive identification)
        // Shacharis variants
        MINYAN_PATTERNS.add(Pattern.compile("\\bshacharis\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bshacharit\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bshaharit\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bshachris\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bshachrith\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bteen\\s+minyan\\b", Pattern.CASE_INSENSITIVE));
        
        // Mincha variants
        MINYAN_PATTERNS.add(Pattern.compile("\\bmincha\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bminchah\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bminha\\b", Pattern.CASE_INSENSITIVE));
        
        // Maariv variants
        MINYAN_PATTERNS.add(Pattern.compile("\\bmaariv\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bma'ariv\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\barvit\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\barvis\\b", Pattern.CASE_INSENSITIVE));
        
        // Selichos variants
        MINYAN_PATTERNS.add(Pattern.compile("\\bselichos\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bselichot\\b", Pattern.CASE_INSENSITIVE));
        
        // Neitz/Sunrise variants
        MINYAN_PATTERNS.add(Pattern.compile("\\bneitz\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bnetz\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bsunrise\\s+minyan\\b", Pattern.CASE_INSENSITIVE));
        MINYAN_PATTERNS.add(Pattern.compile("\\bvasikin\\b", Pattern.CASE_INSENSITIVE));
        
    }

    /**
     * Classification result with reason
     */
    public static class ClassificationResult {
        public MinyanClassification classification;
        public String reason;
        public String notes;
        
        public ClassificationResult(MinyanClassification classification, String reason) {
            this.classification = classification;
            this.reason = reason;
            this.notes = null;
        }
        
        public ClassificationResult(MinyanClassification classification, String reason, String notes) {
            this.classification = classification;
            this.reason = reason;
            this.notes = notes;
        }
    }

    /**
     * Classify a calendar entry based on its title, type, and description.
     * 
     * @param title Entry title
     * @param type Entry type
     * @param description Entry description
     * @param date Entry date (for computing Shkiya for Mincha/Maariv)
     * @return Classification result with reason
     */
    public ClassificationResult classify(String title, String type, String description, LocalDate date) {
        // Combine all text fields for analysis
        String combinedText = combineFields(title, type, description);
        
        log.debug("Classifying entry: {}", combinedText);
        
        // Check for combined Mincha/Maariv first (most specific)
        for (Pattern pattern : MINCHA_MAARIV_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                // Generate Shkiya note and add any title qualifiers
                String notes = generateShkiyaNote(date);
                String titleQualifier = extractTitleQualifier(title);
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    notes = notes != null ? notes + ". " + titleQualifier : titleQualifier;
                }
                return new ClassificationResult(
                    MinyanClassification.MINCHA_MAARIV,
                    "Matched combined Mincha/Maariv pattern: " + pattern.pattern(),
                    notes
                );
            }
        }
        
        // Check denylist first (explicit non-minyan events)
        for (Pattern pattern : NON_MINYAN_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                return new ClassificationResult(
                    MinyanClassification.NON_MINYAN,
                    "Matched non-minyan pattern: " + pattern.pattern()
                );
            }
        }
        
        // Check allowlist (minyan events)
        for (Pattern pattern : MINYAN_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                // Extract title qualifiers for minyan events
                String titleQualifier = extractTitleQualifier(title);
                return new ClassificationResult(
                    MinyanClassification.MINYAN,
                    "Matched minyan pattern: " + pattern.pattern(),
                    titleQualifier
                );
            }
        }
        
        // Default to NON_MINYAN if no patterns match (conservative approach)
        return new ClassificationResult(
            MinyanClassification.NON_MINYAN,
            "No minyan pattern matched - defaulting to NON_MINYAN for safety"
        );
    }

    /**
     * Combine and normalize text fields for classification.
     */
    private String combineFields(String title, String type, String description) {
        StringBuilder combined = new StringBuilder();
        
        if (title != null && !title.trim().isEmpty()) {
            combined.append(title.trim()).append(" ");
        }
        if (type != null && !type.trim().isEmpty()) {
            combined.append(type.trim()).append(" ");
        }
        if (description != null && !description.trim().isEmpty()) {
            combined.append(description.trim());
        }
        
        return combined.toString();
    }

    /**
     * Generate a note with Shkiya (sunset) time for a given date.
     * Used for Mincha/Maariv combined entries.
     * 
     * @param date The date to compute Shkiya for
     * @return Formatted note string, or null if Shkiya cannot be computed
     */
    private String generateShkiyaNote(LocalDate date) {
        if (date == null) {
            return null;
        }
        
        try {
            Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(date);
            Date shkiya = zmanim.get(Zman.SHEKIYA);
            
            if (shkiya != null) {
                // Convert Date to LocalTime for formatting
                LocalTime shkiyaTime = shkiya.toInstant()
                    .atZone(java.time.ZoneId.of("America/New_York"))
                    .toLocalTime();
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
                String formattedTime = shkiyaTime.format(formatter);
                
                return "Shkiya: " + formattedTime;
            } else {
                log.warn("Unable to compute Shkiya for date: {}", date);
                return null;
            }
        } catch (Exception e) {
            log.error("Error computing Shkiya for date {}: {}", date, e.getMessage());
            return null;
        }
    }

    /**
     * Extract special qualifiers from title that should be preserved as notes.
     * Examples: "Teen Minyan" → "Teen", "Early Shacharis" → "Early"
     * 
     * @param title The entry title
     * @return Qualifier string or null if none found
     */
    private String extractTitleQualifier(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        
        String titleLower = title.toLowerCase().trim();
        List<String> qualifiers = new ArrayList<>();
        
        // List of meaningful qualifiers to extract
        String[] qualifierPatterns = {
            "teen", "youth", "young adult", "early", "late", 
            "fast", "quick", "express", "main", "second",
            "women's", "men's", "kollel", "vasikin", "hanetz"
        };
        
        for (String qualifier : qualifierPatterns) {
            // Use word boundaries to match whole words
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(qualifier) + "\\b", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(titleLower).find()) {
                // Preserve original casing
                int start = titleLower.indexOf(qualifier);
                if (start != -1) {
                    String originalQualifier = title.substring(start, start + qualifier.length());
                    qualifiers.add(originalQualifier);
                }
            }
        }
        
        // Return comma-separated qualifiers if any found
        return qualifiers.isEmpty() ? null : String.join(", ", qualifiers);
    }

    /**
     * Normalize title by removing redundant words that duplicate the classification.
     * For example, "Shacharit" in a Shacharis entry becomes redundant.
     * 
     * @param title Original title
     * @param classification Entry classification
     * @return Normalized title with redundant words removed
     */
    public String normalizeTitle(String title, MinyanClassification classification) {
        if (title == null || title.trim().isEmpty()) {
            return title;
        }
        
        String normalized = title.trim();
        
        // Remove redundant classification words
        if (classification == MinyanClassification.MINYAN || classification == MinyanClassification.MINCHA_MAARIV) {
            // Remove standalone minyan type words if they duplicate the classification
            normalized = normalized.replaceAll("(?i)\\b(shacharis?|shacharit|mincha|ma'?ariv|selichos?)\\b", "");
        }
        
        // Collapse multiple spaces
        normalized = normalized.replaceAll("\\s+", " ").trim();
        
        // Remove leading/trailing punctuation and whitespace
        normalized = normalized.replaceAll("^[\\s\\p{Punct}]+|[\\s\\p{Punct}]+$", "");
        
        // Return original if normalization resulted in empty string
        if (normalized.isEmpty()) {
            return title;
        }
        
        return normalized;
    }
}
