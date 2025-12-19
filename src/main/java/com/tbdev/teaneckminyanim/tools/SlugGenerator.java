package com.tbdev.teaneckminyanim.tools;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class SlugGenerator {
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern EDGESDHASHES = Pattern.compile("(^-|-$)");

    /**
     * Generates a URL-friendly slug from the given input string.
     * Converts to lowercase, replaces spaces with hyphens, removes special characters.
     * 
     * @param input The string to convert to a slug
     * @return A URL-safe slug string
     */
    public static String generateSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = EDGESDHASHES.matcher(slug).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }

    /**
     * Generates a unique slug by appending a number if needed.
     * 
     * @param baseSlug The base slug to start with
     * @param counter The counter to append (if > 0)
     * @return A slug with counter appended if counter > 0
     */
    public static String generateUniqueSlug(String baseSlug, int counter) {
        if (counter <= 0) {
            return baseSlug;
        }
        return baseSlug + "-" + counter;
    }
}
