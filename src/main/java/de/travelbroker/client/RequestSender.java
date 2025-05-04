// src/main/java/de/travelbroker/client/RequestSender.java

package de.travelbroker.client;

import org.zeromq.ZMQ;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class RequestSender {

    private static final String BROKER_ADDRESS = "tcp://localhost:5569";

    public static void main(String[] args) throws Exception {
        System.out.println("📤 Starting booking request sender...");

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> bookings = mapper.readValue(
                new File("src/main/resources/testdata.json"),
                new TypeReference<>() {}
        );

        // ZContext statt veralteter ZMQ.context()
        try (ZContext context = new ZContext()) {
            ZMQ.Socket sender = context.createSocket(SocketType.REQ);
            sender.connect(BROKER_ADDRESS);

            for (Map<String, Object> booking : bookings) {
                String customer = (String) booking.get("customerName");
                List<String> hotels = mapper.convertValue(booking.get("hotels"), new TypeReference<List<String>>() {});
                String payload = customer + ":" + String.join(",", hotels);

                log("📨 Sending booking for: " + customer);
                sender.send(payload.getBytes(StandardCharsets.UTF_8), 0);

                byte[] replyBytes = sender.recv(0);
                String reply = new String(replyBytes, StandardCharsets.UTF_8);
                log("📥 Reply from broker: " + reply);
            }
        }

        System.out.println("✅ Done sending all requests.");
    }

    private static void log(String message) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + ts + "] " + message);
    }
}
