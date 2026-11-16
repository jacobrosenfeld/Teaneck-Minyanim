package com.tbdev.teaneckminyanim.service.calendar;

import com.tbdev.teaneckminyanim.model.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Maps free-text location names from imported calendars (the CSV "Location" column, or a
 * location name embedded in the event title) onto an organization's {@link Location} records.
 *
 * <p>Matching is done on a normalized form of both strings: lower case, punctuation and
 * apostrophes removed, whitespace collapsed, a leading "the" dropped, and common
 * transliteration variants folded together ("Bais/Beit/Beth" to "beis", "Midrash/Medresh" to
 * "medrash"). Lookups try, in order:
 * <ol>
 *   <li>an exact normalized match;</li>
 *   <li>the longest Location name contained (as whole words) in the imported text, e.g.
 *       "Main Sanctuary (upstairs)" matches "Main Sanctuary";</li>
 *   <li>the imported text contained in exactly one Location name, e.g. "Sanctuary" matches
 *       "Main Sanctuary" when no other Location contains that word.</li>
 * </ol>
 * Ambiguous matches resolve to no location rather than a guess.
 */
public final class ImportedLocationMatcher {

    /** Shortest normalized text allowed for a contained-name match, to avoid matching on noise. */
    private static final int MIN_CONTAINED_LENGTH = 3;

    /** Canonical token for each known transliteration variant. */
    private static final Map<String, String> TOKEN_ALIASES = Map.ofEntries(
            Map.entry("bais", "beis"),
            Map.entry("beit", "beis"),
            Map.entry("beth", "beis"),
            Map.entry("bet", "beis"),
            Map.entry("beys", "beis"),
            Map.entry("midrash", "medrash"),
            Map.entry("medresh", "medrash"),
            Map.entry("medrish", "medrash"),
            Map.entry("midrosh", "medrash"),
            Map.entry("hamidrash", "hamedrash"),
            Map.entry("hamedresh", "hamedrash"),
            Map.entry("hamedrish", "hamedrash"),
            Map.entry("sanctuery", "sanctuary"));

    private static final Pattern APOSTROPHES = Pattern.compile("['‘’`]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private ImportedLocationMatcher() {
    }

    /**
     * Normalize a location name for comparison. Returns an empty string for null/blank input.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lowered = text.toLowerCase(Locale.ROOT).replace("&", " and ");
        lowered = APOSTROPHES.matcher(lowered).replaceAll("");
        lowered = NON_ALNUM.matcher(lowered).replaceAll(" ").trim();
        if (lowered.isEmpty()) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        for (String token : lowered.split(" ")) {
            tokens.add(TOKEN_ALIASES.getOrDefault(token, token));
        }
        if (tokens.size() > 1 && "the".equals(tokens.get(0))) {
            tokens.remove(0);
        }
        return String.join(" ", tokens);
    }

    /**
     * Match imported location text (e.g. the CSV "Location" column) to one of the org's Locations.
     *
     * @return the matching Location, or empty when the text is blank, unmatched or ambiguous
     */
    public static Optional<Location> match(String locationText, Collection<Location> locations) {
        String needle = normalize(locationText);
        if (needle.isEmpty() || locations == null || locations.isEmpty()) {
            return Optional.empty();
        }

        for (Location location : locations) {
            if (location != null && needle.equals(normalize(location.getName()))) {
                return Optional.of(location);
            }
        }

        Optional<Location> nameInText = longestNameContainedIn(needle, locations);
        if (nameInText.isPresent()) {
            return nameInText;
        }

        if (needle.length() < MIN_CONTAINED_LENGTH) {
            return Optional.empty();
        }
        List<Location> textInName = locations.stream()
                .filter(location -> location != null && containsWords(normalize(location.getName()), needle))
                .toList();
        return textInName.size() == 1 ? Optional.of(textInName.get(0)) : Optional.empty();
    }

    /**
     * Find a Location whose name appears inside an event title, for calendars that put the
     * location in the title instead of the Location column (e.g. "Shacharis - Main Sanctuary").
     */
    public static Optional<Location> findInText(String text, Collection<Location> locations) {
        String haystack = normalize(text);
        if (haystack.isEmpty() || locations == null || locations.isEmpty()) {
            return Optional.empty();
        }
        return longestNameContainedIn(haystack, locations);
    }

    /**
     * Build a case-insensitive pattern that finds {@code phrase} inside raw (un-normalized) text,
     * tolerating punctuation/whitespace differences and the transliteration variants above.
     *
     * @return the pattern, or null if the phrase has no usable words
     */
    public static Pattern phrasePattern(String phrase) {
        String normalized = normalize(phrase);
        if (normalized.isEmpty()) {
            return null;
        }
        String body = Arrays.stream(normalized.split(" "))
                .map(ImportedLocationMatcher::tokenAlternation)
                .collect(Collectors.joining("[\\W_]+"));
        return Pattern.compile("(?<![A-Za-z0-9])" + body + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    }

    private static String tokenAlternation(String canonicalToken) {
        if ("and".equals(canonicalToken)) {
            return "(?:and|&)";
        }
        Set<String> variants = new LinkedHashSet<>();
        variants.add(canonicalToken);
        TOKEN_ALIASES.forEach((variant, canonical) -> {
            if (canonical.equals(canonicalToken)) {
                variants.add(variant);
            }
        });
        // Allow an optional apostrophe between letters, since normalize() strips them
        // ("womens" must still find "Women's").
        return variants.stream()
                .map(ImportedLocationMatcher::apostropheTolerant)
                .collect(Collectors.joining("|", "(?:", ")"));
    }

    private static String apostropheTolerant(String token) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            if (i > 0) {
                sb.append("['‘’`]?");
            }
            sb.append(Pattern.quote(String.valueOf(token.charAt(i))));
        }
        return sb.toString();
    }

    private static Optional<Location> longestNameContainedIn(String haystack, Collection<Location> locations) {
        Location best = null;
        int bestLength = -1;
        boolean tie = false;
        for (Location location : locations) {
            if (location == null) {
                continue;
            }
            String name = normalize(location.getName());
            if (name.length() < MIN_CONTAINED_LENGTH || !containsWords(haystack, name)) {
                continue;
            }
            if (name.length() > bestLength) {
                best = location;
                bestLength = name.length();
                tie = false;
            } else if (name.length() == bestLength && !name.equals(normalize(best.getName()))) {
                tie = true;
            }
        }
        return best == null || tie ? Optional.empty() : Optional.of(best);
    }

    private static boolean containsWords(String haystack, String needle) {
        return (" " + haystack + " ").contains(" " + needle + " ");
    }
}
