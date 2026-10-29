package com.panda.teeming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** The settings file's rewrite: the current header, keys in order, and a pack's values, comments and own keys kept. */
class SettingsFileTest {
    private static final String HEADER = "Teeming (read at startup)\nspawningDensity: 0..4, thins below 1";
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("spawningDensity", "1.0");
        DEFAULTS.put("trophies.chance", "0.025");
        DEFAULTS.put("mobs.thornhorn.enabled", "true");
    }

    private static final Map<String, String> RENAMED = Map.of("mob.thornhorn.enabled", "mobs.thornhorn.enabled");

    @Test
    void firstStartWritesEveryDefaultUnderTheHeader() {
        String text = SettingsFile.write("", HEADER, DEFAULTS, RENAMED);
        assertEquals("# Teeming (read at startup)\n# spawningDensity: 0..4, thins below 1\n\nspawningDensity=1.0\ntrophies.chance=0.025\nmobs.thornhorn.enabled=true\n", text);
        assertEquals(text, SettingsFile.write(text, HEADER, DEFAULTS, RENAMED), "writing it again changes nothing");
    }

    @Test
    void anOldFileGetsTheNewHeaderInOrderWithItsValuesCommentsAndOwnKeys() {
        // as Properties.store wrote it: the old header, a date, keys in hash order; then a pack's edits
        String old = """
                #Teeming (read at startup)
                #spawningDensity: multiplies how often every Teeming mob spawns
                #Thu Oct 29 02:48:00 CET 2026
                trophies.chance=0.05
                # the pack's own setting, read by its scripts
                pack.extra = keep me
                # thinner spawns for this pack
                spawningDensity = 0.5
                """;
        String text = SettingsFile.write(old, HEADER, DEFAULTS, RENAMED);
        assertEquals("""
                # Teeming (read at startup)
                # spawningDensity: 0..4, thins below 1

                # thinner spawns for this pack
                spawningDensity = 0.5
                trophies.chance=0.05
                mobs.thornhorn.enabled=true
                # the pack's own setting, read by its scripts
                pack.extra = keep me
                """, text);
        Properties p = SettingsFile.read(text);
        assertEquals("0.5", p.getProperty("spawningDensity"));
        assertEquals("keep me", p.getProperty("pack.extra"));
        assertEquals(text, SettingsFile.write(text, HEADER, DEFAULTS, RENAMED), "writing it again changes nothing");
        String newer = SettingsFile.write(text, HEADER + "\nmore", DEFAULTS, RENAMED);
        assertTrue(newer.startsWith("# Teeming (read at startup)\n# spawningDensity: 0..4, thins below 1\n# more\n\n# thinner spawns"),
                "a new header replaces the old one: " + newer);
    }

    @Test
    void aRenamedKeyMovesItsValueUnlessTheNewNameIsSet() {
        String moved = SettingsFile.write("mob.thornhorn.enabled=false\n", HEADER, DEFAULTS, RENAMED);
        Properties p = SettingsFile.read(moved);
        assertEquals("false", p.getProperty("mobs.thornhorn.enabled"));
        assertTrue(!p.containsKey("mob.thornhorn.enabled"), "the old name is gone: " + moved);
        String both = SettingsFile.write("mob.thornhorn.enabled=false\nmobs.thornhorn.enabled=true\n", HEADER, DEFAULTS, RENAMED);
        assertEquals("true", SettingsFile.read(both).getProperty("mobs.thornhorn.enabled"));
    }

    @Test
    void aContinuedValueAndTrailingCommentsStay() {
        String old = "# header\n\npack.list = a, \\\n    b\nspawningDensity=2.0\n# the end\n";
        String text = SettingsFile.write(old, HEADER, DEFAULTS, RENAMED);
        assertEquals("a, b", SettingsFile.read(text).getProperty("pack.list"));
        assertTrue(text.endsWith("pack.list = a, \\\n    b\n# the end\n"), text);
    }
}
