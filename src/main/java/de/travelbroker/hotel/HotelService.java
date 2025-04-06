package de.travelbroker.hotel;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.io.FileInputStream;
import java.io.IOException;
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

        // Konfiguration laden
        Config.loadConfig("src/main/resources/config.json");
        loadLocalOverrides();

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
                    System.out.println("✅ Booking successful.");

                    String bookingId = extractBookingId(request);
                    List<Integer> blocks = Arrays.asList(10, 11); // TODO: Realistisch aus request parsen
                    activeBookings.put(bookingId, new Booking(bookingId, blocks));

                    // Verfügbarkeiten reduzieren
                    for (int block : blocks) {
                        availability[block]--;
                    }

                    receiver.send("confirmed");
                } else {
                    System.out.println("❌ No rooms available.");
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
            System.out.println("Interrupted during delay.");
            Thread.currentThread().interrupt();
        }
    }

    private static void loadLocalOverrides() {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream("config/hotel.properties"));
            Config.hotelErrorRate = Double.parseDouble(props.getProperty("crashRate", String.valueOf(Config.hotelErrorRate)));
            Config.hotelTimeoutRate = Double.parseDouble(props.getProperty("unconfirmedRate", String.valueOf(Config.hotelTimeoutRate)));
            Config.noRoomAvailableRate = 1.0 - Double.parseDouble(props.getProperty("successRate", String.valueOf(1.0 - Config.noRoomAvailableRate)));
            Config.bookingDelayMillis = Integer.parseInt(props.getProperty("averageProcessingTimeMs", String.valueOf(Config.bookingDelayMillis)));
        } catch (IOException e) {
            System.out.println("⚠️ Could not load fallback hotel.properties – using only config.json.");
        }
    }

    // Hilfsklasse für Buchungen
    private static class Booking {
        // Removed unused field bookingId
        List<Integer> timeBlocks;

        Booking(String id, List<Integer> blocks) {
            // Removed assignment to unused field bookingId
            this.timeBlocks = blocks;
        }
    }
}
