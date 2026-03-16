package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public-facing DTO for Jewish prayer times (zmanim) on a given date.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ZmanimDto(
        String date,           // "2026-03-15"
        String hebrewDate,     // "כ״ה אדר תשפ״ו"
        Times times
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Times(
            String alotHashachar,    // Alos Hashachar (dawn)
            String misheyakir,       // Misheyakir (earliest tallit/tefillin)
            String netz,             // Netz (sunrise)
            String sofZmanShmaGra,   // Sof Zman Shma GRA
            String sofZmanShmaMga,   // Sof Zman Shma MGA
            String sofZmanTfilaGra,  // Sof Zman Tfila GRA
            String sofZmanTfilaMga,  // Sof Zman Tfila MGA
            String chatzos,          // Chatzos (solar noon)
            String minchaGedola,     // Mincha Gedola
            String minchaKetana,     // Mincha Ketana
            String plagHamincha,     // Plag HaMincha
            String shekiya,          // Shekiya (sunset)
            String earliestShema,    // Earliest time to recite Shema at night
            String tzeis,            // Tzeis Hakochavim (nightfall)
            String chatzosLaila      // Chatzos Laila (midnight)
    ) {}
}
