package com.apogames.settler.common;

import java.util.concurrent.atomic.AtomicLong;

public final class IDGenerator {

    private static final AtomicLong idCounter = new AtomicLong();

    public static Long createID() {
        return idCounter.getAndIncrement();
    }
}
