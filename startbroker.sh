#!/bin/bash
echo "🧹 Beende alte Hotel- und Broker-Prozesse..."
kill -9 $(lsof -t -i :5556 -i :5557 -i :5558 -i :5569) 2>/dev/null


echo "🧳 Starte Hotel-A..."
java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.hotel.HotelService Hotel-A &

echo "🧳 Starte Hotel-B..."
java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.hotel.HotelService Hotel-B &

echo "🧳 Starte Hotel-C..."
java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.hotel.HotelService Hotel-C &

sleep 2

echo "🧠 Starte TravelBroker..."
java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.client.TravelBroker &
