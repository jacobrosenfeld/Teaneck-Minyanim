package com.tbdev.teaneckminyanim.calendar;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CalendarSyncResult {
    private String organizationId;
    private String organizationName;
    private boolean success;
    private String errorMessage;
    private int entriesAdded;
    private int entriesUpdated;
    private int entriesDisabled;
    private int entriesSkipped;
    private LocalDateTime syncTime;
}
