package com.apogames.tiwanaku.server;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class LegacyScores {
    record Parsed(List<HistoryEntry> entries, List<String> rejected) { }
    static Parsed parse(String raw) {
        String data = raw.stripLeading().startsWith("<") ? properties(raw) : raw;
        List<HistoryEntry> entries = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        String fingerprint = StoreJson.checksum(raw);
        String[] records = data.split(";", -1);
        if (records.length > 10_000) throw ApiException.badRequest("At most 10000 legacy records are supported per import");
        for (int i = 0; i < records.length; i++) {
            if (records[i].isBlank()) continue;
            String[] fields = records[i].split("\\|", -1);
            try {
                if (fields.length != 3 && fields.length != 4 && fields.length != 5 && fields.length != 7)
                    throw new IllegalArgumentException("Expected 3, 4, 5 or 7 fields");
                if (fields[0].isBlank() || fields[0].length() > 64) throw new IllegalArgumentException("Invalid map size");
                long elapsed = nonnegative(fields[1]);
                int accuracy = Integer.parseInt(fields[2]);
                if (accuracy < 0 || accuracy > 100) throw new IllegalArgumentException("Accuracy outside 0..100");
                Long date = fields.length >= 4 ? nonnegative(fields[3]) : null;
                if (date != null && date == 0) date = null;
                String tag = fields.length >= 5 ? fields[4] : null;
                if (tag != null && tag.length() > 128) throw new IllegalArgumentException("Player text exceeds 128 characters");
                Long clicks = fields.length == 7 ? nonnegative(fields[5]) : null;
                Long misses = fields.length == 7 ? nonnegative(fields[6]) : null;
                if (misses != null && misses > clicks) throw new IllegalArgumentException("Misses exceed total clicks");
                String id = UUID.nameUUIDFromBytes((fingerprint + ":" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                entries.add(new HistoryEntry(id, null, fields[0], date, elapsed, tag,
                        "legacy-clicks-v1", null, accuracy, clicks, misses, null, null));
            } catch (IllegalArgumentException exception) {
                rejected.add("Record " + (i + 1) + ": " + exception.getMessage());
            }
        }
        return new Parsed(entries, rejected);
    }
    private static long nonnegative(String value) {
        long result = Long.parseLong(value);
        if (result < 0 || result > 9_007_199_254_740_991L) throw new IllegalArgumentException("Number outside supported nonnegative range");
        return result;
    }
    private static String properties(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
            });
            var document = builder.parse(new InputSource(new StringReader(xml)));
            if (document.getDoctype() != null && document.getDoctype().getInternalSubset() != null)
                throw ApiException.badRequest("Internal XML entities are not supported");
            if (!document.getDocumentElement().getTagName().equals("properties"))
                throw ApiException.badRequest("Expected libGDX Java properties XML");
            var nodes = document.getElementsByTagName("entry");
            String result = null;
            for (int i = 0; i < nodes.getLength(); i++) {
                var entry = (org.w3c.dom.Element) nodes.item(i);
                if (entry.getAttribute("key").equals("scoreboard_results")) {
                    if (result != null) throw ApiException.badRequest("Duplicate scoreboard_results entry");
                    result = entry.getTextContent();
                }
            }
            if (result == null) throw ApiException.badRequest("No scoreboard_results entry found");
            return result;
        } catch (javax.xml.parsers.ParserConfigurationException exception) {
            throw new IllegalStateException("Safe XML parser is unavailable", exception);
        } catch (java.io.IOException | SAXException exception) {
            throw ApiException.badRequest("Invalid legacy properties XML");
        }
    }
}
