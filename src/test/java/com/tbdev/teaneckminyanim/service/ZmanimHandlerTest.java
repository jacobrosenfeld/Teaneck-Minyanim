package com.tbdev.teaneckminyanim.service;

import com.kosherjava.zmanim.util.GeoLocation;
import com.tbdev.teaneckminyanim.enums.Zman;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Date;
import java.util.Dictionary;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZmanimHandlerTest {

    private final ZmanimHandler handler = new ZmanimHandler(new GeoLocation(
            "Teaneck, NJ",
            40.8976,
            -74.0159,
            0,
            TimeZone.getTimeZone("America/New_York")));

    @Test
    void getZmanim_addsCandleLightingOnErevShabbosOnly() {
        Dictionary<Zman, Date> friday = handler.getZmanim(LocalDate.of(2026, 6, 5));

        assertNotNull(friday.get(Zman.CANDLE_LIGHTING));
        assertTrue(friday.get(Zman.CANDLE_LIGHTING).before(friday.get(Zman.SHEKIYA)));
        assertNull(friday.get(Zman.HAVDALA));

        Dictionary<Zman, Date> sunday = handler.getZmanim(LocalDate.of(2026, 6, 7));

        assertNull(sunday.get(Zman.CANDLE_LIGHTING));
        assertNull(sunday.get(Zman.HAVDALA));
    }

    @Test
    void getZmanim_addsHavdalaOnMotzeiShabbos() {
        Dictionary<Zman, Date> shabbos = handler.getZmanim(LocalDate.of(2026, 6, 6));

        assertNull(shabbos.get(Zman.CANDLE_LIGHTING));
        assertEquals(shabbos.get(Zman.TZES), shabbos.get(Zman.HAVDALA));
    }

    @Test
    void getZmanim_usesYomTovCandleLightingDaysFromJewishCalendar() {
        Dictionary<Zman, Date> firstDayPesach = handler.getZmanim(LocalDate.of(2026, 4, 2));

        assertEquals(firstDayPesach.get(Zman.TZES), firstDayPesach.get(Zman.CANDLE_LIGHTING));
        assertNull(firstDayPesach.get(Zman.HAVDALA));
    }

    @Test
    void getZmanim_keepsYomTovFridayCandleLightingBeforeSunset() {
        Dictionary<Zman, Date> firstDayShavuos = handler.getZmanim(LocalDate.of(2026, 5, 22));

        assertNotNull(firstDayShavuos.get(Zman.CANDLE_LIGHTING));
        assertTrue(firstDayShavuos.get(Zman.CANDLE_LIGHTING).before(firstDayShavuos.get(Zman.SHEKIYA)));
        assertNull(firstDayShavuos.get(Zman.HAVDALA));
    }

    @Test
    void getZmanim_showsHavdalaForShabbosIntoYomTov() {
        Dictionary<Zman, Date> firstDayRoshHashana = handler.getZmanim(LocalDate.of(2026, 9, 12));

        assertEquals(firstDayRoshHashana.get(Zman.TZES), firstDayRoshHashana.get(Zman.CANDLE_LIGHTING));
        assertEquals(firstDayRoshHashana.get(Zman.TZES), firstDayRoshHashana.get(Zman.HAVDALA));
    }
}
