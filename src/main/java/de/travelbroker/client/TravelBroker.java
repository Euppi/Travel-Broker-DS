// src/main/java/de/travelbroker/client/TravelBroker.java

package de.travelbroker.client;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.json.JSONObject;
import org.json.JSONArray;
import de.travelbroker.util.Config;
import de.travelbroker.util.Logger;
import de.travelbroker.util.Statistics;

import java.util.*;

public class TravelBroker {

    public static void main(String[] args) throws Exception {
        Config.loadConfig("src/main/resources/config.json");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5569");

            Logger.log("\uD83E\uDDE0 TravelBroker bereit und wartet auf Anfragen auf Port 5569...");

            while (!Thread.currentThread().isInterrupted()) {
                byte[] requestBytes = socket.recv(0);
                if (requestBytes == null) continue;

                String request = new String(requestBytes, ZMQ.CHARSET);
                Logger.log("\uD83D\uDCEC Anfrage empfangen: " + request);
                Statistics.incrementTotal();

                String[] parts = request.split(":");
                String customer = parts[0];
                List<String> hotels = Arrays.asList(parts[1].split(","));

                if (hasConsecutiveDuplicateHotels(hotels)) {
                    String msg = "\u274C Ung\u00fcltige Buchung von " + customer + ": gleiches Hotel kommt doppelt hintereinander vor.";
                    Logger.log(msg);
                    socket.send(msg.getBytes(ZMQ.CHARSET), 0);
                    Statistics.incrementFailed();
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
                        Logger.log("\uD83D\uDD01 [Retry " + attempt + "/" + Config.maxRetries + "] f\u00fcr " + hotel);
                        Logger.log("\u23F3 Sende Buchungsanfrage an " + hotel);

                        try (ZMQ.Socket hotelSocket = context.createSocket(SocketType.REQ)) {
                            hotelSocket.connect("tcp://localhost:" + hotelPort(hotel));
                            hotelSocket.send(msg.toString().getBytes(ZMQ.CHARSET), 0);

                            Logger.log("\uD83D\uDD52 Warte auf Antwort von " + hotel + " (max. " + Config.brokerResponseTimeoutMillis + "ms)...");
                            byte[] reply = hotelSocket.recv(Config.brokerResponseTimeoutMillis);

                            if (reply == null) {
                                Logger.log("\u26A0\uFE0F Timeout bei " + hotel + " nach " + Config.brokerResponseTimeoutMillis + "ms.");
                                if (attempt == Config.maxRetries) {
                                    Logger.log("\u26D4 Keine Antwort nach " + Config.maxRetries + " Versuchen – Buchung fehlgeschlagen.");
                                }
                                continue;
                            }

                            String replyStr = new String(reply, ZMQ.CHARSET);
                            if (replyStr.equalsIgnoreCase("confirmed")) {
                                Logger.log("\u2705 Best\u00e4tigt von " + hotel);
                                confirmed.add(hotel);
                                hotelSuccess = true;
                                break;
                            } else if (replyStr.equalsIgnoreCase("rejected")) {
                                Logger.log("\u274C Buchung abgelehnt von " + hotel);
                                break;
                            } else if (replyStr.equalsIgnoreCase("dropped")) {
                                Logger.log("⚠️ Antwort von " + hotel + ": dropped – wird ignoriert, retry folgt.");
                                // Kein break → Retry läuft weiter
                            } else {
                                Logger.log("❓ Unerwartete Antwort von " + hotel + ": " + replyStr);
                            }
                            
                        }
                    }

                    if (!hotelSuccess) {
                        failed = true;
                        break;
                    }
                }

                if (failed) {
                    sendRollback(context, confirmed, customer);
                    socket.send(("Buchung fehlgeschlagen f\u00fcr " + customer).getBytes(ZMQ.CHARSET), 0);
                    Statistics.incrementFailed();
                } else {
                    socket.send(("Buchung erfolgreich f\u00fcr " + customer).getBytes(ZMQ.CHARSET), 0);
                    Statistics.incrementSuccess();
                }

                Statistics.printSummary();
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
                Logger.log("\u21A9\uFE0F Rollback gesendet an " + hotel);
            }
        }
    }

    private static List<Integer> generateRandomTimeBlocks() {
        Random rand = new Random();
        int start = rand.nextInt(98);
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
}
