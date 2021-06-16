package com.dwj.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author daiwj
 * @date 2021/06/03
 * @description:
 */
public class UnicodeUtil {
    public static void main(String[] args) throws IOException {

        List<String> list = Files.readAllLines(Paths.get("E:\\Java\\IDEWorkspase\\ConfigCenter\\src\\main\\resources\\application.yml"));
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File("C:\\Users\\28934\\Desktop\\classes\\application.yml")))) {
            for (String s : list) {
                writer.println(unicodeToString(s));
            }
            writer.flush();
        }

    }

    /**
     * unicode编码中文转换
     * @param s
     * @return
     */
    public static String unicodeToString(String s) {
        String[] split = s.split("\\\\");

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < split.length; i++) {

            if (split[i].startsWith("u")) {
                builder.append((char) Integer.parseInt(split[i].substring(1, 5), 16));
                if (split[i].length() > 5) {
                    builder.append(split[i].substring(5));
                }
            } else {
                builder.append(split[i]);
            }

        }
        return builder.toString();
    }
}
