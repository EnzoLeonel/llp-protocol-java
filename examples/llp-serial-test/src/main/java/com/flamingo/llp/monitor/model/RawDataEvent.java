package com.flamingo.llp.monitor.model;

public class RawDataEvent {

    private final Direction direction;
    private final byte data;
    private final long timestamp;

    public RawDataEvent(Direction direction, byte data) {
        this.direction = direction;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public Direction getDirection() { return direction; }
    public byte getData() { return data; }
    public long getTimestamp() { return timestamp; }
}