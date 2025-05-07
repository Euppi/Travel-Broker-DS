// src/main/java/de/travelbroker/util/Logger.java

package de.travelbroker.util;

import java.io.File;
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

        try {
            File logFile = new File(LOG_FILE);
            // Ordner automatisch erstellen, falls nicht vorhanden
            logFile.getParentFile().mkdirs();

            try (FileWriter fw = new FileWriter(logFile, true)) {
                fw.write(fullMessage + System.lineSeparator());
            }
        } catch (IOException e) {
            System.out.println("Logger failed: " + e.getMessage());
        }
    }
}
