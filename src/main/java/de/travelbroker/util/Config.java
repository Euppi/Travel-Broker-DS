package de.travelbroker.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class Config {
    public static double hotelErrorRate = 0.2;
    public static double hotelTimeoutRate = 0.2;
    public static double noRoomAvailableRate = 0.1;
    public static int bookingDelayMillis = 500;

    public static void loadConfig(String path) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            ConfigFile config = mapper.readValue(new File(path), ConfigFile.class);
            hotelErrorRate = config.hotelErrorRate;
            hotelTimeoutRate = config.hotelTimeoutRate;
            noRoomAvailableRate = config.noRoomAvailableRate;
            bookingDelayMillis = config.bookingDelayMillis;
            System.out.println("✅ Konfiguration geladen aus: " + path);
        } catch (IOException e) {
            System.out.println("⚠️ Konfiguration konnte nicht geladen werden – Standardwerte werden verwendet.");
        }
    }

    // Hilfsklasse für JSON-Parsing
    public static class ConfigFile {
        public double hotelErrorRate;
        public double hotelTimeoutRate;
        public double noRoomAvailableRate;
        public int bookingDelayMillis;
    }
}
