package de.travelbroker.hotel;

import org.zeromq.ZMQ;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class HotelService {

    private static double successRate = 0.8;     // 80% erfolgreich
    private static double crashRate = 0.1;       // 10% ignoriert Anfrage
    private static double unconfirmedRate = 0.1; // 10% kein Antwortversand

    public static void main(String[] args) {
        String hotelName = args.length > 0 ? args[0] : "Hotel-A";
        int port = hotelName.equals("Hotel-A") ? 5556 : 5557;

        loadConfig();

        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket receiver = context.socket(ZMQ.REP);
        receiver.bind("tcp://*:" + port);

        System.out.println(hotelName + " is running on port " + port + " with failure simulation...");

        while (!Thread.currentThread().isInterrupted()) {
            String request = receiver.recvStr();
            System.out.println(hotelName + " received: " + request);

            double rand = Math.random();

            if (rand < crashRate) {
                System.out.println("⚠️ Simulated crash: ignoring request.");
                continue; // Nachricht empfangen, aber keine Antwort → "crash"
            } else if (rand < crashRate + unconfirmedRate) {
                System.out.println("⚠️ Simulated drop: processing without confirmation.");
                // Tun so als ob gebucht wurde, aber senden keine Antwort
                continue;
            } else if (rand < crashRate + unconfirmedRate + (1 - crashRate - unconfirmedRate) * successRate) {
                System.out.println("✅ Booking successful.");
                receiver.send("confirmed");
            } else {
                System.out.println("❌ No rooms available.");
                receiver.send("rejected");
            }
        }

        receiver.close();
        context.term();
    }

    private static void loadConfig() {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream("config/hotel.properties"));
            successRate = Double.parseDouble(props.getProperty("successRate", "0.8"));
            crashRate = Double.parseDouble(props.getProperty("crashRate", "0.1"));
            unconfirmedRate = Double.parseDouble(props.getProperty("unconfirmedRate", "0.1"));
        } catch (IOException e) {
            System.out.println("⚠️ Could not load config. Using defaults.");
        }
    }
}
