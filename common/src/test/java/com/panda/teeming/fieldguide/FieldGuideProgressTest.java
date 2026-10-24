package com.panda.teeming.fieldguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/** The Field Guide's progress rules, and that it survives the save codec and the sync codec unchanged. */
class FieldGuideProgressTest {
    private static FieldGuideProgress sample() {
        return FieldGuideProgress.EMPTY.withSeen("thornhorn", "base").withKill("slagheart_golem", "cooled_golem")
                .withKill("slagheart_golem", "base").withKill("slagheart_golem", "base").withTrophy("levimoth");
    }

    @Test
    void seenAndKills() {
        FieldGuideProgress p = sample();
        assertTrue(p.hasSeen("thornhorn"));
        assertTrue(p.hasSeen("slagheart_golem"), "a kill counts as seeing it");
        assertFalse(p.hasSeen("levimoth"));
        assertEquals(3, p.killsOf("slagheart_golem"));
        assertTrue(p.revealed("slagheart_golem", FieldGuide.KILLS_TO_REVEAL));
        assertFalse(p.revealed("thornhorn", FieldGuide.KILLS_TO_REVEAL));
        assertTrue(p.hasSeenLook("slagheart_golem", "cooled_golem") && p.hasSeenLook("slagheart_golem", "base"));
    }

    @Test
    void unchangedIsSameInstance() {
        FieldGuideProgress p = sample();
        assertSame(p, p.withSeen("thornhorn", "base"), "seeing a known mob in a known look changes nothing");
    }

    @Test
    void trophiesOwned() {
        FieldGuideProgress p = sample();
        assertTrue(p.hasTrophy("levimoth"));
        assertFalse(p.hasTrophy("thornhorn"));
        assertSame(p, p.withTrophy("levimoth"), "owning a trophy twice changes nothing");
        assertTrue(p.withKill("levimoth", "base").hasTrophy("levimoth"), "a kill keeps the trophies");
    }

    @Test
    void oldSaveWithoutTrophiesLoads() {
        JsonElement json = JsonParser.parseString("{\"seen\":[\"thornhorn\"],\"kills\":{\"thornhorn\":2},\"looks\":[\"thornhorn/base\"]}");
        FieldGuideProgress p = FieldGuideProgress.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(2, p.killsOf("thornhorn"));
        assertTrue(p.trophies().isEmpty());
    }

    @Test
    void saveCodecRoundTrip() {
        FieldGuideProgress p = sample();
        JsonElement json = FieldGuideProgress.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
        assertEquals(p, FieldGuideProgress.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void syncCodecRoundTrip() {
        FieldGuideProgress p = sample();
        ByteBuf buf = Unpooled.buffer();
        FieldGuideProgress.STREAM_CODEC.encode(buf, p);
        assertEquals(p, FieldGuideProgress.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }
}
