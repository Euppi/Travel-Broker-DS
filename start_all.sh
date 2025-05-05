#!/bin/bash

# Start Hotel A
osascript -e 'tell app "Terminal"
    do script "cd \"'"$PWD"'\"; java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.hotel.HotelService Hotel-A"
end tell'

# Start Hotel B
osascript -e 'tell app "Terminal"
    do script "cd \"'"$PWD"'\"; java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.hotel.HotelService Hotel-B"
end tell'

# Start Hotel C
osascript -e 'tell app "Terminal"
    do script "cd \"'"$PWD"'\"; java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.hotel.HotelService Hotel-C"
end tell'

# Start TravelBroker
osascript -e 'tell app "Terminal"
    do script "cd \"'"$PWD"'\"; java -cp target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar de.travelbroker.client.TravelBroker"
end tell'
