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

    private static final Random rand = new Random();
    private static final Map<String, Booking> activeBookings = new HashMap<>();
    private static final int[] availability = new int[100]; // 100 Zeitblöcke (Wochen)

    static {
        Arrays.fill(availability, 5); // z.B. 5 Zimmer pro Woche
    }

    public static void main(String[] args) {
        String hotelName = args.length > 0 ? args[0] : "Hotel-A";
        int port = switch (hotelName) {
            case "Hotel-A" -> 5556;
            case "Hotel-B" -> 5557;
            case "Hotel-C" -> 5558;
            default -> throw new IllegalArgumentException("Unbekannter Hotelname: " + hotelName);
        };

        Config.loadConfig("src/main/resources/config.json");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket receiver = context.createSocket(SocketType.REP);
            receiver.bind("tcp://*:" + port);

            System.out.println(hotelName + " is running on port " + port + " with failure simulation...");

            while (!Thread.currentThread().isInterrupted()) {
                String request = receiver.recvStr();
                if (request == null) continue;

                System.out.println("📨 [" + hotelName + "] received: " + request);
                simulateDelay();

                if (request.contains("\"action\":\"cancel\"")) {
                    String bookingId = extractBookingId(request);
                    cancelBooking(bookingId);
                    receiver.send("cancelled");
                    continue;
                }

                double chance = rand.nextDouble();
                System.out.println("🔍 [" + hotelName + "] Zufallswert (Fehlersimulation): " + chance);

                if (chance < Config.hotelErrorRate) {
                    System.out.println("⚠️ [" + hotelName + "] Simulated crash: no response.");
                    continue;
                } else if (chance < Config.hotelErrorRate + Config.hotelTimeoutRate) {
                    System.out.println("⚠️ [" + hotelName + "] Simulated drop: process without confirmation.");
                    receiver.send("dropped"); // Wichtig: trotzdem eine Antwort senden
                    continue;
                }
                

                System.out.println("✅ [" + hotelName + "] Prüfe Verfügbarkeit der Zeitblöcke...");

                String bookingId = extractBookingId(request);
                JSONObject obj = new JSONObject(request);
                JSONArray jsonBlocks = obj.getJSONArray("timeBlocks");
                List<Integer> blocks = new ArrayList<>();
                for (int i = 0; i < jsonBlocks.length(); i++) {
                    blocks.add(jsonBlocks.getInt(i));
                }

                boolean allAvailable = true;
                for (int block : blocks) {
                    if (availability[block] <= 0) {
                        allAvailable = false;
                        break;
                    }
                }

                if (allAvailable) {
                    for (int block : blocks) {
                        availability[block]--;
                    }
                    activeBookings.put(bookingId, new Booking(bookingId, blocks));
                    System.out.println("✅ [" + hotelName + "] Booking successful for: " + bookingId);
                    receiver.send("confirmed");
                } else {
                    System.out.println("❌ [" + hotelName + "] No rooms available for: " + bookingId);
                    receiver.send("rejected");
                }
            }
        }
    }

    private static void cancelBooking(String bookingId) {
        Booking booking = activeBookings.remove(bookingId);
        if (booking != null) {
            for (int block : booking.timeBlocks) {
                availability[block]++;
            }
            System.out.println("🔁 Rollback erfolgreich für " + bookingId);
        } else {
            System.out.println("⚠️ Keine aktive Buchung für " + bookingId + " gefunden.");
        }
    }

    private static String extractBookingId(String request) {
        int start = request.indexOf("bookingId\":\"") + 12;
        int end = request.indexOf("\"", start);
        return request.substring(start, end);
    }

    private static void simulateDelay() {
        int delay = (int) (rand.nextGaussian() * 300 + Config.bookingDelayMillis);
        delay = Math.max(100, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            System.out.println("⚠️ Interrupted during delay.");
            Thread.currentThread().interrupt();
        }
    }

    private static class Booking {
        List<Integer> timeBlocks;

        Booking(String id, List<Integer> blocks) {
            this.timeBlocks = blocks;
        }
    }
}
