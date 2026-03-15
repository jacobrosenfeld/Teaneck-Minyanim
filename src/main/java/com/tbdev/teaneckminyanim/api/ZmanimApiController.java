package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.ZmanimDto;
import com.tbdev.teaneckminyanim.enums.Zman;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.ZmanimHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Map;
import java.util.TimeZone;

/**
 * Public REST API for Jewish prayer times (zmanim).
 *
 * GET /api/v1/zmanim?date=YYYY-MM-DD   → zmanim for a specific date
 * GET /api/v1/zmanim                   → zmanim for today (convenience)
 */
@RestController
@RequestMapping("/api/v1/zmanim")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
public class ZmanimApiController {

    private final ZmanimHandler zmanimHandler;
    private final ApplicationSettingsService settingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<ZmanimDto>> getZmanim(
            @RequestParam(required = false) String date) {

        LocalDate localDate;
        try {
            localDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_DATE", "Use ISO-8601 format: YYYY-MM-DD"));
        }

        Dictionary<Zman, Date> zmanim = zmanimHandler.getZmanim(localDate);
        String hebrewDate = zmanimHandler.getHebrewDate(
                Date.from(localDate.atStartOfDay(settingsService.getZoneId()).toInstant()));

        ZmanimDto dto = new ZmanimDto(
                localDate.toString(),
                hebrewDate,
                new ZmanimDto.Times(
                        fmt(zmanim.get(Zman.ALOS_HASHACHAR)),
                        fmt(zmanim.get(Zman.MISHEYAKIR)),
                        fmt(zmanim.get(Zman.NETZ)),
                        fmt(zmanim.get(Zman.SZKS)),
                        fmt(zmanim.get(Zman.MASZKS)),
                        fmt(zmanim.get(Zman.SZT)),
                        fmt(zmanim.get(Zman.MASZT)),
                        fmt(zmanim.get(Zman.CHATZOS)),
                        fmt(zmanim.get(Zman.MINCHA_GEDOLA)),
                        fmt(zmanim.get(Zman.MINCHA_KETANA)),
                        fmt(zmanim.get(Zman.PLAG_HAMINCHA)),
                        fmt(zmanim.get(Zman.SHEKIYA)),
                        fmt(zmanim.get(Zman.TZES)),
                        fmt(zmanim.get(Zman.CHATZOS_LAILA))
                )
        );

        return ResponseEntity.ok(ApiResponse.ok(dto, Map.of(
                "timezone", settingsService.getZoneId().getId(),
                "location", settingsService.getLocationName()
        )));
    }

    /** Format a Date to "HH:mm" in the app timezone, or null if the date is null. */
    private String fmt(Date d) {
        if (d == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone(settingsService.getZoneId()));
        return sdf.format(d);
    }
}
