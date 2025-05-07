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
        // Konfigurationsdatei laden (z. B. Zeitlimits, Retry-Zahl)
        Config.loadConfig("src/main/resources/config.json");

        // ZeroMQ-Kontext öffnen
        try (ZContext context = new ZContext()) {
            // REPLY-Socket (Broker wartet auf Anfragen vom Client)
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5569");

            Logger.log("TravelBroker bereit und wartet auf Anfragen auf Port 5569...");

            // Hauptverarbeitungsschleife
            while (!Thread.currentThread().isInterrupted()) {
                // Anfrage empfangen
                byte[] requestBytes = socket.recv(0);
                if (requestBytes == null) continue;

                String request = new String(requestBytes, ZMQ.CHARSET);
                Logger.log("Anfrage empfangen: " + request);
                Statistics.incrementTotal();

                // Anfrage aufsplitten in Kunde und Hotelliste
                String[] parts = request.split(":");
                String customer = parts[0];
                List<String> hotels = Arrays.asList(parts[1].split(","));

                // Prüfe auf direkt aufeinanderfolgende doppelte Hotels
                if (hasConsecutiveDuplicateHotels(hotels)) {
                    String msg = "Ungültige Buchung von " + customer + ": gleiches Hotel kommt doppelt hintereinander vor.";
                    Logger.log(msg);
                    socket.send(msg.getBytes(ZMQ.CHARSET), 0);
                    Statistics.incrementFailed();
                    continue;
                }

                boolean failed = false;
                List<String> confirmed = new ArrayList<>();
                List<Integer> timeBlocks = generateRandomTimeBlocks(); // z. B. [23, 24]

                // Anfrage an alle Hotels schicken
                for (String hotel : hotels) {
                    String bookingId = customer + "_" + hotel;

                    // JSON-Buchungsnachricht erstellen
                    JSONObject msg = new JSONObject();
                    msg.put("action", "book");
                    msg.put("bookingId", bookingId);
                    msg.put("timeBlocks", new JSONArray(timeBlocks));

                    boolean hotelSuccess = false;

                    // Wiederholungsversuche bei Fehlern/Timeouts
                    for (int attempt = 1; attempt <= Config.maxRetries; attempt++) {
                        Logger.log("[Retry " + attempt + "/" + Config.maxRetries + "] für " + hotel);
                        Logger.log("Sende Buchungsanfrage an " + hotel);

                        try (ZMQ.Socket hotelSocket = context.createSocket(SocketType.REQ)) {
                            hotelSocket.connect("tcp://localhost:" + hotelPort(hotel));
                            hotelSocket.send(msg.toString().getBytes(ZMQ.CHARSET), 0);

                            Logger.log("Warte auf Antwort von " + hotel + " (max. " + Config.brokerResponseTimeoutMillis + "ms)...");
                            byte[] reply = hotelSocket.recv(Config.brokerResponseTimeoutMillis);

                            // Keine Antwort erhalten (Timeout)
                            if (reply == null) {
                                Logger.log("Timeout bei " + hotel + " nach " + Config.brokerResponseTimeoutMillis + "ms.");
                                if (attempt == Config.maxRetries) {
                                    Logger.log("Keine Antwort nach " + Config.maxRetries + " Versuchen – Buchung fehlgeschlagen.");
                                }
                                continue;
                            }

                            // Antwort erhalten
                            String replyStr = new String(reply, ZMQ.CHARSET);
                            if (replyStr.equalsIgnoreCase("confirmed")) {
                                Logger.log("Bestätigt von " + hotel);
                                confirmed.add(hotel);
                                hotelSuccess = true;
                                break;
                            } else if (replyStr.equalsIgnoreCase("rejected")) {
                                Logger.log("Buchung abgelehnt von " + hotel);
                                break;
                            } else if (replyStr.equalsIgnoreCase("dropped")) {
                                Logger.log("Antwort von " + hotel + ": dropped – wird ignoriert, retry folgt.");
                                // keine break-Anweisung → Retry wird fortgesetzt
                            } else {
                                Logger.log("Unerwartete Antwort von " + hotel + ": " + replyStr);
                            }
                        }
                    }

                    // Wenn Buchung bei diesem Hotel fehlschlägt → Abbruch
                    if (!hotelSuccess) {
                        failed = true;
                        break;
                    }
                }

                // Wenn ein Hotel fehlschlug → Rollback an alle vorher bestätigten Hotels
                if (failed) {
                    sendRollback(context, confirmed, customer);
                    socket.send(("Buchung fehlgeschlagen für " + customer).getBytes(ZMQ.CHARSET), 0);
                    Statistics.incrementFailed();
                } else {
                    // Erfolg für alle Hotels
                    socket.send(("Buchung erfolgreich für " + customer).getBytes(ZMQ.CHARSET), 0);
                    Statistics.incrementSuccess();
                }

                // Statistik ausgeben
                Statistics.printSummary();
            }
        }
    }

    /**
     * Prüft, ob zwei gleiche Hotels direkt hintereinander gebucht werden sollen.
     */
    private static boolean hasConsecutiveDuplicateHotels(List<String> hotels) {
        for (int i = 1; i < hotels.size(); i++) {
            if (hotels.get(i).equals(hotels.get(i - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Führt Rollback durch für bereits bestätigte Buchungen.
     */
    private static void sendRollback(ZContext context, List<String> hotels, String customer) {
        for (String hotel : hotels) {
            try (ZMQ.Socket cancel = context.createSocket(SocketType.REQ)) {
                cancel.connect("tcp://localhost:" + hotelPort(hotel));

                String bookingId = customer + "_" + hotel;
                JSONObject msg = new JSONObject();
                msg.put("action", "cancel");
                msg.put("bookingId", bookingId);

                cancel.send(msg.toString().getBytes(ZMQ.CHARSET), 0);
                cancel.recv(0); // Warten auf Bestätigung
                Logger.log("Rollback gesendet an " + hotel);
            }
        }
    }

    /**
     * Erzeugt zufällige Zeitblöcke (z. B. [15, 16]) für die Buchung.
     */
    private static List<Integer> generateRandomTimeBlocks() {
        Random rand = new Random();
        int start = rand.nextInt(98); // z. B. 0–97
        return Arrays.asList(start, start + 1);
    }

    /**
     * Gibt den Port für das jeweilige Hotel zurück.
     */
    private static int hotelPort(String name) {
        return switch (name) {
            case "Hotel-A" -> 5556;
            case "Hotel-B" -> 5557;
            case "Hotel-C" -> 5558;
            default -> 5559; // Fallback-Port
        };
    }
}
