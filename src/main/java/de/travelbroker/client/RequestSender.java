// src/main/java/de/travelbroker/client/RequestSender.java

package de.travelbroker.client;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RequestSender {

    private static final List<String> CUSTOMERS = Arrays.asList(
        "Alice", "Bob", "Clara", "David", "Ella", "Finn", "Greta", "Hannes", "Ida", "Jonas"
    );
    private static final List<String> HOTELS = Arrays.asList("Hotel-A", "Hotel-B", "Hotel-C");
    private static final Random rand = new Random();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("📤 Starting booking request sender...");

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(ZMQ.REQ);
            socket.connect("tcp://localhost:5569");

            for (String customer : CUSTOMERS) {
                // zufällige Hotelkombination mit 2 oder 3 Hotels (ohne doppelte)
                List<String> hotels = new ArrayList<>(HOTELS);
                Collections.shuffle(hotels);
                hotels = hotels.subList(0, rand.nextInt(2) + 2); // 2 oder 3 Hotels

                // In 10% der Fälle füge ein doppeltes Hotel ein (zum Testen der Duplikatprüfung)
                if (rand.nextDouble() < 0.1) {
                    hotels.add(hotels.get(0));
                }

                String request = customer + ":" + String.join(",", hotels);
                log("📨 Sending booking for: " + customer);
                socket.send(request.getBytes(ZMQ.CHARSET), 0);

                byte[] replyBytes = socket.recv(3000); // 3s Timeout
                if (replyBytes != null) {
                    String reply = new String(replyBytes, ZMQ.CHARSET);
                    log("📥 Reply from broker: " + reply);
                } else {
                    log("⚠️ No reply from broker (timeout)");
                }

                Thread.sleep(1000); // Warte 1 Sekunde bis zur nächsten Anfrage
            }

            log("✅ Alle Buchungen abgeschlossen.");
        }
    }

    private static void log(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + time + "] " + message);
    }
}
