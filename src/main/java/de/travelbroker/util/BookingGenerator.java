// src/main/java/de/travelbroker/util/BookingGenerator.java

package de.travelbroker.util;

import java.util.*;

public class BookingGenerator {

    private static final String[] HOTELS = {"Hotel-A", "Hotel-B", "Hotel-C"};
    private static final Random rand = new Random();

    public static Map<String, Object> generateBooking(int customerId) {
        Map<String, Object> booking = new HashMap<>();
        booking.put("customerName", "Testcustomer-" + customerId);

        int hotelCount = rand.nextInt(3) + 2; // 2 bis 4 Hotels
        List<String> hotels = new ArrayList<>();

        String lastHotel = "";
        for (int i = 0; i < hotelCount; i++) {
            String hotel;
            do {
                hotel = HOTELS[rand.nextInt(HOTELS.length)];
            } while (hotel.equals(lastHotel)); // Keine 2 direkt nacheinander
            hotels.add(hotel);
            lastHotel = hotel;
        }

        booking.put("hotels", hotels);
        return booking;
    }

    public static List<Map<String, Object>> generateBookings(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(generateBooking(i + 1));
        }
        return list;
    }
}
