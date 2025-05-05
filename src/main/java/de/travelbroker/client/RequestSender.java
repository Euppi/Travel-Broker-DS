// src/main/java/de/travelbroker/client/RequestSender.java

package de.travelbroker.client;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RequestSender {

    // Liste von Beispielkunden, die Buchungen durchführen
    private static final List<String> CUSTOMERS = Arrays.asList(
        "Alice", "Bob", "Clara", "David", "Ella", "Finn", "Greta", "Hannes", "Ida", "Jonas"
    );

    // Liste der verfügbaren Hotels
    private static final List<String> HOTELS = Arrays.asList("Hotel-A", "Hotel-B", "Hotel-C");

    // Zufallsgenerator für zufällige Auswahl
    private static final Random rand = new Random();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting booking request sender...");

        // Erstelle einen neuen ZeroMQ-Kontext
        try (ZContext context = new ZContext()) {

            // Erstelle einen REQ-Socket (Request) für Kommunikation mit dem Broker
            try (ZMQ.Socket socket = context.createSocket(ZMQ.REQ)) {
                // Verbinde mit dem Broker auf localhost:5569
                socket.connect("tcp://localhost:5569");

                // Schleife über alle Kunden
                for (String customer : CUSTOMERS) {
                    // Kopiere die Hotelliste und mische sie zufällig
                    List<String> hotels = new ArrayList<>(HOTELS);
                    Collections.shuffle(hotels);

                    // Wähle zufällig 2 oder 3 Hotels aus (ohne Duplikate)
                    hotels = hotels.subList(0, rand.nextInt(2) + 2); // Auswahl: 2 oder 3

                    // In 10 % der Fälle ein Duplikat des ersten Hotels hinzufügen (Fehlersimulation)
                    if (rand.nextDouble() < 0.1) {
                        hotels.add(hotels.get(0)); // bewusst doppelt
                    }

                    // Erstelle die Buchungsanfrage im Format "Kunde:Hotel1,Hotel2,..."
                    String request = customer + ":" + String.join(",", hotels);
                    log("Sending booking for: " + customer);

                    // Sende die Anfrage an den Broker
                    socket.send(request.getBytes(ZMQ.CHARSET), 0);

                    // Warte auf Antwort vom Broker (Timeout 3 Sekunden)
                    byte[] replyBytes = socket.recv(3000);

                    if (replyBytes != null) {
                        // Antwort erhalten: dekodieren und ausgeben
                        String reply = new String(replyBytes, ZMQ.CHARSET);
                        log("Reply from broker: " + reply);
                    } else {
                        // Keine Antwort erhalten
                        log("!ATTENTION! No reply from broker (timeout)");
                    }

                    // Warte 1 Sekunde, bevor die nächste Anfrage gesendet wird
                    Thread.sleep(1000);
                }

                log("Alle Buchungen abgeschlossen.");
            }
        }
    }

    /**
     * Gibt eine formatierte Lognachricht mit Zeitstempel aus.
     */
    private static void log(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + time + "] " + message);
    }
}
