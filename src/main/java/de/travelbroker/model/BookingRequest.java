package de.travelbroker.model;

public class BookingRequest {
    private String customerName;
    private String hotel;

    public BookingRequest(String customerName, String hotel) {
        this.customerName = customerName;
        this.hotel = hotel;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getHotel() {
        return hotel;
    }
}
