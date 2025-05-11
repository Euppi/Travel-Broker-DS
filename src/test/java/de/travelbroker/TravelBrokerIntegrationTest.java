// src/test/java/de/travelbroker/TravelBrokerIntegrationTest.java

package de.travelbroker;

import de.travelbroker.client.TravelBroker;
import de.travelbroker.hotel.HotelService;
import org.junit.jupiter.api.*;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TravelBrokerIntegrationTest {

    private ZContext context;
    private ZMQ.Socket socket;
    private final List<Thread> serviceThreads = new ArrayList<>();

    @BeforeAll
    public void setup() throws InterruptedException {
        System.out.println("Starting HotelServices...");

        for (String hotel : List.of("Hotel-A", "Hotel-B", "Hotel-C")) {
            Thread serviceThread = new Thread(() -> HotelService.main(new String[]{hotel}), hotel + "-Service");
            serviceThread.start();
            serviceThreads.add(serviceThread);
            Thread.sleep(500); // small pause between starts
        }

        System.out.println("Starting TravelBroker...");
        Thread brokerThread = new Thread(() -> {
            try {
                TravelBroker.main(new String[0]);
            } catch (Exception e) {
                throw new RuntimeException("Broker could not be started", e);
            }
        }, "TravelBroker-Thread");
        brokerThread.start();
        serviceThreads.add(brokerThread);

        // Wait until everything is ready
        Thread.sleep(3000);

        context = new ZContext();
        socket = context.createSocket(SocketType.REQ);
        socket.connect("tcp://localhost:5569");

        System.out.println("ZeroMQ successfully connected.");
    }

    @AfterAll
    public void teardown() {
        System.out.println("Stopping services...");
        if (socket != null) socket.close();
        if (context != null) context.close();

        for (Thread t : serviceThreads) {
            if (t != null && t.isAlive()) {
                t.interrupt();
            }
        }

        System.out.println("Testing environment stopped.");
    }

    private String sendBookingRequest(String customer, List<String> hotels) {
        try (ZContext threadContext = new ZContext()) {
            ZMQ.Socket threadSocket = threadContext.createSocket(SocketType.REQ);
            threadSocket.connect("tcp://localhost:5569");

            String payload = customer + ":" + String.join(",", hotels);
            System.out.println("[" + customer + "] sending booking: " + payload);
            threadSocket.send(payload.getBytes(StandardCharsets.UTF_8), 0);

            byte[] replyBytes = threadSocket.recv(0);
            String response = new String(replyBytes, StandardCharsets.UTF_8);
            System.out.println("[" + customer + "] reply: " + response);
            return response;
        }
    }

    @Test
    public void testValidBookingAccepted() {
        String response = sendBookingRequest("Alice", List.of("Hotel-A", "Hotel-B", "Hotel-C"));
        assertTrue(response.contains("successful") || response.contains("failed"),
                "Answer should show success or failure");
    }

    @Test
    public void testInvalidBookingRejected_DuplicateConsecutiveHotels() {
        String response = sendBookingRequest("Bob", List.of("Hotel-B", "Hotel-B", "Hotel-C"));
        assertTrue(response.contains("same Hotel appears twice"),
                "Same hotels directly have to be declined");
    }

    @Test
    public void testValidBookingWithRepeatsNotConsecutive() {
        String response = sendBookingRequest("Clara", List.of("Hotel-C", "Hotel-A", "Hotel-C"));
        assertTrue(response.contains("successful") || response.contains("failed"),
                "Answer should show success or failure");
    }

    @Test
    public void testInvalidBookingRejected_TwoSameHotels() {
        String response = sendBookingRequest("David", List.of("Hotel-A", "Hotel-A"));
        assertTrue(response.contains("same Hotel appears twice"),
                "Same hotels directly have to be declined");
    }

    @Test
    public void testManyBookingsInParallel() throws InterruptedException {
        List<Thread> threads = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            final int index = i;
            Thread t = new Thread(() -> {
                String customer = "User" + index;
                List<String> hotels = List.of("Hotel-A", "Hotel-B", "Hotel-C");
                String response = sendBookingRequest(customer, hotels);
                assertNotNull(response, "Response should not be null");
            });
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }
    }
}
