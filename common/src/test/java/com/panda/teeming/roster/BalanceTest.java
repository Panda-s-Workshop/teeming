package com.panda.teeming.roster;

import static org.junit.jupiter.api.Assertions.*;

import com.panda.teeming.mob.end.FacetDoppel;
import com.panda.teeming.mob.end.GrandOrrery;
import com.panda.teeming.mob.end.MoteSwarm;
import com.panda.teeming.mob.end.Phasewalker;
import com.panda.teeming.mob.end.Riftjackal;
import com.panda.teeming.mob.end.VoidBloom;
import com.panda.teeming.mob.nether.BlackstoneScuttler;
import com.panda.teeming.mob.nether.ScarletMantis;
import com.panda.teeming.mob.overworld.Gloamhound;
import com.panda.teeming.roster.RosterStats.Dim;
import com.panda.teeming.roster.RosterStats.Entry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every mob against its dimension's weak reference player, with vanilla's armour and Protection formulas
 * ({@code CombatRules}: armour − damage / (2 + toughness / 4), clamped to [armour / 5, 20], each point −4 %; EPF
 * clamped to 20, each point −4 %). Reads {@code RosterStats}, so every changed number is checked here, and the mob
 * classes' own constants where a row's numbers alone would mislead (a share of a blow through armour, a blow made of
 * many small hits, damage a mob shrugs off, an alpha's stats).
 *
 * <p>Two kinds of bound. Ceilings keep a mob fair: the player kills it in time and survives enough of its hits.
 * Floors keep it a threat, so failing a counter costs something: the player dies to a dimension's ordinary hits, its
 * mechanic blows and its apex's strongest blow within a set number of each. A mob the floors leave out of a kind of hit
 * is named here with its reason, and {@link #theFloorsNameEveryMobTheyLeaveOut} lists them all.
 */
class BalanceTest {
    private static final float SWINGS_PER_SECOND = 1.6F;
    /** Vanilla's Hard difficulty: every mob's hit on a player times 1.5. The targets are for Normal. */
    private static final float HARD = 1.5F;

    private record Ref(float hit, float armor, float toughness, float epf) {}

    /** The most hits of each kind the reference player may survive: a hit or blow that needs more costs nothing. */
    private record Floor(float normalHits, float mechanicBlows, float apexBlows) {}

    private static Ref ref(Dim dim) {
        return switch (dim) {
            case OVERWORLD -> new Ref(6.0F, 15, 0, 0); // iron sword, iron set
            case NETHER -> new Ref(8.5F, 20, 8, 0); // diamond sword Sharpness II, diamond set
            case END -> new Ref(11.0F, 20, 12, 16); // netherite sword Sharpness V, netherite set Protection IV
        };
    }

    /**
     * The floors per dimension. The End's are the design's own (25, 8, 5). The Overworld's and the Nether's sit just
     * above their rosters' spread (ordinary hits 5.3-22.7 and 7.7-29.0, blows 3.1-6.4 and 6.2-12.8, the Behemoth's
     * strongest 4.2), so they catch an outlier without moving anything else.
     */
    private static Floor floor(Dim dim) {
        return switch (dim) {
            case OVERWORLD -> new Floor(25, 8, 5);
            case NETHER -> new Floor(30, 13, 5);
            case END -> new Floor(25, 8, 5);
        };
    }

    /**
     * Mobs whose failed counter costs something other than their own normal hit, so the normal-hit floor doesn't ask it
     * to be a threat. Each still answers every ceiling, and the mechanic-blow floor checks a blow it has unless
     * {@link #NOT_ITS_BLOW} names it too.
     */
    private static final Map<String, String> NOT_ITS_HIT = Map.ofEntries(
            Map.entry("veinleech", "its cost is the vein's drain, a second at a time through armour"),
            Map.entry("siphonjelly", "its cost is the beneficial effect its sting steals"),
            Map.entry("cindermoth_swarm", "its cost is the burning, a second at a time"),
            Map.entry("levimoth", "its cost is the Levitation and the fall after it"),
            Map.entry("warpseed_bloom", "its cost is the seed's teleport"),
            Map.entry("riftmine_sapper", "its cost is the mine's launch and the fall after it"),
            Map.entry("exile_wraith", "its cost is the exile to other land"),
            Map.entry("abyss_angler", "its cost is the reel toward the edge"),
            Map.entry("vigil_owl", "its cost is the mark that calls the island's mobs on you"),
            Map.entry("hollowmind", "its cost is its host's hit, an Enderman's at +50 %"));

    /** Mobs whose mechanic blow is not where its cost lies, so the mechanic-blow floor doesn't ask it to be a threat. */
    private static final Map<String, String> NOT_ITS_BLOW = Map.ofEntries(
            Map.entry("warpseed_bloom", "its blow is the seed's 3, and the seed's cost is the teleport that comes with it"));

    /**
     * Mobs with no hit at all (attack 0, and none taken from the player), and why: the floors and ceilings on a normal
     * hit have nothing to ask of them. A mechanic blow they have is still checked.
     */
    private static final Map<String, String> NO_HIT = Map.ofEntries(
            Map.entry("magma_tortoise", "a passive creature that never attacks: a lava ferry"),
            Map.entry("woolcap_grazer", "a passive creature: a scared herd's stampede only throws (Shellward Charm)"),
            Map.entry("chorus_grazer", "a passive creature: a startled herd only blinks away"),
            Map.entry("pearlshell_snail", "a passive creature that never attacks"),
            Map.entry("driftsong", "a passive creature: its song gives Slow Falling"),
            Map.entry("phantasm_lantern", "it never strikes: its cost is the harmless illusions that hide the real mobs round it"),
            Map.entry("mending_crystalling", "it never strikes: its cost is the End mobs its beam heals"));

    /**
     * The End reference player's netherite sword as a Facet Doppel copies it: a bare hand's 1, the sword's 3 and
     * netherite's 4, no enchantments.
     */
    private static final float COPIED_SWORD = 1 + 3 + 4;

    /**
     * Its normal hit. The row's attack, except where a mob takes its hit from somewhere else: a Facet Doppel whose
     * counter failed swings the End reference player's own sword at {@link FacetDoppel#STRIKE} of it, and a Void
     * Bloom's stain lands {@link VoidBloom#STAIN_DAMAGE} once a second on whoever stands in it.
     */
    private static float attack(Entry mob) {
        return switch (mob.id()) {
            case "facet_doppel" -> COPIED_SWORD * FacetDoppel.STRIKE;
            case "void_bloom" -> VoidBloom.STAIN_DAMAGE;
            default -> mob.balanceAttack();
        };
    }

    /** A normal hit that armour and Protection don't stop: the Void Bloom's stain. */
    private static boolean unstoppable(Entry mob) {
        return mob.id().equals("void_bloom");
    }

    /** Its armour: the row's, except a Facet Doppel whose counter failed, which wears the reference player's, copied. */
    private static float armor(Entry mob) {
        return mob.id().equals("facet_doppel") ? ref(mob.dim()).armor() : mob.armor();
    }

    /** The share of the reference player's hits a mob takes: damage it shrugs off, or time it can't be hit. */
    private static float taken(Entry mob) {
        return switch (mob.id()) {
            case "mote_swarm" -> MoteSwarm.SINGLE_TARGET_DAMAGE;                        // a sword hit passes through the cloud
            case "blackstone_scuttler" -> BlackstoneScuttler.SHELL_FACTOR;              // a sword, not a pickaxe
            case "phasewalker" -> Phasewalker.SOLID_TICKS / (float) (Phasewalker.SOLID_TICKS + Phasewalker.PHASED_TICKS);
            case "scarlet_mantis" -> 1.0F - ScarletMantis.STANCE_TICKS / (float) ScarletMantis.STANCE_EVERY; // parried
            default -> 1.0F;
        };
    }

    /** The part of a mob's strongest blow that goes through armour (Protection still counts). */
    private static float pierce(Entry mob) {
        return mob.id().equals("grand_orrery") ? GrandOrrery.MOTE_PIERCE : 0.0F;
    }

    /**
     * A mechanic blow made of many small hits, each its attack through armour: how many land in one. Such a mob's only
     * attack is that blow, so its floor is the blow's.
     */
    private static int surgeHits(Entry mob) {
        return mob.id().equals("mote_swarm") ? MoteSwarm.MOTES : 0;
    }

    /** Every row, plus the stronger forms a row's numbers leave out: the pack alphas. */
    private static List<Entry> everyForm() {
        Entry hound = RosterStats.get("gloamhound"), jackal = RosterStats.get("riftjackal");
        return Stream.concat(RosterStats.ALL.stream(), Stream.of(
                form(hound, "gloamhound alpha", Gloamhound.ALPHA_HEALTH, Gloamhound.ALPHA_ATTACK),
                form(jackal, "riftjackal alpha", Riftjackal.ALPHA_HEALTH, jackal.balanceAttack()))).toList();
    }

    private static Entry form(Entry e, String id, float health, float attack) {
        return new Entry(e.number(), id, e.dim(), e.cat(), e.apex(), health, attack, e.armor(), e.width(), e.height(),
                e.tellTicks(), e.blow(), health, attack, e.weight(), e.groupMin(), e.groupMax(), e.secondLook(),
                e.material(), e.place(), e.anyLight(), e.maxY(), e.spawner(), e.surface());
    }

    static float afterArmor(float damage, float armor, float toughness) {
        float reduction = Math.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        return damage * (1.0F - reduction / 25.0F);
    }

    static float toPlayer(float damage, Ref ref) {
        return toPlayer(damage, 0.0F, ref);
    }

    /** A hit of {@code damage}, {@code pierce} of it through armour, on the reference player. */
    static float toPlayer(float damage, float pierce, Ref ref) {
        return (afterArmor(damage - pierce, ref.armor, ref.toughness) + pierce) * (1.0F - Math.min(20.0F, ref.epf) / 25.0F);
    }

    static float secondsToKill(Entry mob) {
        float dealt = afterArmor(ref(mob.dim()).hit, armor(mob), 0) * taken(mob);
        return mob.balanceHealth() / (dealt * SWINGS_PER_SECOND);
    }

    static float hitsToDie(float damage, Ref ref) {
        return 20.0F / toPlayer(damage, ref);
    }

    /** Hits of its normal attack the reference player survives, with mob damage times {@code scale}. */
    private static float normalHits(Entry mob, Ref ref, float scale) {
        float hit = attack(mob) * scale;
        return 20.0F / (unstoppable(mob) ? hit : toPlayer(hit, ref));
    }

    /**
     * Its strongest blow (its mechanic blow, or an apex's attack if that is bigger) that many times survived, with mob
     * damage times {@code scale}; infinite for a mob with none.
     */
    private static float strongestBlows(Entry mob, Ref ref, float scale) {
        if (surgeHits(mob) > 0) return 20.0F / (surgeHits(mob) * toPlayer(attack(mob) * scale, ref));
        float blow = mob.blow(), strongest = Math.max(blow, mob.apex() ? attack(mob) : 0);
        if (strongest <= 0) return Float.POSITIVE_INFINITY;
        float pierce = strongest == blow ? pierce(mob) : 0.0F;
        return 20.0F / toPlayer(strongest * scale, pierce * scale, ref);
    }

    private static float hardestHit(Dim dim) {
        return (float) RosterStats.ALL.stream().filter(e -> e.dim() == dim && !e.apex()).mapToDouble(BalanceTest::attack).max().orElse(0);
    }

    /** Why the floors leave {@code mob}'s normal hit out; null when they check it. */
    private static String hitLeftOut(Entry mob) {
        if (mob.apex()) return "an apex: the apex floor checks its strongest blow instead";
        if (surgeHits(mob) > 0) return "its only attack is its surge, checked as its mechanic blow";
        if (NOT_ITS_HIT.containsKey(mob.id())) return NOT_ITS_HIT.get(mob.id());
        return attack(mob) > 0 ? null : NO_HIT.get(mob.id());
    }

    /** Why the floors leave {@code mob}'s mechanic blow out; null when they check it. */
    private static String blowLeftOut(Entry mob) {
        if (!Float.isFinite(strongestBlows(mob, ref(mob.dim()), 1.0F))) return NO_BLOW;
        return NOT_ITS_BLOW.get(mob.id());
    }

    private static final String NO_BLOW = "it has no mechanic blow";

    @Test
    void formulaMatchesTheMddExample() {
        // Iron armour turns Dripfang's 7 into 3.78, so 20 health takes 5.3 hits
        assertEquals(3.78F, afterArmor(7, 15, 0), 0.005F);
        assertEquals(5.29F, hitsToDie(7, ref(Dim.OVERWORLD)), 0.01F);
        // a share through armour skips the armour step only: 6 + 10 on the End player is 0.54 + 3.6
        assertEquals(4.14F, toPlayer(16, 10, ref(Dim.END)), 0.01F);
    }

    @Test
    void theReferencePlayerKillsEveryMobInTime() {
        List<String> slow = new ArrayList<>();
        for (Entry mob : everyForm()) {
            float limit = mob.apex() ? 60 : 12;
            float seconds = secondsToKill(mob);
            if (seconds > limit) slow.add("%s %.1f s > %.0f s".formatted(mob.id(), seconds, limit));
        }
        assertTrue(slow.isEmpty(), "too slow to kill: " + slow);
    }

    @Test
    void theReferencePlayerSurvivesEnoughHits() {
        List<String> deadly = new ArrayList<>();
        for (Entry mob : everyForm()) {
            Ref ref = ref(mob.dim());
            if (!mob.apex() && attack(mob) > 0) {
                float need = attack(mob) >= hardestHit(mob.dim()) ? 5 : 7;
                float hits = normalHits(mob, ref, 1.0F);
                if (hits < need) deadly.add("%s normal hit: %.1f < %.0f".formatted(mob.id(), hits, need));
            }
            float blows = strongestBlows(mob, ref, 1.0F);
            if (blows < 3) deadly.add("%s strongest blow: %.1f < 3".formatted(mob.id(), blows));
        }
        assertTrue(deadly.isEmpty(), "too deadly: " + deadly);
    }

    /**
     * Failing a counter costs something: the reference player dies to a dimension's ordinary hits, mechanic blows and
     * apex blows within its floor (End: 25 ordinary hits, 8 mechanic blows, 5 of an apex's strongest).
     */
    @Test
    void everyHitAndBlowIsAThreat() {
        List<String> harmless = new ArrayList<>();
        for (Entry mob : everyForm()) {
            Ref ref = ref(mob.dim());
            Floor floor = floor(mob.dim());
            if (hitLeftOut(mob) == null) {
                float hits = normalHits(mob, ref, 1.0F);
                if (hits > floor.normalHits()) harmless.add("%s normal hit: %.1f > %.0f".formatted(mob.id(), hits, floor.normalHits()));
            }
            if (blowLeftOut(mob) == null) {
                float blows = strongestBlows(mob, ref, 1.0F);
                float most = mob.apex() ? floor.apexBlows() : floor.mechanicBlows();
                if (blows > most) harmless.add("%s strongest blow: %.1f > %.0f".formatted(mob.id(), blows, most));
            }
        }
        assertTrue(harmless.isEmpty(), "costs too little to take: " + harmless);
    }

    /**
     * Every exception names a mob it fits, with its reason: one left out of the normal-hit floor has a hit to leave out,
     * one with no hit really has none, one left out of the blow floor has a blow.
     */
    @Test
    void theFloorsExceptionsAreRealMobs() {
        for (var e : NOT_ITS_HIT.entrySet()) {
            Entry mob = RosterStats.get(e.getKey());
            assertTrue(attack(mob) > 0 && !mob.apex(), e.getKey() + " has a normal hit to leave out");
            assertFalse(e.getValue().isBlank(), e.getKey() + " says why");
        }
        for (var e : NO_HIT.entrySet()) {
            Entry mob = RosterStats.get(e.getKey());
            assertTrue(attack(mob) == 0 && !mob.apex() && surgeHits(mob) == 0, e.getKey() + " has no hit of its own");
            assertFalse(e.getValue().isBlank(), e.getKey() + " says why");
        }
        for (var e : NOT_ITS_BLOW.entrySet()) {
            Entry mob = RosterStats.get(e.getKey());
            assertTrue(mob.blow() > 0, e.getKey() + " has a mechanic blow to leave out");
            assertFalse(e.getValue().isBlank(), e.getKey() + " says why");
        }
    }

    /**
     * The floors never skip a mob in silence: every hit and blow they leave out, on every row and alpha, has its reason,
     * and the list of them is printed with the test's output.
     */
    @Test
    void theFloorsNameEveryMobTheyLeaveOut() {
        Map<String, String> leftOut = new LinkedHashMap<>();
        List<String> unnamed = new ArrayList<>();
        for (Entry mob : everyForm()) {
            String hit = hitLeftOut(mob), blow = blowLeftOut(mob);
            if (hit == null && attack(mob) <= 0) unnamed.add(mob.id() + " has no hit and no reason in NO_HIT");
            if (hit != null) leftOut.put(mob.id() + " normal hit", hit);
            if (blow != null && !blow.equals(NO_BLOW)) leftOut.put(mob.id() + " mechanic blow", blow);
        }
        assertTrue(unnamed.isEmpty(), "left out without a reason: " + unnamed);
        leftOut.forEach((what, why) -> System.out.println("the floors leave out " + what + ": " + why));
    }

    /**
     * Inside a Nullcantor's ring the End reference player's Protection does nothing, and every End mob can share an
     * island with one. A floor-strength blow (8 outside) is then about 2.9, so the ring is a cost of its own; the ring
     * still never makes a blow nearly a kill: every End mob's hit and blow is survived at least 2.5 times inside it, an
     * apex's strongest at least 1.5 times.
     */
    @Test
    void theEndPlayerSurvivesMechanicBlowsInsideANullingRing() {
        Ref nulled = new Ref(8.0F, 20, 12, 0);   // the sword's Sharpness and the set's Protection both silenced
        List<String> deadly = new ArrayList<>();
        for (Entry mob : RosterStats.ALL) {
            if (mob.dim() != Dim.END) continue;
            float least = mob.apex() ? 1.5F : 2.5F;
            float blows = Math.min(strongestBlows(mob, nulled, 1.0F), attack(mob) > 0 ? normalHits(mob, nulled, 1.0F) : Float.POSITIVE_INFINITY);
            if (blows < least) deadly.add("%s %.2f < %.1f".formatted(mob.id(), blows, least));
        }
        assertTrue(deadly.isEmpty(), "too deadly inside a nulling ring: " + deadly);
    }

    /**
     * The targets are for Normal. On Hard every mob hits half again as hard, as vanilla's do; even so no hit or blow
     * in the roster takes two thirds of the reference player's health at once.
     */
    @Test
    void onHardNoBlowIsNearlyAKill() {
        List<String> deadly = new ArrayList<>();
        for (Entry mob : everyForm()) {
            Ref ref = ref(mob.dim());
            if (attack(mob) > 0) {
                float hits = normalHits(mob, ref, HARD);
                if (hits < 1.5F) deadly.add("%s normal hit on Hard: %.2f".formatted(mob.id(), hits));
            }
            float blows = strongestBlows(mob, ref, HARD);
            if (blows < 1.5F) deadly.add("%s strongest blow on Hard: %.2f".formatted(mob.id(), blows));
        }
        assertTrue(deadly.isEmpty(), "near one-shots on Hard: " + deadly);
    }

    @Test
    void ordinaryMobsWinTheDuelRatio() {
        List<String> lost = new ArrayList<>();
        for (Entry mob : everyForm()) {
            if (mob.apex() || attack(mob) <= 0) continue;
            float ratio = normalHits(mob, ref(mob.dim()), 1.0F) / secondsToKill(mob);
            if (ratio < 1.4F) lost.add("%s %.2f".formatted(mob.id(), ratio));
        }
        assertTrue(lost.isEmpty(), "duel ratio under 1.4: " + lost);
    }

    @Test
    void rosterShape() {
        assertEquals(90, RosterStats.ALL.size());
        assertEquals(20, RosterStats.ALL.stream().filter(e -> e.dim() == Dim.OVERWORLD).count());
        assertEquals(30, RosterStats.ALL.stream().filter(e -> e.dim() == Dim.NETHER).count());
        assertEquals(40, RosterStats.ALL.stream().filter(e -> e.dim() == Dim.END).count());
        assertEquals(3, RosterStats.ALL.stream().filter(Entry::apex).count());
        assertEquals(177, RosterStats.ALL.stream().mapToInt(e -> e.secondLook().isEmpty() ? 1 : 2).sum());
    }
}
