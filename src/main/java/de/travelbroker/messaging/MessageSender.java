package de.travelbroker.messaging;

import org.zeromq.ZMQ;

public class MessageSender implements AutoCloseable {

    private final ZMQ.Context context;
    private final ZMQ.Socket sender;

    public MessageSender(String address) {
        context = ZMQ.context(1);
        sender = context.socket(ZMQ.REQ);
        sender.connect(address);
    }

    public String send(String message) {
        sender.send(message);
        return sender.recvStr();
    }

    @Override
    public void close() {
        sender.setLinger(0);
        sender.close();
        context.term();
    }
}
