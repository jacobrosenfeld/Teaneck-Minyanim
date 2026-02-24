package com.tbdev.teaneckminyanim.tools;

import java.util.Date;
import java.util.Random;

public class IDGenerator {

    public static String generateID(Character prefix) {
        Long timeSinceEpoch = (new Date()).getTime();
        return "%s%s%s".formatted(prefix, timeSinceEpoch.toString().substring(5), random());
    }

    private static String random() {
        return String.valueOf(getRandomNumber());
    }

    private static int getRandomNumber() {
        Random random = new Random();
        return random.nextInt((9999 - 1000) + 1) + 1000;
    }
}
