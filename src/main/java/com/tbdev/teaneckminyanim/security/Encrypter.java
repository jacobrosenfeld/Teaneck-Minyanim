package com.tbdev.teaneckminyanim.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Encrypter {
        public static String encrytedPassword(String password) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            return encoder.encode(password);
        }

        public static void main(String[] args) {
        }
}
