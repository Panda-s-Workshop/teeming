package com.panda.teeming.roster;

import static org.junit.jupiter.api.Assertions.*;

import com.panda.teeming.roster.RosterStats.Dim;
import com.panda.teeming.roster.RosterStats.Entry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every mob against its dimension's weak reference player, with vanilla's armour and Protection formulas
 * ({@code CombatRules}: armour − damage / (2 + toughness / 4), clamped to [armour / 5, 20], each point −4 %; EPF
 * clamped to 20, each point −4 %). Reads {@code RosterStats}, so every changed number is checked here.
 */
class BalanceTest {
    private static final float SWINGS_PER_SECOND = 1.6F;

    private record Ref(float hit, float armor, float toughness, float epf) {}

    private static Ref ref(Dim dim) {
        return switch (dim) {
            case OVERWORLD -> new Ref(6.0F, 15, 0, 0); // iron sword, iron set
            case NETHER -> new Ref(8.5F, 20, 8, 0); // diamond sword Sharpness II, diamond set
            case END -> new Ref(11.0F, 20, 12, 16); // netherite sword Sharpness V, netherite set Protection IV
        };
    }

    static float afterArmor(float damage, float armor, float toughness) {
        float reduction = Math.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        return damage * (1.0F - reduction / 25.0F);
    }

    static float toPlayer(float damage, Ref ref) {
        return afterArmor(damage, ref.armor, ref.toughness) * (1.0F - Math.min(20.0F, ref.epf) / 25.0F);
    }

    static float secondsToKill(Entry mob) {
        float dealt = afterArmor(ref(mob.dim()).hit, mob.armor(), 0);
        return mob.balanceHealth() / (dealt * SWINGS_PER_SECOND);
    }

    static float hitsToDie(float damage, Ref ref) {
        return 20.0F / toPlayer(damage, ref);
    }

    private static float hardestHit(Dim dim) {
        return (float) RosterStats.ALL.stream().filter(e -> e.dim() == dim && !e.apex()).mapToDouble(Entry::balanceAttack).max().orElse(0);
    }

    @Test
    void formulaMatchesTheMddExample() {
        // Iron armour turns Dripfang's 7 into 3.78, so 20 health takes 5.3 hits
        assertEquals(3.78F, afterArmor(7, 15, 0), 0.005F);
        assertEquals(5.29F, hitsToDie(7, ref(Dim.OVERWORLD)), 0.01F);
    }

    @Test
    void theReferencePlayerKillsEveryMobInTime() {
        List<String> slow = new ArrayList<>();
        for (Entry mob : RosterStats.ALL) {
            float limit = mob.apex() ? 60 : 12;
            float seconds = secondsToKill(mob);
            if (seconds > limit) slow.add("%s %.1f s > %.0f s".formatted(mob.id(), seconds, limit));
        }
        assertTrue(slow.isEmpty(), "too slow to kill: " + slow);
    }

    @Test
    void theReferencePlayerSurvivesEnoughHits() {
        List<String> deadly = new ArrayList<>();
        for (Entry mob : RosterStats.ALL) {
            Ref ref = ref(mob.dim());
            if (!mob.apex() && mob.balanceAttack() > 0) {
                float need = mob.balanceAttack() >= hardestHit(mob.dim()) ? 5 : 7;
                float hits = hitsToDie(mob.balanceAttack(), ref);
                if (hits < need) deadly.add("%s normal hit: %.1f < %.0f".formatted(mob.id(), hits, need));
            }
            float strongest = Math.max(mob.blow(), mob.apex() ? mob.balanceAttack() : 0);
            if (strongest > 0) {
                float blows = hitsToDie(strongest, ref);
                if (blows < 3) deadly.add("%s strongest blow: %.1f < 3".formatted(mob.id(), blows));
            }
        }
        assertTrue(deadly.isEmpty(), "too deadly: " + deadly);
    }

    /**
     * Inside a Nullcantor's ring the End reference player's Protection does nothing, and every End mob can share an
     * island with one: each End mob's mechanic blow is still survived at least 3 times with the armour alone.
     */
    @Test
    void theEndPlayerSurvivesMechanicBlowsInsideANullingRing() {
        Ref nulled = new Ref(8.0F, 20, 12, 0);   // the sword's Sharpness and the set's Protection both silenced
        List<String> deadly = new ArrayList<>();
        for (Entry mob : RosterStats.ALL) {
            if (mob.dim() != Dim.END) continue;
            float strongest = Math.max(mob.blow(), mob.balanceAttack());
            if (strongest <= 0) continue;
            float blows = hitsToDie(strongest, nulled);
            if (blows < 3) deadly.add("%s %.1f < 3".formatted(mob.id(), blows));
        }
        assertTrue(deadly.isEmpty(), "too deadly inside a nulling ring: " + deadly);
    }

    @Test
    void ordinaryMobsWinTheDuelRatio() {
        List<String> lost = new ArrayList<>();
        for (Entry mob : RosterStats.ALL) {
            if (mob.apex() || mob.balanceAttack() <= 0) continue;
            float ratio = hitsToDie(mob.balanceAttack(), ref(mob.dim())) / secondsToKill(mob);
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
