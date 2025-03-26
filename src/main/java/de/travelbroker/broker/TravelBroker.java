package de.travelbroker.broker;

import de.travelbroker.messaging.MessageSender;

import java.io.FileInputStream;
import de.travelbroker.util.Logger;
import de.travelbroker.util.Statistics;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class TravelBroker {

    private final List<String> hotelEndpoints = Arrays.asList(
        "tcp://localhost:5556",
        "tcp://localhost:5557"
    );

    private final Properties config = new Properties();
    private int numberOfBookings = 5;
    private long delayBetweenRequestsMs = 2000;

    public void start() {
        loadConfig();
        log("Travel Broker started. Simulating " + numberOfBookings + " bookings every " + delayBetweenRequestsMs + " ms.");

        for (int i = 1; i <= numberOfBookings; i++) {
            Logger.log("🔁 Starting booking request #" + i);
        Statistics.incrementTotal();
            simulateBooking(i);
            try {
                Thread.sleep(delayBetweenRequestsMs);
            } catch (InterruptedException e) {
                log("Simulation interrupted.");
                break;
            }
        }
    }

    private void simulateBooking(int bookingId) {
        ExecutorService executor = Executors.newFixedThreadPool(hotelEndpoints.size());
        Map<String, Future<String>> futures = new LinkedHashMap<>();

        for (String endpoint : hotelEndpoints) {
            Future<String> future = executor.submit(() -> {
                try (MessageSender sender = new MessageSender(endpoint)) {
                    return sender.send("book#" + bookingId);
                } catch (Exception e) {
                    return "error:" + e.getMessage();
                }
            });
            futures.put(endpoint, future);
        }

        Map<String, Boolean> bookingStatus = new LinkedHashMap<>();
        boolean allSuccessful = true;

        for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
            String endpoint = entry.getKey();
            try {
                String result = entry.getValue().get(3, TimeUnit.SECONDS); // Timeout nach 3s
                log("Response from " + endpoint + ": " + result);
                if ("confirmed".equalsIgnoreCase(result)) {
                    bookingStatus.put(endpoint, true);
                } else {
                    bookingStatus.put(endpoint, false);
                    allSuccessful = false;
                }
            } catch (Exception e) {
                log("⚠️ Timeout or error at " + endpoint + ": " + e.getMessage());
                bookingStatus.put(endpoint, false);
                allSuccessful = false;
            }
        }

        executor.shutdownNow();
        Statistics.printSummary();

        if (allSuccessful) {
            Logger.log("✅ Booking #" + bookingId + " fully confirmed.");
            Statistics.incrementSuccess();
        } else {
            Statistics.incrementFailed();
            triggerRollback(bookingStatus);
        }
    }

    private void triggerRollback(Map<String, Boolean> bookingStatus) {
        log("🔁 Triggering rollback for confirmed bookings...");
        for (Map.Entry<String, Boolean> entry : bookingStatus.entrySet()) {
            if (entry.getValue()) {
                log("↩️ Rolling back booking at: " + entry.getKey());
            }
        }
        log("❗ Booking process rolled back.");
    }

    private void loadConfig() {
        try {
            config.load(new FileInputStream("config/broker.properties"));
            numberOfBookings = Integer.parseInt(config.getProperty("numberOfBookings", "5"));
            delayBetweenRequestsMs = Long.parseLong(config.getProperty("delayBetweenRequestsMs", "2000"));
        } catch (Exception e) {
            log("⚠️ Could not load broker config. Using defaults.");
        }
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + timestamp + "] " + message);
    }
}
