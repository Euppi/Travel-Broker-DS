package de.travelbroker.util;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private static final String LOG_FILE = "logs/broker.log";

    public static void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String fullMessage = "[" + timestamp + "] " + message;
        System.out.println(fullMessage);
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(fullMessage + System.lineSeparator());
        } catch (IOException e) {
            System.out.println("⚠️ Logger failed: " + e.getMessage());
        }
    }
}
