#!/bin/bash

PROJECT_DIR="$(pwd)"
cd "$PROJECT_DIR" || exit 1

echo "🔧 [1/5] Baue das Projekt..."
mvn clean package -q

echo "🏨 [2/5] Starte HotelService A (Port 5556)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.hotel.HotelService" -Dexec.args="Hotel-A" > hotelA.log 2>&1 &
sleep 2

echo "🏨 [2/5] Starte HotelService B (Port 5557)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.hotel.HotelService" -Dexec.args="Hotel-B" > hotelB.log 2>&1 &
sleep 2

echo "🏨 [2/5] Starte HotelService C (Port 5558)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.hotel.HotelService" -Dexec.args="Hotel-C" > hotelC.log 2>&1 &
sleep 2

echo "🧠 [3/5] Starte TravelBroker (Port 5560)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.client.TravelBroker" > broker.log 2>&1 &
sleep 3

echo "📨 [4/5] Starte RequestSender – sendet Buchungsanfragen..."
mvn exec:java -Dexec.mainClass="de.travelbroker.client.RequestSender"

echo "✅ [5/5] Alle Komponenten wurden erfolgreich gestartet."
echo "📄 Logs: hotelA.log | hotelB.log | hotelC.log | broker.log"
