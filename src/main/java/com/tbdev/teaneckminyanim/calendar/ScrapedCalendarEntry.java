package com.tbdev.teaneckminyanim.calendar;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class ScrapedCalendarEntry {
    private LocalDate date;
    private String title;
    private LocalTime time;
    private String rawText;
    private String sourceUrl;
}
