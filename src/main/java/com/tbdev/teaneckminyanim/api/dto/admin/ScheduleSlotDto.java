package com.tbdev.teaneckminyanim.api.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.enums.Zman;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One schedule slot of a minyan, as read and written by the admin API.
 * <ul>
 *   <li>{@code {"type":"FIXED","time":"06:30"}}: a fixed 24-hour time</li>
 *   <li>{@code {"type":"DYNAMIC","zman":"NETZ","offsetMinutes":-5}}: a zman plus an offset</li>
 *   <li>{@code {"type":"ROUNDED","zman":"PLAG_HAMINCHA","offsetMinutes":-10}}: the zman plus an offset, aligned across the week to 5 minutes</li>
 *   <li>{@code {"type":"NONE"}}: no minyan on that day</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A schedule slot: FIXED (time), DYNAMIC or ROUNDED (zman + offsetMinutes) or NONE.")
public record ScheduleSlotDto(
        @Schema(description = "Slot kind", example = "DYNAMIC") SlotType type,
        @Schema(description = "24-hour HH:mm. Only for FIXED.", example = "06:30") String time,
        @Schema(description = "Zman name. Only for DYNAMIC and ROUNDED.", example = "NETZ") String zman,
        @Schema(description = "Minutes after (positive) or before (negative) the zman, from -240 to 240. Only for DYNAMIC and ROUNDED. Defaults to 0.",
                example = "-5") Integer offsetMinutes
) {
    public enum SlotType { FIXED, DYNAMIC, ROUNDED, NONE }

    public static final int MAX_OFFSET_MINUTES = 240;
    private static final Pattern HH_MM = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$");

    /** Reads a stored slot. Stored values are {@code T h:m:s:ms}, {@code R<ZMAN>:<offset>}, {@code Q<ZMAN>:<offset>} or {@code NM}. */
    public static ScheduleSlotDto from(MinyanTime minyanTime) {
        String raw = minyanTime == null ? "NM" : minyanTime.toString();
        try {
            if (raw.startsWith("T")) {
                String[] parts = raw.substring(1).split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return new ScheduleSlotDto(SlotType.FIXED, String.format("%02d:%02d", hours, minutes), null, null);
            }
            if (raw.startsWith("R") || raw.startsWith("Q")) {
                String[] parts = raw.substring(1).split(":");
                SlotType type = raw.startsWith("R") ? SlotType.DYNAMIC : SlotType.ROUNDED;
                return new ScheduleSlotDto(type, null, Zman.fromString(parts[0]).name(), Integer.parseInt(parts[1]));
            }
        } catch (RuntimeException e) {
            // Unreadable stored value: report it as no minyan rather than failing the whole response.
        }
        return new ScheduleSlotDto(SlotType.NONE, null, null, null);
    }

    /**
     * Validates this slot and converts it to a {@link MinyanTime}.
     *
     * @throws IllegalArgumentException with a message that is safe to show to API clients
     */
    public MinyanTime toMinyanTime() {
        if (type == null) {
            throw new IllegalArgumentException("Each schedule slot needs a type (FIXED, DYNAMIC, ROUNDED or NONE).");
        }
        switch (type) {
            case NONE:
                return new MinyanTime();
            case FIXED: {
                Matcher matcher = time == null ? null : HH_MM.matcher(time.trim());
                if (matcher == null || !matcher.matches()) {
                    throw new IllegalArgumentException("A FIXED slot needs a time in HH:mm (24-hour) format.");
                }
                return new MinyanTime("T" + Integer.parseInt(matcher.group(1)) + ":" + Integer.parseInt(matcher.group(2)) + ":0:0");
            }
            default: {
                if (zman == null || zman.isBlank()) {
                    throw new IllegalArgumentException("A " + type + " slot needs a zman.");
                }
                Zman parsed;
                try {
                    parsed = Zman.fromString(zman.trim());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown zman. Use one of: " + validZmanim() + ".");
                }
                int offset = offsetMinutes == null ? 0 : offsetMinutes;
                if (offset < -MAX_OFFSET_MINUTES || offset > MAX_OFFSET_MINUTES) {
                    throw new IllegalArgumentException("offsetMinutes must be between -" + MAX_OFFSET_MINUTES + " and " + MAX_OFFSET_MINUTES + ".");
                }
                return new MinyanTime((type == SlotType.ROUNDED ? "Q" : "R") + parsed.name() + ":" + offset);
            }
        }
    }

    private static String validZmanim() {
        StringBuilder names = new StringBuilder();
        for (Zman value : Zman.values()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(value.name());
        }
        return names.toString();
    }
}
