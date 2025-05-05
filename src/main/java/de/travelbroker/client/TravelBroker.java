package de.travelbroker.client;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.json.JSONObject;
import org.json.JSONArray;
import de.travelbroker.util.Config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TravelBroker {

    public static void main(String[] args) throws Exception {
        // Konfiguration laden (inkl. maxRetries)
        Config.loadConfig("src/main/resources/config.json");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5569");

            log("🧠 TravelBroker bereit und wartet auf Anfragen auf Port 5569...");

            while (!Thread.currentThread().isInterrupted()) {
                byte[] requestBytes = socket.recv(0);
                if (requestBytes == null) continue;

                String request = new String(requestBytes, ZMQ.CHARSET);
                log("📥 Anfrage empfangen: " + request);

                String[] parts = request.split(":");
                String customer = parts[0];
                List<String> hotels = Arrays.asList(parts[1].split(","));

                if (hasConsecutiveDuplicateHotels(hotels)) {
                    String msg = "❌ Ungültige Buchung von " + customer + ": gleiches Hotel kommt doppelt hintereinander vor.";
                    log(msg);
                    socket.send(msg.getBytes(ZMQ.CHARSET), 0);
                    continue;
                }

                boolean failed = false;
                List<String> confirmed = new ArrayList<>();
                List<Integer> timeBlocks = generateRandomTimeBlocks();

                for (String hotel : hotels) {
                    String bookingId = customer + "_" + hotel;
                    JSONObject msg = new JSONObject();
                    msg.put("action", "book");
                    msg.put("bookingId", bookingId);
                    msg.put("timeBlocks", new JSONArray(timeBlocks));

                    boolean hotelSuccess = false;

                    for (int attempt = 1; attempt <= Config.maxRetries; attempt++) {
                        try (ZMQ.Socket hotelSocket = context.createSocket(SocketType.REQ)) {
                            hotelSocket.connect("tcp://localhost:" + hotelPort(hotel));

                            log("⏳ Versuche Buchung bei " + hotel + " (Versuch " + attempt + ")");
                            hotelSocket.send(msg.toString().getBytes(ZMQ.CHARSET), 0);

                            byte[] reply = hotelSocket.recv(2000); // 2s Timeout

                            if (reply == null) {
                                log("⚠️ Keine Antwort von " + hotel + " beim Versuch " + attempt);
                                continue;
                            }

                            String replyStr = new String(reply, ZMQ.CHARSET);
                            if (replyStr.equalsIgnoreCase("confirmed")) {
                                log("✅ Bestätigt von " + hotel);
                                confirmed.add(hotel);
                                hotelSuccess = true;
                                break;
                            } else if (replyStr.equalsIgnoreCase("rejected")) {
                                log("❌ Buchung abgelehnt von " + hotel);
                                break;
                            } else {
                                log("❓ Unerwartete Antwort von " + hotel + ": " + replyStr);
                            }
                        }
                    }

                    if (!hotelSuccess) {
                        log("❌ Buchung fehlgeschlagen bei " + hotel + " nach " + Config.maxRetries + " Versuchen");
                        failed = true;
                        break;
                    }
                }

                if (failed) {
                    sendRollback(context, confirmed, customer);
                    socket.send(("Buchung fehlgeschlagen für " + customer).getBytes(ZMQ.CHARSET), 0);
                } else {
                    socket.send(("Buchung erfolgreich für " + customer).getBytes(ZMQ.CHARSET), 0);
                }
            }
        }
    }

    private static boolean hasConsecutiveDuplicateHotels(List<String> hotels) {
        for (int i = 1; i < hotels.size(); i++) {
            if (hotels.get(i).equals(hotels.get(i - 1))) {
                return true;
            }
        }
        return false;
    }

    private static void sendRollback(ZContext context, List<String> hotels, String customer) {
        for (String hotel : hotels) {
            try (ZMQ.Socket cancel = context.createSocket(SocketType.REQ)) {
                cancel.connect("tcp://localhost:" + hotelPort(hotel));

                String bookingId = customer + "_" + hotel;
                JSONObject msg = new JSONObject();
                msg.put("action", "cancel");
                msg.put("bookingId", bookingId);

                cancel.send(msg.toString().getBytes(ZMQ.CHARSET), 0);
                cancel.recv(0);
                log("↩️ Rollback gesendet an " + hotel);
            }
        }
    }

    private static List<Integer> generateRandomTimeBlocks() {
        Random rand = new Random();
        int start = rand.nextInt(98); // 98 + 1 = max 99
        return Arrays.asList(start, start + 1);
    }

    private static int hotelPort(String name) {
        return switch (name) {
            case "Hotel-A" -> 5556;
            case "Hotel-B" -> 5557;
            case "Hotel-C" -> 5558;
            default -> 5559;
        };
    }

    private static void log(String message) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + ts + "] " + message);
    }
}
