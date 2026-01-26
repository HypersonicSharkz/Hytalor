package com.hypersonicsharkz.util;

import java.util.regex.Pattern;

public class QueryUtil {
    public static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);

            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++; // skip second *
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append('.');
                case '.' -> regex.append("\\.");
                case '/' -> regex.append("/");
                default -> regex.append(String.valueOf(c));
            }
        }

        regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
