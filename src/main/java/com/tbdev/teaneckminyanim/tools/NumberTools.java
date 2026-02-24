package com.tbdev.teaneckminyanim.tools;

import java.util.concurrent.ThreadLocalRandom;

public class NumberTools {
    public static int getRandomNumber(int min, int max) {
        return (int) ((ThreadLocalRandom.current().nextDouble() * (max - min)) + min);
    }
}