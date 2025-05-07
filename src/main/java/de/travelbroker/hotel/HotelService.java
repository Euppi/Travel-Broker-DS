// src/main/java/de/travelbroker/hotel/HotelService.java

package de.travelbroker.hotel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.*;
import de.travelbroker.util.Config;

public class HotelService {

    // Zufallsgenerator für Fehlersimulation und Zeitverzögerung
    private static final Random rand = new Random();

    // Aktive Buchungen, um Rollbacks zu ermöglichen
    private static final Map<String, Booking> activeBookings = new HashMap<>();

    // Verfügbarkeiten für 100 Zeitblöcke (z. B. Kalenderwochen)
    private static final int[] availability = new int[100];

    static {
        Arrays.fill(availability, 5); // Jedes Zeitfenster hat 5 freie Zimmer
    }

    public static void main(String[] args) {
        // Hotelname über Kommandozeilenargument oder Standardwert
        String hotelName = args.length > 0 ? args[0] : "Hotel-A";

        // Portzuordnung abhängig vom Hotelnamen
        int port = switch (hotelName) {
            case "Hotel-A" -> 5556;
            case "Hotel-B" -> 5557;
            case "Hotel-C" -> 5558;
            default -> throw new IllegalArgumentException("Unknown Hotel name: " + hotelName);
        };

        // Konfiguration laden (z. B. Fehlerwahrscheinlichkeiten, Verzögerung)
        Config.loadConfig("src/main/resources/config.json");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket receiver = context.createSocket(SocketType.REP);
            receiver.bind("tcp://*:" + port);

            System.out.println(hotelName + " is running on port " + port + " with failure simulation...");

            // Hauptverarbeitungsschleife
            while (!Thread.currentThread().isInterrupted()) {
                String request = receiver.recvStr();
                if (request == null) continue;

                System.out.println("[" + hotelName + "] received: " + request);

                // Künstliche Verzögerung simulieren (z. B. Netzwerk-Latenz)
                simulateDelay();

                // Prüfe, ob es sich um eine Stornierung handelt
                if (request.contains("\"action\":\"cancel\"")) {
                    String bookingId = extractBookingId(request);
                    cancelBooking(bookingId);
                    receiver.send("cancelled");
                    continue;
                }

                // Fehlersimulation: z. B. keine Antwort, Antwort mit „dropped“, etc.
                double chance = rand.nextDouble();
                System.out.println("[" + hotelName + "] Random value (Failure Simulation): " + chance);

                if (chance < Config.hotelErrorRate) {
                    // Kein Response: Broker wird Timeout behandeln
                    System.out.println("[" + hotelName + "] Simulated crash: no response.");
                    continue;
                } else if (chance < Config.hotelErrorRate + Config.hotelTimeoutRate) {
                    // Gültige, aber bedeutungslose Antwort → Broker versucht Retry
                    System.out.println("[" + hotelName + "] Simulated drop: process without confirmation.");
                    receiver.send("dropped");
                    continue;
                }

                System.out.println("[" + hotelName + "] Checking availability of the time blocks...");

                // JSON parsen
                String bookingId = extractBookingId(request);
                JSONObject obj = new JSONObject(request);
                JSONArray jsonBlocks = obj.getJSONArray("timeBlocks");

                List<Integer> blocks = new ArrayList<>();
                for (int i = 0; i < jsonBlocks.length(); i++) {
                    blocks.add(jsonBlocks.getInt(i));
                }

                // Prüfen, ob alle gewünschten Zeitblöcke noch verfügbar sind
                boolean allAvailable = true;
                for (int block : blocks) {
                    if (availability[block] <= 0) {
                        allAvailable = false;
                        break;
                    }
                }

                if (allAvailable) {
                    // Verfügbarkeiten reduzieren & Buchung merken
                    for (int block : blocks) {
                        availability[block]--;
                    }
                    activeBookings.put(bookingId, new Booking(bookingId, blocks));
                    System.out.println("[" + hotelName + "] Booking successful for: " + bookingId);
                    receiver.send("confirmed");
                } else {
                    System.out.println("[" + hotelName + "] No rooms available for: " + bookingId);
                    receiver.send("rejected");
                }
            }
        }
    }

    /**
     * Führt einen Rollback (Stornierung) einer bestätigten Buchung durch.
     */
    private static void cancelBooking(String bookingId) {
        Booking booking = activeBookings.remove(bookingId);
        if (booking != null) {
            for (int block : booking.timeBlocks) {
                availability[block]++;
            }
            System.out.println("Rollback successful for " + bookingId);
        } else {
            System.out.println("No active booking for " + bookingId + " found.");
        }
    }

    /**
     * Extrahiert die Booking-ID aus dem JSON-Request.
     */
    private static String extractBookingId(String request) {
        int start = request.indexOf("bookingId\":\"") + 12;
        int end = request.indexOf("\"", start);
        return request.substring(start, end);
    }

    /**
     * Simuliert eine zufällige Netzwerkverzögerung (Gaussian verteilt).
     */
    private static void simulateDelay() {
        int delay = (int) (rand.nextGaussian() * 300 + Config.bookingDelayMillis);
        delay = Math.max(100, delay); // Mindestverzögerung 100ms
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            System.out.println("Interrupted during delay.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Innere Klasse zur Repräsentation einer aktiven Buchung.
     */
    private static class Booking {
        List<Integer> timeBlocks;

        Booking(String id, List<Integer> blocks) {
            this.timeBlocks = blocks;
        }
    }
}
