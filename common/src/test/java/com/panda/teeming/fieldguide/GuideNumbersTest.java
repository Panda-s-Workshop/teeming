package com.panda.teeming.fieldguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panda.teeming.charm.Charms;
import com.panda.teeming.mob.end.AnchorGolem;
import com.panda.teeming.mob.end.Chronomite;
import com.panda.teeming.mob.end.Driftsong;
import com.panda.teeming.mob.end.ExileWraith;
import com.panda.teeming.mob.end.Gatecaster;
import com.panda.teeming.mob.end.GeminiWatchers;
import com.panda.teeming.mob.end.GrandOrrery;
import com.panda.teeming.mob.end.LiftstoneMonolith;
import com.panda.teeming.mob.end.Mnemovore;
import com.panda.teeming.mob.end.MoteSwarm;
import com.panda.teeming.mob.end.Pullstar;
import com.panda.teeming.mob.end.Recurrent;
import com.panda.teeming.mob.end.ShardlingColossus;
import com.panda.teeming.mob.end.VoidBloom;
import com.panda.teeming.mob.nether.BasaltBehemoth;
import com.panda.teeming.mob.nether.KeeningShade;
import com.panda.teeming.mob.nether.MagmaTortoise;
import com.panda.teeming.mob.nether.PyreHerald;
import com.panda.teeming.mob.nether.SlagheartGolem;
import com.panda.teeming.mob.nether.Slagspitter;
import com.panda.teeming.mob.nether.SporeCannon;
import com.panda.teeming.mob.nether.WeeplingBroodmother;
import com.panda.teeming.mob.overworld.Dustwhirl;
import com.panda.teeming.mob.overworld.HollowMourner;
import com.panda.teeming.mob.overworld.Hushwing;
import com.panda.teeming.mob.overworld.MireGulper;
import com.panda.teeming.mob.overworld.Petalkin;
import com.panda.teeming.mob.overworld.Rattlecoil;
import com.panda.teeming.mob.overworld.Thornhorn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the player reads states the numbers the code plays by. Each phrase below is built from the constant it names
 * (12.0 becomes "twelve", 30 ticks "a second and a half", 2.0 "double"), so a changed constant fails here until the
 * Field Guide text, the charm tooltip or the advancement that quotes it says the new number. The Field Guide writes
 * numbers as words; tooltips and advancements write them as digits.
 */
