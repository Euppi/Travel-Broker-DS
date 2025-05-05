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
        int port = hotelName.equals("Hotel-A") ? 5556 : hotelName.equals("Hotel-B") ? 5557 : 5558;

        // Nur noch config.json laden
        Config.loadConfig("src/main/resources/config.json");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket receiver = context.createSocket(SocketType.REP);
            receiver.bind("tcp://*:" + port);

            System.out.println(hotelName + " is running on port " + port + " with failure simulation...");

            while (!Thread.currentThread().isInterrupted()) {
                String request = receiver.recvStr();
                if (request == null) continue;

                System.out.println(hotelName + " received: " + request);
                simulateDelay();

                // --- Rollback-Nachricht erkennen ---
                if (request.contains("\"action\":\"cancel\"")) {
                    String bookingId = extractBookingId(request);
                    cancelBooking(bookingId);
                    receiver.send("cancelled");
                    continue;
                }

                // --- Buchung simulieren ---
                double chance = rand.nextDouble();

                if (chance < Config.hotelErrorRate) {
                    System.out.println("⚠️ Simulated crash: no response.");
                    continue;
                } else if (chance < Config.hotelErrorRate + Config.hotelTimeoutRate) {
                    System.out.println("⚠️ Simulated drop: process without confirmation.");
                    continue;
                } else if (chance < Config.hotelErrorRate + Config.hotelTimeoutRate + (1.0 - Config.noRoomAvailableRate)) {
                    System.out.println("✅ Prüfe Verfügbarkeit der Zeitblöcke...");

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
                        System.out.println("✅ Booking successful.");
                        receiver.send("confirmed");
                    } else {
                        System.out.println("❌ No rooms available.");
                        receiver.send("rejected");
                    }
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
            System.out.println("Interrupted during delay.");
            Thread.currentThread().interrupt();
        }
    }

    // Hilfsklasse für Buchungen
    private static class Booking {
        List<Integer> timeBlocks;

        Booking(String id, List<Integer> blocks) {
            this.timeBlocks = blocks;
        }
    }
}
