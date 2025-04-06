package de.travelbroker.messaging;

import org.zeromq.ZMQ;
import org.zeromq.SocketType;
import org.zeromq.ZContext;

public class MessageSender implements AutoCloseable {

    private final ZContext context;
    private final ZMQ.Socket sender;

    public MessageSender(String address) {
        context = new ZContext(); // neue API statt ZMQ.context(1)
        sender = context.createSocket(SocketType.REQ);
        sender.connect(address);
    }

    public String send(String message) {
        sender.send(message);
        return sender.recvStr();
    }

    @Override
    public void close() {
        sender.close();
        context.close(); // ZContext schließt auch intern alles korrekt
    }
}
