package com.apogames.settler.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    private static final String LOG_DIR = "logs";
    private static final String LOG_PREFIX = "settler";
    private static final String LOG_EXTENSION = ".log";

    private static PrintWriter writer = null;
    private static boolean initialized = false;

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            File logFile = new File(dir, LOG_PREFIX + "_" + timestamp + LOG_EXTENSION);
            writer = new PrintWriter(new FileWriter(logFile, true), true);
            writeRaw("=== Settler Log started: " + new Date() + " ===");
        } catch (IOException e) {
            System.err.println("Failed to initialize file logger: " + e.getMessage());
            writer = null;
        }
    }

    public static void debug(String tag, String message) {
        init();
        writeRaw("[" + timestamp() + "] [DEBUG] [" + tag + "] " + message);
    }

    public static void info(String tag, String message) {
        init();
        writeRaw("[" + timestamp() + "] [INFO] [" + tag + "] " + message);
    }

    public static void warn(String tag, String message) {
        init();
        writeRaw("[" + timestamp() + "] [WARN] [" + tag + "] " + message);
    }

    public static void error(String tag, String message) {
        init();
        writeRaw("[" + timestamp() + "] [ERROR] [" + tag + "] " + message);
    }

    private static void writeRaw(String line) {
        if (writer != null) {
            writer.println(line);
            writer.flush();
        }
        System.out.println(line);
    }

    private static String timestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        return sdf.format(new Date());
    }

    public static void close() {
        if (writer != null) {
            writeRaw("=== Settler Log ended: " + new Date() + " ===");
            writer.close();
            writer = null;
        }
    }
}
