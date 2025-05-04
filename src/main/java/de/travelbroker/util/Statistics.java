// src/main/java/de/travelbroker/util/Statistics.java

package de.travelbroker.util;

public class Statistics {

    private static int total = 0;
    private static int success = 0;
    private static int failed = 0;

    public static synchronized void incrementTotal() {
        total++;
    }

    public static synchronized void incrementSuccess() {
        success++;
    }

    public static synchronized void incrementFailed() {
        failed++;
    }

    public static void printSummary() {
        Logger.log("📊 Booking Summary:");
        Logger.log("Total Requests: " + total);
        Logger.log("✅ Successful:   " + success);
        Logger.log("❌ Failed:       " + failed);
        Logger.log("Success Rate:   " + (total > 0 ? (success * 100 / total) + "%" : "N/A"));
    }
}
