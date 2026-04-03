package com.flamingo.llp.monitor;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MonitorEventBus {

    private final CopyOnWriteArrayList<Consumer<Object>> listeners = new CopyOnWriteArrayList<>();

    public void publish(Object event) {
        for (Consumer<Object> l : listeners) {
            l.accept(event);
        }
    }

    public void subscribe(Consumer<Object> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<Object> listener) {
        listeners.remove(listener);
    }
}