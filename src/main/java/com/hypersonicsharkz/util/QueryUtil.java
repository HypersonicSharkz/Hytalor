package com.hypersonicsharkz.util;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public static String getExtension(String str){
        int begin = str.lastIndexOf(".");
        if(begin == -1)
            return null;
        String extension = str.substring(begin + 1);
        return extension;
    }

    public static String getFullPath(Path path) {
        FileSystem fs = path.getFileSystem();
        String zipFilePath = Paths.get(fs.toString()).toString();

        boolean zip = zipFilePath.endsWith(".jar") || zipFilePath.endsWith(".zip");

        return (zip ? (zipFilePath + "!") : "") + path;
    }
}
