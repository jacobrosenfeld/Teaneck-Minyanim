package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Nusach;

/**
 * A change to a shul profile. Anything that is null keeps its current value. For the optional text fields
 * (address, website, whatsapp, calendar URL) a blank string clears the value; a blank slug generates one from
 * the shul name.
 */
public record ShulProfileCommand(
        String name,
        String address,
        String websiteUrl,
        Nusach nusach,
        String color,
        String slug,
        String whatsapp,
        String calendarUrl,
        Boolean useScrapedCalendar
) {
}
