package com.tbdev.teaneckminyanim.calendar;

import com.tbdev.teaneckminyanim.minyan.MinyanType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CalendarNormalizer {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2})[:.]?(\\d{2})\\s*([AaPp][Mm]?)?");

    /**
     * Normalize a title/label by trimming, collapsing spaces, and normalizing punctuation
     */
    public String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\p{Punct}&&[^-]]", "")
                .toLowerCase();
    }

    /**
     * Normalize time string to LocalTime
     */
    public LocalTime normalizeTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }

        timeStr = timeStr.trim().toLowerCase();

        // Try standard formatters first
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("h:mm a"),
                DateTimeFormatter.ofPattern("h:mma"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("h:mm:ss a"),
                DateTimeFormatter.ofPattern("h:mm:ssa"),
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(timeStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        // Use regex pattern matching for more flexible parsing
        Matcher matcher = TIME_PATTERN.matcher(timeStr);
        if (matcher.find()) {
            try {
                int hours = Integer.parseInt(matcher.group(1));
                int minutes = Integer.parseInt(matcher.group(2));
                String ampm = matcher.group(3);

                if (ampm != null) {
                    ampm = ampm.toLowerCase().replaceAll("[^ap]", "");
                    if (ampm.equals("pm") && hours < 12) {
                        hours += 12;
                    } else if (ampm.equals("am") && hours == 12) {
                        hours = 0;
                    }
                }

                return LocalTime.of(hours, minutes);
            } catch (Exception e) {
                log.warn("Failed to parse time from regex match: {}", timeStr, e);
            }
        }

        log.warn("Could not parse time: {}", timeStr);
        return null;
    }

    /**
     * Infer MinyanType from title
     */
    public MinyanType inferMinyanType(String title) {
        if (title == null) {
            return null;
        }

        String normalized = normalizeTitle(title);

        if (normalized.contains("shachar") || normalized.contains("shachris") || 
            normalized.contains("morning") || normalized.contains("shacharis")) {
            return MinyanType.SHACHARIS;
        } else if (normalized.contains("mincha") || normalized.contains("afternoon")) {
            return MinyanType.MINCHA;
        } else if (normalized.contains("maariv") || normalized.contains("mariv") || 
                   normalized.contains("arvit") || normalized.contains("evening")) {
            return MinyanType.MAARIV;
        } else if (normalized.contains("selichos") || normalized.contains("selichot")) {
            return MinyanType.SELICHOS;
        } else if (normalized.contains("megila") || normalized.contains("megillah")) {
            return MinyanType.MEGILA_READING;
        }

        return null;
    }

    /**
     * Generate a fingerprint for deduplication
     * Based on org ID, date, normalized title, and normalized time
     */
    public String generateFingerprint(String orgId, String dateStr, String title, String timeStr) {
        String normalized = String.format("%s|%s|%s|%s",
                orgId,
                dateStr,
                normalizeTitle(title),
                timeStr);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            return Integer.toString(normalized.hashCode());
        }
    }
}
