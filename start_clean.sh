#!/bin/bash

echo "🧹 Beende ggf. alte Prozesse auf Ports 5556–5560..."
for port in 5556 5557 5558 5560; do
  pid=$(lsof -ti tcp:$port)
  if [ -n "$pid" ]; then
    echo "🔪 Beende Prozess auf Port $port (PID: $pid)..."
    kill -9 $pid
  fi
done

sleep 2  # 👈 neu: gib dem System Zeit, die Ports wirklich freizugeben


echo "🗑 Leere alte Logs..."
: > hotelA.log
: > hotelB.log
: > hotelC.log
: > broker.log

echo "🔧 Baue das Projekt..."
mvn clean package -q

echo "🏨 Starte HotelService A (Port 5556)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.hotel.HotelService" -Dexec.args="Hotel-A" > hotelA.log 2>&1 &
sleep 2

echo "🏨 Starte HotelService B (Port 5557)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.hotel.HotelService" -Dexec.args="Hotel-B" > hotelB.log 2>&1 &
sleep 2

echo "🏨 Starte HotelService C (Port 5558)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.hotel.HotelService" -Dexec.args="Hotel-C" > hotelC.log 2>&1 &
sleep 2

echo "🧠 Starte TravelBroker (Port 5560)..."
mvn exec:java -Dexec.mainClass="de.travelbroker.client.TravelBroker" > broker.log 2>&1 &
sleep 3

echo "📨 Starte RequestSender – sendet Buchungsanfragen..."
mvn exec:java -Dexec.mainClass="de.travelbroker.client.RequestSender"

echo "✅ Alle Komponenten gestartet. Logs in hotelA.log | hotelB.log | hotelC.log | broker.log"
