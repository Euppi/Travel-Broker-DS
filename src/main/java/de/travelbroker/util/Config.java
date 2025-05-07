// src/main/java/de/travelbroker/util/Config.java

package de.travelbroker.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class Config {
    public static double hotelErrorRate = 0.2;
    public static double hotelTimeoutRate = 0.2;
    public static double noRoomAvailableRate = 0.1;
    public static int bookingDelayMillis = 500;
    public static int maxRetries = 2;
    public static int brokerResponseTimeoutMillis = 2000; // NEU

    public static void loadConfig(String path) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            ConfigFile config = mapper.readValue(new File(path), ConfigFile.class);
            hotelErrorRate = config.hotelErrorRate;
            hotelTimeoutRate = config.hotelTimeoutRate;
            noRoomAvailableRate = config.noRoomAvailableRate;
            bookingDelayMillis = config.bookingDelayMillis;
            maxRetries = config.maxRetries;
            brokerResponseTimeoutMillis = config.brokerResponseTimeoutMillis; // NEU
            System.out.println("Configuration loaded from: " + path);
        } catch (IOException e) {
            System.out.println("Configuration could not be loaded: Setting default values.");
        }
    }

    public static class ConfigFile {
        public double hotelErrorRate;
        public double hotelTimeoutRate;
        public double noRoomAvailableRate;
        public int bookingDelayMillis;
        public int maxRetries;
        public int brokerResponseTimeoutMillis; // NEU
    }
}
