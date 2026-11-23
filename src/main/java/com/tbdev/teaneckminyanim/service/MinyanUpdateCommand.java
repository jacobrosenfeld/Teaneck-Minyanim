package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanDay;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.minyan.MinyanType;

import java.util.Map;

/**
 * A change to a minyan rule. Anything that is null (or, for {@code slots}, missing) keeps its current value.
 * For {@code notes} and {@code whatsapp}, a blank string clears the value.
 *
 * @param slots already-parsed schedule slots keyed by day; days not in the map keep their current time
 */
public record MinyanUpdateCommand(
        MinyanType type,
        String locationId,
        Nusach nusach,
        String notes,
        String whatsapp,
        Map<MinyanDay, MinyanTime> slots
) {
}