class GuideNumbersTest {
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "teeming", "lang", "en_us.json");
    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    /** A whole number as the guide writes it: "twelve", "forty-eight". */
    static String words(double value) {
        int n = (int) value;
        if (n != value || n < 0 || n > 99) fail("no plain wording for " + value);
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : "-" + ONES[n % 10]);
    }

    /** A length of time in ticks as the guide writes it: "half a second", "a second", "a second and a half", "three seconds". */
    static String seconds(int ticks) {
        if (ticks == 10) return "half a second";
        if (ticks == 20) return "a second";
        if (ticks == 30) return "a second and a half";
        if (ticks % 20 != 0) fail("no plain wording for " + ticks + " ticks");
        return words(ticks / 20) + " seconds";
    }

    /** A damage multiplier as the guide writes it: "double", "triple", "half again as much". */
    static String times(double factor) {
        if (near(factor, 2.0)) return "double";
        if (near(factor, 3.0)) return "triple";
        if (near(factor, 1.5)) return "half again as much";
        fail("no plain wording for x" + factor);
        return "";
    }

    /** A share as the guide writes it: "half", "a quarter", "a fifth". */
    static String share(double part) {
        if (near(part, 0.5)) return "half";
        if (near(part, 0.25)) return "a quarter";
        if (near(part, 0.2)) return "a fifth";
        fail("no plain wording for a share of " + part);
        return "";
    }

    /** Equal as written in the code: a float constant such as 0.2F is not exactly the double 0.2. */
    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    /** A whole number of seconds as a tooltip or advancement writes it: "2 seconds". */
    static String digitSeconds(int ticks) {
        if (ticks % 20 != 0) fail("no whole seconds in " + ticks + " ticks");
        return String.valueOf(ticks / 20);
    }

    private record Row(String key, String phrase) {}

    private final List<Row> rows = new ArrayList<>();

    /** The phrase is in one of the mob's four Field Guide texts (home, role, tell, counter). */
    private void guide(String mob, String phrase) {
        rows.add(new Row("guide.teeming." + mob, phrase));
    }

    /** The phrase is in the lang entry {@code key}. */
    private void text(String key, String phrase) {
        rows.add(new Row(key, phrase));
    }

    @Test
    void theGuideStatesTheNumbersTheCodeUses() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();

        // the End: the numbers the balance pass changed, and their neighbours on the same pages
        guide("shardling_colossus", "A hit of " + words(ShardlingColossus.SPLIT_HIT) + " or more");
        guide("shardling_colossus", "below " + share(ShardlingColossus.SPLIT_BELOW) + " health");
        guide("shardling_colossus", "Kill one half within " + seconds(ShardlingColossus.REJOIN_AFTER));
        guide("recurrent", "kill it again within " + seconds(Recurrent.REWIND_EVERY) + " of a rewind");
        guide("recurrent", "stands dazed for " + seconds(Recurrent.REWOUND_TICKS));
        guide("recurrent", "where it stood " + seconds(Recurrent.REWIND_BACK) + " earlier");
        guide("recurrent", "only " + words(Recurrent.ANCHOR_HEALTH) + " health");
        assertEquals(0, Mnemovore.REPAY_BONUS, "the Mnemovore's page says it pays back every point it took, but never more");
        guide("mnemovore", "every point it took, but never more");
        guide("mnemovore", "takes " + words(Mnemovore.LEVELS_PER_BITE) + " of your experience levels");
        guide("mnemovore", "holds still for " + seconds(Mnemovore.GAPE_TICKS));
        guide("gemini_watchers", seconds(GeminiWatchers.REVIVE_TICKS));
        guide("gemini_watchers", "with " + share(GeminiWatchers.REVIVE_HEALTH) + " its health");
        guide("grand_orrery", "opens gold for " + seconds(GrandOrrery.OPEN_TICKS));
        guide("grand_orrery", "takes " + times(GrandOrrery.OPEN_DAMAGE) + " damage");
        guide("grand_orrery", "burst of " + words(GrandOrrery.BURST_VOLLEYS) + " volleys");
        assertEquals(0, GrandOrrery.OPEN_ARMOR, "the Grand Orrery's page says it has no armour while open");
        guide("grand_orrery", "no armour");
        assertTrue(GrandOrrery.MOTE_PIERCE > 0, "the Grand Orrery's page says each mote goes partly through armour");
        guide("grand_orrery", "goes partly through armour");
        guide("pullstar", "within " + words(Pullstar.PULL_RADIUS) + " blocks");
        guide("pullstar", "Every " + seconds(Pullstar.CYCLE_TICKS) + " it dims for " + words(Pullstar.DIM_TICKS / 20.0));
        guide("pullstar", "takes " + times(Pullstar.DIM_DAMAGE) + " damage");
        guide("void_bloom", share(VoidBloom.GROW_STEP) + " a block wider every " + seconds(VoidBloom.GROW_EVERY) + ", out to "
                + words(VoidBloom.MAX_RADIUS) + " blocks");
        guide("anchor_golem", "Heavy for " + seconds(AnchorGolem.HEAVY_TICKS));
        guide("anchor_golem", "at " + share(AnchorGolem.MIRED_SPEED) + " speed");
        guide("chronomite", "the first " + words(Chronomite.SHELL) + " points of damage");
        guide("chronomite", "for " + words(Chronomite.FIELD_RADIUS) + " blocks round it");
        guide("mote_swarm", "only does " + share(MoteSwarm.SINGLE_TARGET_DAMAGE));
        guide("mote_swarm", "anything that spreads does " + times(MoteSwarm.SPREAD_DAMAGE));
        guide("driftsong", "Every " + seconds(Driftsong.SONG_EVERY) + " it sings");
        guide("driftsong", "within " + words(Driftsong.SONG_RANGE) + " blocks");
        guide("driftsong", "Slow Falling for " + seconds(Driftsong.SLOW_FALLING_TICKS));
        guide("driftsong", "dives " + words(Driftsong.DIVE_DEPTH) + " blocks deeper");
        guide("exile_wraith", words(ExileWraith.EXILE_NEAR) + " to " + words(ExileWraith.EXILE_FAR) + " blocks off");
        guide("gatecaster", words(Gatecaster.GATE_NEAR) + " to " + words(Gatecaster.GATE_FAR) + " blocks behind you");
        guide("liftstone_monolith", "lifted " + words(LiftstoneMonolith.LIFT_HEIGHT) + " blocks into the air");

        // the Nether
        guide("spore_cannon", words(SporeCannon.SPORE_DAMAGE) + " damage each");
        guide("spore_cannon", "Every " + seconds(SporeCannon.VOLLEY_EVERY) + " it fires " + words(SporeCannon.SPORES) + " spores");
        guide("spore_cannon", "stuns it for " + seconds(SporeCannon.STUN_TICKS));
        guide("spore_cannon", "every hit lands " + times(SporeCannon.STUN_DAMAGE));
        guide("spore_cannon", "pops with " + words(SporeCannon.SPORE_HEALTH) + " hit");
        guide("slagheart_golem", "shakes for " + seconds(SlagheartGolem.TELL_TICKS));
        guide("slagheart_golem", "within " + words(SlagheartGolem.ERUPT_RADIUS) + " blocks");
        guide("slagheart_golem", "quenched for " + seconds(SlagheartGolem.QUENCH_TICKS) + " and takes "
                + times(SlagheartGolem.QUENCHED_DAMAGE) + " damage");
        assertTrue(SlagheartGolem.POWDER_SNOW >= SlagheartGolem.MAX_HEAT, "the Slagheart's page says powder snow takes all its heat");
        guide("slagheart_golem", "Powder snow takes all of it");
        guide("magma_tortoise", "Feed it magma cream and you can ride it across the lava");
        guide("magma_tortoise", "keeps it with you for " + seconds(MagmaTortoise.FOLLOW_TICKS));
        guide("slagspitter", words(Slagspitter.MIN_RANGE) + " to " + words(Slagspitter.MAX_RANGE) + " blocks away");
        guide("slagspitter", "burns for " + seconds(Slagspitter.PATCH_TICKS));
        guide("keening_shade", "Silenced for " + seconds(KeeningShade.SILENCE_TICKS));
        guide("keening_shade", "Every " + seconds(KeeningShade.WAIL_EVERY));
        guide("pyre_herald", "soaks up " + words(PyreHerald.SHELL / 2) + " hearts");
        guide("weepling_broodmother", "lays " + words(WeeplingBroodmother.CLUTCH) + " pale sacs");
        guide("weepling_broodmother", "at most " + words(WeeplingBroodmother.MAX_BROOD) + " sacs");
        guide("basalt_behemoth", "within " + words(BasaltBehemoth.SLAM_RADIUS) + " blocks");
        guide("basalt_behemoth", "takes " + times(BasaltBehemoth.EXPOSED_DAMAGE) + " damage");

        // the Overworld
        guide("thornhorn", "paws the ground for " + seconds(Thornhorn.TELL_TICKS));
        guide("thornhorn", "stunned for " + seconds(Thornhorn.STUN_TICKS));
        guide("thornhorn", "takes " + times(Thornhorn.STUN_DAMAGE) + " damage");
        guide("hushwing", "a sprint within " + words(Hushwing.SPRINT_RANGE) + " blocks, a fight within " + words(Hushwing.COMBAT_RANGE)
                + " and mining within " + words(Hushwing.MINING_RANGE));
        guide("mire_gulper", "hit it " + words(MireGulper.HITS_TO_SPIT) + " times");
        guide("mire_gulper", "chews on you for " + seconds(MireGulper.HOLD_TICKS));
        guide("hollow_mourner", "within " + words(HollowMourner.GRAVE_RANGE) + " blocks");
        guide("hollow_mourner", "chants for " + seconds(HollowMourner.TELL_TICKS));
        guide("hollow_mourner", "every " + seconds(HollowMourner.CHANT_EVERY) + " it raises one");
        guide("petalkin", "leaving " + words(Petalkin.DECOY_COUNT) + " copies");
        guide("petalkin", "The copies last " + seconds(Petalkin.DECOY_TICKS));
        guide("petalkin", "up to " + words(Petalkin.BLINK_RANGE) + " blocks away");
        guide("dustwhirl", "within " + words(Dustwhirl.ITEM_REACH) + " blocks");
        guide("dustwhirl", "outward for " + seconds(Dustwhirl.GUST_TICKS));
        guide("rattlecoil", "within " + words(Rattlecoil.WARN_RANGE) + " blocks");
        guide("rattlecoil", "within " + words(Rattlecoil.STRIKE_RANGE) + " blocks");
        guide("rattlecoil", "more than " + words(Rattlecoil.CALM_RANGE) + " blocks away");

        // charm tooltips name the mobs they answer and say their numbers
        text("item.teeming.gilded_ward_charm.effect", "Aurovores and Siphonjellies fail to steal from you " + share(Charms.GILDED_WARD_FAIL) + " the time");
        assertEquals(Charms.HEARTH_FREEZE, Charms.HEARTH_TETHER_HEAL, "the Hearth Charm's tooltip halves both with one word");
        assertEquals("half", share(Charms.HEARTH_FREEZE), "the Hearth Charm's tooltip says it halves");
        text("item.teeming.hearth_charm.effect", "Halves the Shiverwraith's frost and its tether's healing");
        text("item.teeming.quench_charm.effect", "burns you " + share(Charms.QUENCH_PATCH_BURN) + " as much");
        text("item.teeming.tether_charm.effect", "after " + digitSeconds(Charms.TETHER_SNAP_TICKS) + " seconds");
        text("item.teeming.clear_voice_charm.effect", "from " + digitSeconds(KeeningShade.SILENCE_TICKS) + " seconds to "
                + digitSeconds(Charms.CLEAR_VOICE_SILENCED_TICKS));

        // advancements that quote a number
        text("advancements.teeming.grand_orrery_pattern.description", "within " + (int) GrandOrrery.MOTE_RANGE + " blocks");
        text("advancements.teeming.magma_tortoise_ride.description", (int) MagmaTortoise.FERRY_BLOCKS + " blocks across lava");

        List<String> stale = new ArrayList<>();
        for (Row row : rows) {
            String said = said(lang, row.key());
            if (said == null) stale.add(row.key() + ": no such text");
            else if (!said.contains(row.phrase())) stale.add(row.key() + ": expected \"" + row.phrase() + "\" in \"" + said + "\"");
        }
        assertTrue(stale.isEmpty(), "text that no longer states its constant:\n" + String.join("\n", stale));
    }

    /** The lang text for {@code key}; for a mob's guide prefix, its four texts joined. Null when there is none. */
    private static String said(JsonObject lang, String key) {
        if (lang.has(key)) return lang.get(key).getAsString();
        if (!key.startsWith("guide.teeming.")) return null;
        StringBuilder all = new StringBuilder();
        for (String side : new String[] {"home", "role", "tell", "counter"}) {
            if (lang.has(key + "." + side)) all.append(lang.get(key + "." + side).getAsString()).append('\n');
        }
        return all.isEmpty() ? null : all.toString();
    }

    @Test
    void theWordingHelpersSayWhatTheGuideSays() {
        assertEquals("twelve", words(12.0F));
        assertEquals("forty-eight", words(48));
        assertEquals("twenty", words(20));
        assertEquals("half a second", seconds(10));
        assertEquals("a second and a half", seconds(30));
        assertEquals("thirty seconds", seconds(600));
        assertEquals("half again as much", times(1.5F));
        assertEquals("a quarter", share(0.25F));
    }
}
