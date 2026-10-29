package com.panda.teeming;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The text of a {@code key=value} settings file (Fabric's {@code teeming.properties}), written in a fixed key order under
 * the current header. A rewrite keeps every value the file gives, a key's own line as the file writes it, the comments
 * above a key (with that key), keys the mod doesn't know (after its own, in the file's order) and comments after the
 * last key; it adds a missing key with its default and moves a renamed key's value to its new name. The old header (the
 * comment lines the file starts with, up to the first blank line or key) is replaced, so it always says what the
 * current version does. Writing the same text again gives the same text, so a file is rewritten only when it changes.
 */
public final class SettingsFile {
    private SettingsFile() {}

    /** One key's line as the file writes it, with the comment lines above it. */
    private record Line(String key, String value, String text, List<String> comments) {}

    /**
     * The file's new text. {@code old}: its text (empty for no file). {@code header}: the header's lines, without the
     * comment marks. {@code defaults}: every key the mod reads, in the file's order, with its default. {@code renamed}:
     * an old key name to its new one; the old key's value moves to the new name unless the file already has that.
     */
    public static String write(String old, String header, Map<String, String> defaults, Map<String, String> renamed) {
        Map<String, Line> keys = new LinkedHashMap<>();
        List<String> comments = new ArrayList<>();
        List<String> lines = logicalLines(old);
        int i = 0;
        while (i < lines.size() && comment(lines.get(i))) i++;              // the old header
        for (; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            if (comment(line)) {
                comments.add(line);
                continue;
            }
            Properties one = new Properties();
            try {
                one.load(new StringReader(line));
            } catch (IOException | IllegalArgumentException e) {
                comments.add("# " + line);                                    // unreadable: kept, as a comment
                continue;
            }
            if (one.isEmpty()) continue;
            String key = one.stringPropertyNames().iterator().next();
            Line was = keys.remove(key);                                      // a key written twice: the last one counts
            List<String> above = new ArrayList<>(was == null ? List.of() : was.comments());
            above.addAll(comments);
            comments.clear();
            keys.put(key, new Line(key, one.getProperty(key), line, above));
        }
        for (Map.Entry<String, String> rename : renamed.entrySet()) {
            Line was = keys.remove(rename.getKey());
            if (was != null && !keys.containsKey(rename.getValue()))
                keys.put(rename.getValue(), new Line(rename.getValue(), was.value(), escape(rename.getValue(), was.value()), was.comments()));
        }
        StringBuilder out = new StringBuilder();
        for (String line : header.split("\n", -1)) out.append(line.isEmpty() ? "#" : "# " + line).append('\n');
        out.append('\n');
        for (Map.Entry<String, String> key : defaults.entrySet()) {
            Line line = keys.remove(key.getKey());
            if (line == null) {
                out.append(escape(key.getKey(), key.getValue())).append('\n');
            } else {
                line.comments().forEach(c -> out.append(c).append('\n'));
                out.append(line.text()).append('\n');
            }
        }
        for (Line line : keys.values()) {
            line.comments().forEach(c -> out.append(c).append('\n'));
            out.append(line.text()).append('\n');
        }
        comments.forEach(c -> out.append(c).append('\n'));
        return out.toString();
    }

    /** The values the text gives, as {@link Properties} reads them. */
    public static Properties read(String text) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            TeemingMod.LOGGER.warn("Couldn't read the settings text", e);
        }
        return p;
    }

    private static boolean comment(String line) {
        String t = line.stripLeading();
        return t.startsWith("#") || t.startsWith("!");
    }

    /** The text's lines, a line that ends in an odd number of backslashes joined to the next, as {@link Properties} reads it. */
    private static List<String> logicalLines(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder joined = null;
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            boolean continues = !comment(line) || joined != null ? trailingBackslashes(line) % 2 == 1 : false;
            if (joined == null) joined = new StringBuilder(line);
            else joined.append('\n').append(line);
            if (!continues) {
                out.add(joined.toString());
                joined = null;
            }
        }
        if (joined != null) out.add(joined.toString());
        while (!out.isEmpty() && out.getLast().isEmpty()) out.removeLast();
        return out;
    }

    private static int trailingBackslashes(String line) {
        int n = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) n++;
        return n;
    }

    /** One {@code key=value} line, escaped as {@link Properties#store} would write it. */
    private static String escape(String key, String value) {
        Properties one = new Properties();
        one.setProperty(key, value);
        java.io.StringWriter out = new java.io.StringWriter();
        try {
            one.store(out, null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (String line : out.toString().split("\\R")) {
            if (!line.isEmpty() && !line.startsWith("#")) return line;
        }
        return key + "=" + value;
    }
}
