package com.tbdev.teaneckminyanim.tools;

public class NumberTools {
    public static int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }
}