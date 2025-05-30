# Travel-Broker using SAGAs

This project simulates a **distributed travel booking system** using the **SAGA pattern**. It includes a central travel broker and multiple hotel services, all communicating asynchronously via **ZeroMQ**. Failures (both technical and business) are simulated, and **rollback logic** ensures eventual consistency.

## Table of Contents

- `src/` – Java source code
- `config.json` – Configuration file to simulate errors and timeouts
- `startbroker.sh` – Bash script to start all services which are accepting requests
- `sendrequest.sh`- Start sending request
- `pom.xml` – Maven project file
- `target/` – Contains the built JAR files

## 🚀 Running the Project

### 1. Build the project

Type "mvn clean package" in the console

This generates a runnable **fat JAR**:
 in --> target/travel-broker-saga-1.0-SNAPSHOT-jar-with-dependencies.jar
---

### 2. Start the full system (Hotel Services + Broker)


chmod +x startbroker.sh
./startbroker.sh

This will:

* Start **Hotel-A** on port `5556`
* Start **Hotel-B** on port `5557`
* Start **Hotel-C** on port `5558`
* Start the **TravelBroker** on port `5569`

Logs will show simulation of delays, errors, bookings, and rollbacks.

---

### 3. Send booking requests

In a separate terminal:

chmod +x startbroker.sh
./startbroker.sh to start sending requests!

---

## Configuration

Edit `src/main/resources/config.json` to adjust the simulation:

```json
{
  "hotelErrorRate": 0.05,
  "hotelTimeoutRate": 0.1,
  "noRoomAvailableRate": 0.1,
  "bookingDelayMillis": 500,
  "brokerResponseTimeoutMillis": 1500,
  "maxRetries": 3
}
```

This lets you control:

* Technicael failures (timeouts or drops)
* Business failures (no rooms available)
* Response teims and retry behavior

---

## Test Scenarios

IN the following theare are the 

| Scenario                           | Expected Result                  |
| ---------------------------------- | -------------------------------- |
| All hotels confirm                 | Booking successful               |
| One hotel has no rooms             | Booking fails, rollback others   |
| One hotel does not respond (crash) | Retries; fails after maxRetries  |
| Multiple hotel timeouts            | Rollback after second failure    |
| Unexpected reply from hotel        | Logged, retries up to maxRetries |

---

## 📜 Authors & Contribution 

| Name        | Responsibility                      |
| ----------- | ----------------------------------- |
| \Alexander K. | HotelService implementation         |
| \Merle N. | TravelBroker logic and coordination |
| \Bent P. | Retry and Timeout handling          |
| \Eric U. | Test client and data generator      |
| \Louis S. | Documentation and setup scripts     |
