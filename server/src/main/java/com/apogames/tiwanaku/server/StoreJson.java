package com.apogames.tiwanaku.server;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.HexFormat;

@Component
public final class StoreJson {
    private final ObjectMapper mapper;
    public StoreJson(ObjectMapper mapper) { this.mapper = mapper; }
    public String write(Object value) { return mapper.writeValueAsString(value); }
    public <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JacksonException exception) {
            throw new org.springframework.dao.DataIntegrityViolationException("Persisted JSON is unreadable; preserve the data directory", exception);
        }
    }
    public <T> T input(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JacksonException exception) { throw ApiException.badRequest("Invalid import JSON: check required fields, types and unsupported properties"); }
    }
    public static String text(Object value) {
        if (value == null) return null;
        if (value instanceof Clob clob) {
            try { return clob.getSubString(1, Math.toIntExact(clob.length())); }
            catch (SQLException exception) { throw new IllegalStateException("Cannot read local store", exception); }
        }
        return (String) value;
    }
    public static String checksum(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
