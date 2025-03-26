package de.travelbroker;

import de.travelbroker.broker.TravelBroker;

public class Main {
    public static void main(String[] args) {
        TravelBroker broker = new TravelBroker();
        broker.start();
    }
}
