package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.minyan.MinyanType;
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

    // Denylist: Patterns that indicate non-minyan events
    private static final Set<Pattern> NON_MINYAN_PATTERNS = new HashSet<>();
    
    // Combined Mincha/Maariv patterns
    private static final Set<Pattern> MINCHA_MAARIV_PATTERNS = new HashSet<>();
    
    // Shacharis patterns
    private static final Set<Pattern> SHACHARIS_PATTERNS = new HashSet<>();
    
    // Mincha patterns
    private static final Set<Pattern> MINCHA_PATTERNS = new HashSet<>();
    
    // Maariv patterns
    private static final Set<Pattern> MAARIV_PATTERNS = new HashSet<>();
    
    // Selichos patterns
    private static final Set<Pattern> SELICHOS_PATTERNS = new HashSet<>();
    
    // Netz Hachama patterns (sunrise minyanim with special zman note)
    private static final Set<Pattern> NETZ_PATTERNS = new HashSet<>();
    
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
        
        // Shacharis patterns (Shacharis and sunrise-related minyanim)
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bshacharis\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bshacharit\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bshaharit\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bshachris\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bshachrith\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bneitz\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bnetz\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bsunrise\\s+minyan\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bvasikin\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bteen\\s+minyan\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bShc\\b", Pattern.CASE_INSENSITIVE));
        SHACHARIS_PATTERNS.add(Pattern.compile("\\bShac\\b", Pattern.CASE_INSENSITIVE));


        // Mincha patterns (including early mincha)
        MINCHA_PATTERNS.add(Pattern.compile("\\bearly\\s+mincha\\b", Pattern.CASE_INSENSITIVE));
        MINCHA_PATTERNS.add(Pattern.compile("\\bmincha\\b", Pattern.CASE_INSENSITIVE));
        MINCHA_PATTERNS.add(Pattern.compile("\\bminchah\\b", Pattern.CASE_INSENSITIVE));
        MINCHA_PATTERNS.add(Pattern.compile("\\bminha\\b", Pattern.CASE_INSENSITIVE));
        MINCHA_PATTERNS.add(Pattern.compile("\\bMnc\\b", Pattern.CASE_INSENSITIVE));
        
        // Maariv patterns
        MAARIV_PATTERNS.add(Pattern.compile("\\bmaariv\\b", Pattern.CASE_INSENSITIVE));
        MAARIV_PATTERNS.add(Pattern.compile("\\bma'ariv\\b", Pattern.CASE_INSENSITIVE));
        MAARIV_PATTERNS.add(Pattern.compile("\\barvit\\b", Pattern.CASE_INSENSITIVE));
        MAARIV_PATTERNS.add(Pattern.compile("\\barvis\\b", Pattern.CASE_INSENSITIVE));
        
        // Selichos patterns
        SELICHOS_PATTERNS.add(Pattern.compile("\\bselichos\\b", Pattern.CASE_INSENSITIVE));
        SELICHOS_PATTERNS.add(Pattern.compile("\\bselichot\\b", Pattern.CASE_INSENSITIVE));
        
        // Netz Hachama patterns (sunrise minyanim - classified as Shacharis with Netz time in notes)
        NETZ_PATTERNS.add(Pattern.compile("\\bvasikin\\b", Pattern.CASE_INSENSITIVE));

    }

    /**
     * Classification result with reason
     */
    public static class ClassificationResult {
        public MinyanType classification;
        public String reason;
        public String notes;
        
        public ClassificationResult(MinyanType classification, String reason) {
            this.classification = classification;
            this.reason = reason;
            this.notes = null;
        }
        
        public ClassificationResult(MinyanType classification, String reason, String notes) {
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
     * @param time Entry time (for NS abbreviation classification)
     * @return Classification result with reason
     */
    public ClassificationResult classify(String title, String type, String description, LocalDate date, LocalTime time) {
        // Combine all text fields for analysis
        String combinedText = combineFields(title, type, description);
        
        log.debug("Classifying entry: {}", combinedText);
        
        // Check for "NS" (Nusach Sefard) in title - track this for later note addition
        boolean hasNS = title != null && title.matches("(?i).*\\bNS\\b.*");
        boolean isNSBeforeNoon = hasNS && time != null && time.isBefore(LocalTime.NOON);
        
        // If NS is in title AND time is before 12pm, force classification as Shacharis
        if (isNSBeforeNoon) {
            String notes = "Nusach Sefard";
            return new ClassificationResult(
                MinyanType.SHACHARIS,
                "Matched NS abbreviation before 12pm - classified as Shacharis",
                notes
            );
        }
        
        // Check for Netz Hachama patterns (sunrise minyanim) - high priority for Shacharis
        for (Pattern pattern : NETZ_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                String netzNote = generateNetzHachamaNote(date);
                String titleQualifier = extractTitleQualifier(title);
                // Remove Netz-related qualifiers (hanetz, vasikin, netz, neitz, sunrise) to avoid duplication
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    titleQualifier = titleQualifier.replaceAll("(?i)\\b(hanetz|vasikin|netz|neitz|sunrise)\\b,?\\s*", "").trim();
                }
                // Add NS note if present
                if (hasNS) {
                    netzNote = netzNote != null ? netzNote + ". Nusach Sefard" : "Nusach Sefard";
                }
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    netzNote = netzNote != null ? netzNote + ". " + titleQualifier : titleQualifier;
                }
                return new ClassificationResult(
                    MinyanType.SHACHARIS,
                    "Matched Netz Hachama pattern: " + pattern.pattern(),
                    netzNote
                );
            }
        }
        
        // Check for combined Mincha/Maariv first (most specific)
        for (Pattern pattern : MINCHA_MAARIV_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                // Generate Shkiya note and add any title qualifiers
                String notes = generateShkiyaNote(date);
                String titleQualifier = extractTitleQualifier(title);
                // Add NS note if present
                if (hasNS) {
                    notes = notes != null ? notes + ". Nusach Sefard" : "Nusach Sefard";
                }
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    notes = notes != null ? notes + ". " + titleQualifier : titleQualifier;
                }
                return new ClassificationResult(
                    MinyanType.MINCHA_MAARIV,
                    "Matched combined Mincha/Maariv pattern: " + pattern.pattern(),
                    notes
                );
            }
        }
        
        // Check denylist (explicit non-minyan events)
        for (Pattern pattern : NON_MINYAN_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                return new ClassificationResult(
                    MinyanType.NON_MINYAN,
                    "Matched non-minyan pattern: " + pattern.pattern()
                );
            }
        }
        
        // Check specific minyan types (in order of specificity)
        
        // Check Selichos
        for (Pattern pattern : SELICHOS_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                String titleQualifier = extractTitleQualifier(title);
                String notes = null;
                // Add NS note if present
                if (hasNS) {
                    notes = "Nusach Sefard";
                }
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    notes = notes != null ? notes + ". " + titleQualifier : titleQualifier;
                }
                return new ClassificationResult(
                    MinyanType.SELICHOS,
                    "Matched Selichos pattern: " + pattern.pattern(),
                    notes
                );
            }
        }
        
        // Check Shacharis (including sunrise minyanim)
        for (Pattern pattern : SHACHARIS_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                String titleQualifier = extractTitleQualifier(title);
                String notes = null;
                // Add NS note if present
                if (hasNS) {
                    notes = "Nusach Sefard";
                }
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    notes = notes != null ? notes + ". " + titleQualifier : titleQualifier;
                }
                return new ClassificationResult(
                    MinyanType.SHACHARIS,
                    "Matched Shacharis pattern: " + pattern.pattern(),
                    notes
                );
            }
        }
        
        // Check Mincha (separate from combined Mincha/Maariv)
        for (Pattern pattern : MINCHA_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                String titleQualifier = extractTitleQualifier(title);
                String notes = null;
                // Add NS note if present
                if (hasNS) {
                    notes = "Nusach Sefard";
                }
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    notes = notes != null ? notes + ". " + titleQualifier : titleQualifier;
                }
                return new ClassificationResult(
                    MinyanType.MINCHA,
                    "Matched Mincha pattern: " + pattern.pattern(),
                    notes
                );
            }
        }
        
        // Check Maariv (separate from combined Mincha/Maariv)
        for (Pattern pattern : MAARIV_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                String titleQualifier = extractTitleQualifier(title);
                String notes = null;
                // Add NS note if present
                if (hasNS) {
                    notes = "Nusach Sefard";
                }
                if (titleQualifier != null && !titleQualifier.isEmpty()) {
                    notes = notes != null ? notes + ". " + titleQualifier : titleQualifier;
                }
                return new ClassificationResult(
                    MinyanType.MAARIV,
                    "Matched Maariv pattern: " + pattern.pattern(),
                    notes
                );
            }
        }
        
        // Default to NON_MINYAN if no patterns match (conservative approach)
        // But still add NS note if present
        String notes = null;
        if (hasNS) {
            notes = "Nusach Sefard";
        }
        return new ClassificationResult(
            MinyanType.NON_MINYAN,
            "No minyan pattern matched - defaulting to NON_MINYAN for safety",
            notes
        );
    }

    /**
     * Classify a calendar entry based on its title, type, and description.
     * Convenience method without time parameter for backward compatibility.
     * 
     * @param title Entry title
     * @param type Entry type
     * @param description Entry description
     * @param date Entry date (for computing Shkiya for Mincha/Maariv)
     * @return Classification result with reason
     */
    public ClassificationResult classify(String title, String type, String description, LocalDate date) {
        return classify(title, type, description, date, null);
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
     * Generate a note with Netz Hachama (sunrise) time for a given date.
     * Used for Netz minyan entries.
     * 
     * @param date The date to compute Netz Hachama for
     * @return Formatted note string, or null if Netz cannot be computed
     */
    private String generateNetzHachamaNote(LocalDate date) {
        if (date == null) {
            return null;
        }
        
        try {
            Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(date);
            Date netz = zmanim.get(Zman.NETZ);
            
            if (netz != null) {
                // Convert Date to LocalTime for formatting
                LocalTime netzTime = netz.toInstant()
                    .atZone(java.time.ZoneId.of("America/New_York"))
                    .toLocalTime();
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
                String formattedTime = netzTime.format(formatter);
                
                return "Netz Hachama: " + formattedTime + " Vasikin";
            } else {
                log.warn("Unable to compute Netz Hachama for date: {}", date);
                return null;
            }
        } catch (Exception e) {
            log.error("Error computing Netz Hachama for date {}: {}", date, e.getMessage());
            return null;
        }
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
     * Examples: "Teen Minyan" → "Teen Minyan", "Early Shacharis" → "Early Shacharis"
     * "NS Minyan" → "Nusach Sefard"
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
        
        // Check for NS abbreviation (Nusach Sefard)
        Pattern nsPattern = Pattern.compile("\\bNS\\b", Pattern.CASE_INSENSITIVE);
        if (nsPattern.matcher(title).find()) {
            qualifiers.add("Nusach Sefard");
        }
        
        // Multi-word phrases to extract (check these first)
        String[] multiWordPatterns = {
            "teen minyan", "youth minyan", "young adult minyan",
            "early shacharis", "early shacharit", "late shacharis", "late shacharit",
            "early mincha", "late mincha", "fast mincha",
            "early maariv", "late maariv",
            "women's minyan", "men's minyan"
        };
        
        for (String phrase : multiWordPatterns) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(titleLower).find()) {
                // Preserve original casing by finding the phrase in the original title
                int start = titleLower.indexOf(phrase.toLowerCase());
                if (start != -1) {
                    String originalPhrase = title.substring(start, start + phrase.length());
                    qualifiers.add(originalPhrase);
                }
            }
        }
        
        // If no multi-word patterns matched (and no NS found), check single-word qualifiers
        if (qualifiers.isEmpty()) {
            String[] singleWordPatterns = {
                "teen", "youth", "early", "late", 
                "fast", "quick", "express", "main", "second",
                "kollel", "vasikin", "hanetz"
            };
            
            for (String qualifier : singleWordPatterns) {
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
    public String normalizeTitle(String title, MinyanType classification) {
        if (title == null || title.trim().isEmpty()) {
            return title;
        }
        
        String normalized = title.trim();
        
        // Remove redundant classification words for minyan types
        if (classification.isMinyan()) {
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
