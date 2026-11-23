package com.tbdev.teaneckminyanim.api.dto.admin;

import com.tbdev.teaneckminyanim.minyan.MinyanDay;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.minyan.Schedule;
import com.tbdev.teaneckminyanim.model.Minyan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.EnumMap;
import java.util.Map;

/**
 * The 11 schedule slots of a minyan: the seven weekdays plus the Rosh Chodesh, Yom Tov, Chanuka and
 * Rosh Chodesh Chanuka overrides. In a PATCH request any slot may be left out (or null) to keep its current value.
 */
@Schema(description = "The 11 schedule slots. In a PATCH any slot may be omitted to keep its current value.")
public record MinyanScheduleDto(
        ScheduleSlotDto sunday,
        ScheduleSlotDto monday,
        ScheduleSlotDto tuesday,
        ScheduleSlotDto wednesday,
        ScheduleSlotDto thursday,
        ScheduleSlotDto friday,
        ScheduleSlotDto shabbos,
        ScheduleSlotDto roshChodesh,
        ScheduleSlotDto yomTov,
        ScheduleSlotDto chanuka,
        ScheduleSlotDto roshChodeshChanuka
) {
    /** Reads the stored schedule strings of a minyan (not the transient parsed schedule, which may be stale). */
    public static MinyanScheduleDto from(Minyan m) {
        Schedule s = safeSchedule(m);
        return new MinyanScheduleDto(
                ScheduleSlotDto.from(s.getSunday()), ScheduleSlotDto.from(s.getMonday()),
                ScheduleSlotDto.from(s.getTuesday()), ScheduleSlotDto.from(s.getWednesday()),
                ScheduleSlotDto.from(s.getThursday()), ScheduleSlotDto.from(s.getFriday()),
                ScheduleSlotDto.from(s.getShabbos()), ScheduleSlotDto.from(s.getRoshChodesh()),
                ScheduleSlotDto.from(s.getYomTov()), ScheduleSlotDto.from(s.getChanuka()),
                ScheduleSlotDto.from(s.getRoshChodeshChanuka()));
    }

    private static Schedule safeSchedule(Minyan m) {
        try {
            return new Schedule(m.getStartTime1(), m.getStartTime2(), m.getStartTime3(), m.getStartTime4(),
                    m.getStartTime5(), m.getStartTime6(), m.getStartTime7(), m.getStartTimeRC(), m.getStartTimeYT(),
                    m.getStartTimeCH(), m.getStartTimeCHRC());
        } catch (RuntimeException e) {
            return new Schedule(new MinyanTime(), new MinyanTime(), new MinyanTime(), new MinyanTime(), new MinyanTime(),
                    new MinyanTime(), new MinyanTime(), new MinyanTime(), new MinyanTime(), new MinyanTime(), new MinyanTime());
        }
    }

    /**
     * Validates the supplied slots and converts them. Slots that are null are not in the result (they keep
     * their current value).
     *
     * @throws IllegalArgumentException with a message that is safe to show to API clients
     */
    public Map<MinyanDay, MinyanTime> toSlots() {
        Map<MinyanDay, MinyanTime> slots = new EnumMap<>(MinyanDay.class);
        put(slots, MinyanDay.SUNDAY, "sunday", sunday);
        put(slots, MinyanDay.MONDAY, "monday", monday);
        put(slots, MinyanDay.TUESDAY, "tuesday", tuesday);
        put(slots, MinyanDay.WEDNESDAY, "wednesday", wednesday);
        put(slots, MinyanDay.THURSDAY, "thursday", thursday);
        put(slots, MinyanDay.FRIDAY, "friday", friday);
        put(slots, MinyanDay.SHABBOS, "shabbos", shabbos);
        put(slots, MinyanDay.ROSH_CHODESH, "roshChodesh", roshChodesh);
        put(slots, MinyanDay.YOM_TOV, "yomTov", yomTov);
        put(slots, MinyanDay.CHANUKA, "chanuka", chanuka);
        put(slots, MinyanDay.ROSH_CHODESH_CHANUKA, "roshChodeshChanuka", roshChodeshChanuka);
        return slots;
    }

    private static void put(Map<MinyanDay, MinyanTime> slots, MinyanDay day, String name, ScheduleSlotDto slot) {
        if (slot == null) {
            return;
        }
        try {
            slots.put(day, slot.toMinyanTime());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("schedule." + name + ": " + e.getMessage());
        }
    }
}
