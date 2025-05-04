// src/main/java/de/travelbroker/model/BookingResponse.java

package de.travelbroker.model;

public class BookingResponse {
    private boolean success;
    private String message;

    public BookingResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
