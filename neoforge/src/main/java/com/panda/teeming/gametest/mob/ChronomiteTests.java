package com.panda.teeming.gametest.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.gametest.TestPlayers;
import com.panda.teeming.mob.MechanicEvent;
import com.panda.teeming.mob.end.Chronomite;
import com.panda.teeming.mob.end.Chronomite.Step;
import com.panda.teeming.registry.ModMobs;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Chronomite: while its shell holds, its field of radius 6 (a pig whose nearest edge is 5.8 off yes, one at 6.3 no)
 * slows everything inside to x0.4 (a player's speed 0.1 to 0.04, a pig's 0.25 to 0.1), projectiles to x0.4 (and back
 * to full speed once out, or as one strikes: a slowed arrow still hits a pig for 2, not 1), and attack speed to x0.6
 * (a player's 4.0 to 2.4); a creative player is left alone. Its 20-point shell takes every blow first (8 leaves 12 and
 * its health whole; a blow past the shell reaches its health through armour 6), its cracks growing through stages 1 to
 * 3; when it cracks the field ends that tick and stays gone, also across a save. The bite: an 8-tick tell, then 8.5 to
 * whatever is still in reach. Health 26, attack 8.5, armour 6, 0.9 x 0.7; End midlands and barrens, weight 4, alone.
 * Every slowing of a body outside this arena is vetoed. Every number is checked against a plain value.
 */
public final class ChronomiteTests {
    private ChronomiteTests() {}

    /** The middle of the arena: the 6-block field just reaches its edges. */
    private static final Vec3 AT = new Vec3(6.5, 1, 6.5);
    /** A corner spot: the field's far side reaches 9.5 across, leaving room outside it on the arena's floor. */
    private static final Vec3 CORNER = new Vec3(3.5, 1, 3.5);

    public static void register(BiConsumer<String, Consumer<GameTestHelper>> out) {
        out.accept("chronomite_tell", ChronomiteTests::tell);
        out.accept("chronomite_counter", ChronomiteTests::counter);
        out.accept("chronomite_failure", ChronomiteTests::failure);
        out.accept("chronomite_radius_6", ChronomiteTests::radius);
        out.accept("chronomite_release", ChronomiteTests::release);
        out.accept("chronomite_projectiles", ChronomiteTests::projectiles);
        out.accept("chronomite_full_force", ChronomiteTests::fullForce);
        out.accept("chronomite_shell_first", ChronomiteTests::shellFirst);
        out.accept("chronomite_shell_overflow", ChronomiteTests::shellOverflow);
        out.accept("chronomite_cracks_grow", ChronomiteTests::cracksGrow);
        out.accept("chronomite_spares_creative", ChronomiteTests::sparesCreative);
        out.accept("chronomite_field_cancel", ChronomiteTests::fieldCancel);
        out.accept("chronomite_bite_reach", ChronomiteTests::biteReach);
        out.accept("chronomite_save_load", ChronomiteTests::saveLoad);
        out.accept("chronomite_stats", ChronomiteTests::stats);
        out.accept("chronomite_home", ChronomiteTests::home);
    }

    // ---- set-up --------------------------------------------------------------------------------------------------------

    /**
     * A Chronomite at {@code at} with the base look, kept to this arena ({@link #contain}).
     */
    private static Chronomite mite(GameTestHelper helper, Vec3 at) {
        Chronomite m = helper.spawn(ModMobs.CHRONOMITE.get(), at);
        m.core().setLook("base", true);
        contain(helper, m);
        return m;
    }

    /**
     * Takes away its goals, gives it a follow range of 1 (it notices nothing past its own arena), vetoes its field for any
     * body outside this arena, and removes it when the test ends.
     */
    private static void contain(GameTestHelper helper, Chronomite m) {
        m.removeFreeWill();
        m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0);
        Consumer<MechanicEvent> keep = e -> {
            if (e.mob() == m && e.step().equals("slow") && e.target() != null && !helper.getBounds().contains(e.target().position())) e.cancel();
        };
        MechanicEvent.listen(keep);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(keep));
        TestPlayers.whenDone(helper, m::discard);
    }

    /** A still pig with 100 health, removed when the test ends. */
    private static Pig pig(GameTestHelper helper, double x, double y, double z) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, y, z));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        TestPlayers.whenDone(helper, pig::discard);
        return pig;
    }

    /** An arrow at {@code at} with no gravity and no owner, flying at {@code motion}; removed when the test ends. */
    private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 motion) {
        Vec3 abs = helper.absoluteVec(at);
        Arrow arrow = new Arrow(helper.getLevel(), abs.x, abs.y, abs.z, new ItemStack(Items.ARROW), null);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(arrow);
        TestPlayers.whenDone(helper, arrow::discard);
        return arrow;
    }

    private static double speed(LivingEntity e) {
        return e.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    private static double attackSpeed(LivingEntity e) {
        return e.getAttributeValue(Attributes.ATTACK_SPEED);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-6;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    // ---- the bite ------------------------------------------------------------------------------------------------------

    /**
     * The tell holds exactly 8 ticks, still and harmless to the pig in reach; then the bite takes 8.5
     * from it (armour 0 takes all 8.5).
     */
    private static void tell(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        Pig pig = pig(helper, 6.5, 1, 7.6);
        m.setTarget(pig);
        Vec3 start = m.position();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (!m.core().in(Step.TELL)) return;
            ticks[0]++;
            helper.assertTrue(pig.getHealth() >= 100.0F, "the tell hurts nothing");
            helper.assertTrue(m.bites() == 0, "no bite yet");
            helper.assertTrue(flat(m.position(), start) < 0.05, "it holds still while it rears up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(m.bites() == 1, "the bite landed, bites " + m.bites() + ", phase " + m.phase());
            helper.assertTrue(ticks[0] == 8, "the tell lasts 8 ticks, saw " + ticks[0]);
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 8.5F) < 0.01F, "the bite takes 8.5, the pig lost " + lost);
        });
    }

    /** Stepping back out of reach during the tell: the bite snaps at the air. */
    private static void biteReach(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        Pig pig = pig(helper, 6.5, 1, 7.6);
        m.setTarget(pig);
        boolean[] moved = {false}, bit = {false};
        helper.onEachTick(() -> {
            if (m.core().in(Step.TELL) && !moved[0]) {
                pig.snapTo(pig.getX(), pig.getY(), pig.getZ() + 2.5);
                moved[0] = true;
            }
            if (m.core().in(Step.BITE)) bit[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bit[0], "it bit");
            helper.assertTrue(m.bites() == 0 && pig.getHealth() >= 100.0F, "at the air: the pig has " + pig.getHealth());
        });
    }

    // ---- the counter and the failure -----------------------------------------------------------------------------------

    /**
     * The counter: a survival player 3 blocks off is slowed; a blow of 20 empties the shell with its health still 26, and
     * that same tick the field is gone: the player is back to speed 0.1 and attack speed 4.0, the shell shows broken
     * (stage 4). Ten ticks later the field is still gone.
     */
    private static void counter(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(6.5, 1, 3.5));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(near(speed(player), 0.04), "slowed first, speed " + speed(player));
            m.hurtServer(helper.getLevel(), m.damageSources().playerAttack(player), 20.0F);
            helper.assertTrue(m.shell() == 0.0F && m.cracks() == 1, "the shell cracked, " + m.shell());
            helper.assertTrue(Math.abs(m.getHealth() - 26.0F) < 0.01F, "its health untouched, " + m.getHealth());
            helper.assertTrue(!m.fieldOpen(), "the field ended at once");
            helper.assertTrue(near(speed(player), 0.1), "the player's speed is back to 0.1 that tick, " + speed(player));
            helper.assertTrue(near(attackSpeed(player), 4.0), "attack speed back to 4.0, " + attackSpeed(player));
            helper.assertTrue(m.core().stage() == 4, "the shell shows broken, stage " + m.core().stage());
        });
        helper.runAtTickTime(13, () -> {
            helper.assertTrue(!m.fieldOpen() && near(speed(player), 0.1) && near(attackSpeed(player), 4.0), "the field stays gone");
            helper.assertTrue(m.slowedCount() == 0, "nothing slowed");
            helper.succeed();
        });
    }

    /**
     * Failing it: a survival player at its jaws inside the field moves at 0.04 (x0.4 of 0.1) and attacks at 2.4 (x0.6
     * of 4.0, 40 % slower), and is bitten for its attack of 8.5, scaled by difficulty as every mob's blow on a
     * player is.
     */
    private static void failure(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        ServerPlayer player = TestPlayers.survivor(helper, new Vec3(6.5, 1, 7.4));
        float before = player.getHealth();
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(near(speed(player), 0.04), "the player moves at 0.04, " + speed(player));
            helper.assertTrue(near(attackSpeed(player), 2.4), "and attacks at 2.4, " + attackSpeed(player));
            m.setTarget(player);
        });
        float expected = switch (helper.getLevel().getDifficulty()) {
            case PEACEFUL -> 0.0F;
            case EASY -> 5.25F;
            case NORMAL -> 8.5F;
            case HARD -> 12.75F;
        };
        helper.succeedWhen(() -> {
            helper.assertTrue(m.bites() == 1, "it bit the player");
            float lost = before - player.getHealth();
            helper.assertTrue(Math.abs(lost - expected) < 0.01F, "the bite takes " + expected + ", the player lost " + lost);
            helper.assertTrue(near(speed(player), 0.04), "still slowed");
        });
    }

    // ---- the field -----------------------------------------------------------------------------------------------------

    /** The reach is 6: a pig whose nearest edge is 5.8 off is slowed from 0.25 to 0.1, one at 6.3 keeps 0.25. */
    private static void radius(GameTestHelper helper) {
        Chronomite m = mite(helper, CORNER);
        Pig in = pig(helper, 9.75, 1, 3.5);
        Pig out = pig(helper, 3.5, 1, 10.25);
        helper.runAtTickTime(3, () -> {
            Vec3 feet = m.position();
            double inGap = Math.sqrt(in.getBoundingBox().distanceToSqr(feet)), outGap = Math.sqrt(out.getBoundingBox().distanceToSqr(feet));
            helper.assertTrue(Math.abs(inGap - 5.8) < 0.01 && Math.abs(outGap - 6.3) < 0.01, "edges at 5.8 and 6.3, " + inGap + " and " + outGap);
            helper.assertTrue(near(speed(in), 0.1), "the pig at 5.8 moves at 0.1, " + speed(in));
            helper.assertTrue(near(speed(out), 0.25), "the pig at 6.3 keeps 0.25, " + speed(out));
            helper.succeed();
        });
    }

    /** A slowed pig carried out of the field gets its own speed back the next tick: 0.1 inside, 0.25 outside. */
    private static void release(GameTestHelper helper) {
        mite(helper, CORNER);
        Pig pig = pig(helper, 6.5, 1, 3.5);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(near(speed(pig), 0.1), "slowed inside, " + speed(pig));
            pig.snapTo(helper.absoluteVec(new Vec3(10.5, 1, 10.5)));
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(near(speed(pig), 0.25), "back to 0.25 outside, " + speed(pig));
            helper.succeed();
        });
    }

    /**
     * Projectiles: an arrow inside the field flies at x0.4 the speed of the same arrow outside it, and once carried out
     * flies at full speed again.
     */
    private static void projectiles(GameTestHelper helper) {
        Chronomite m = mite(helper, CORNER);
        Arrow in = arrow(helper, new Vec3(3.5, 3, 6.5), new Vec3(0.1, 0, 0));
        Arrow out = arrow(helper, new Vec3(10.5, 3, 10.5), new Vec3(-0.1, 0, 0));
        helper.runAtTickTime(5, () -> {
            double ratio = in.getDeltaMovement().x / -out.getDeltaMovement().x;
            helper.assertTrue(Math.abs(ratio - 0.4) < 0.001, "inside it flies at x0.4, " + ratio);
            helper.assertTrue(m.heldShots() == 1, "the field holds one arrow, " + m.heldShots());
            in.snapTo(helper.absoluteVec(new Vec3(9.5, 3, 10.5)));
        });
        helper.runAtTickTime(8, () -> {
            double ratio = in.getDeltaMovement().x / -out.getDeltaMovement().x;
            helper.assertTrue(Math.abs(ratio - 1.0) < 0.001, "outside it flies at full speed again, " + ratio);
            helper.assertTrue(m.heldShots() == 0, "the field let it go");
            helper.succeed();
        });
    }

    /**
     * A slowed arrow strikes at full force: flying at 1 block a tick (2 damage at that speed, 1 at x0.4), it crawls
     * through the field and hits the pig for 2.
     */
    private static void fullForce(GameTestHelper helper) {
        Chronomite m = mite(helper, CORNER);
        Pig pig = pig(helper, 6.5, 1, 8.5);
        Arrow arrow = arrow(helper, new Vec3(6.5, 1.5, 4.5), new Vec3(0, 0, 1.0));
        boolean[] held = {false};
        helper.onEachTick(() -> {
            if (m.heldShots() == 1) held[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(held[0], "the field held the arrow back");
            helper.assertTrue(pig.getHealth() < 100.0F, "it hit");
            float lost = 100.0F - pig.getHealth();
            helper.assertTrue(Math.abs(lost - 2.0F) < 0.01F, "for 2, at full force, the pig lost " + lost);
            helper.assertTrue(m.strikesHastened() == 1, "time caught up as it struck");
        });
    }

    /** A creative player beside it is never slowed: speed 0.1, attack speed 4.0. */
    private static void sparesCreative(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.snapTo(helper.absoluteVec(new Vec3(6.5, 1, 4.5)));
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(near(speed(creative), 0.1) && near(attackSpeed(creative), 4.0), "left alone, " + speed(creative));
            helper.assertTrue(m.fieldOpen(), "though the field is on");
            helper.succeed();
        });
    }

    /** A vetoed "field" opens no field: the pig 2 blocks off keeps its 0.25. */
    private static void fieldCancel(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        Consumer<MechanicEvent> veto = e -> {
            if (e.mob() == m && e.step().equals("field")) e.cancel();
        };
        MechanicEvent.listen(veto);
        TestPlayers.whenDone(helper, () -> MechanicEvent.unlisten(veto));
        Pig pig = pig(helper, 4.5, 1, 6.5);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(!m.fieldOpen(), "no field");
            helper.assertTrue(near(speed(pig), 0.25), "the pig keeps 0.25, " + speed(pig));
            helper.succeed();
        });
    }

    // ---- the shell -----------------------------------------------------------------------------------------------------

    /** A blow of 8 goes to the shell: 12 points left, its health still 26, the field still on. */
    private static void shellFirst(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        Pig pig = pig(helper, 2.5, 1, 2.5);
        helper.runAtTickTime(2, () -> {
            m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(pig), 8.0F);
            helper.assertTrue(Math.abs(m.shell() - 12.0F) < 0.01F, "12 left in the shell, " + m.shell());
            helper.assertTrue(Math.abs(m.getHealth() - 26.0F) < 0.01F, "its health untouched, " + m.getHealth());
            helper.assertTrue(m.fieldOpen() && m.cracks() == 0, "the field holds");
            helper.succeed();
        });
    }

    /**
     * A blow past the shell: 26 on a whole shell, 20 taken there and the other 6 through its armour of 6 (6 x (1 - 3/25)
     * = 5.28): health 20.72, and the field ends.
     */
    private static void shellOverflow(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        Pig pig = pig(helper, 2.5, 1, 2.5);
        helper.runAtTickTime(2, () -> {
            m.hurtServer(helper.getLevel(), m.damageSources().mobAttack(pig), 26.0F);
            helper.assertTrue(m.shell() == 0.0F && m.cracks() == 1, "the shell took 20 and cracked");
            helper.assertTrue(Math.abs(m.getHealth() - 20.72F) < 0.01F, "health 20.72, " + m.getHealth());
            helper.assertTrue(!m.fieldOpen(), "the field ended");
            helper.succeed();
        });
    }

    /**
     * The cracks grow as the shell drains: whole (stage 0) at 20, stage 1 at 19, 2 at 13, 3 at 6, broken (4) at 0 with
     * the field gone and the shatter under way.
     */
    private static void cracksGrow(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.core().stage() == 0 && m.fieldOpen(), "whole, the field on");
            float[] points = {19.0F, 13.0F, 6.0F};
            for (int i = 0; i < points.length; i++) {
                m.setShell(points[i]);
                helper.assertTrue(m.core().stage() == i + 1, "stage " + (i + 1) + " at " + points[i] + ", got " + m.core().stage());
                helper.assertTrue(Chronomite.crackStage(points[i]) == i + 1, "the stage rule agrees");
            }
            helper.assertTrue(m.fieldOpen(), "a cracked shell that holds keeps the field");
            m.setShell(0.0F);
            helper.assertTrue(m.core().stage() == 4 && !m.fieldOpen() && m.core().in(Step.SHATTER), "broken, the field gone, shattering");
            helper.assertTrue(Chronomite.crackStage(20.0F) == 0 && Chronomite.crackStage(0.0F) == 4, "whole at 20, broken at 0");
            helper.succeed();
        });
    }

    /**
     * A shell saved at 12 comes back at 12 with its field on again; a cracked one comes back cracked (stage 4), its field
     * never reopening.
     */
    private static void saveLoad(GameTestHelper helper) {
        Chronomite a = mite(helper, new Vec3(3.5, 1, 6.5));
        Chronomite b = mite(helper, new Vec3(9.5, 1, 6.5));
        Chronomite[] copies = {null, null};
        helper.runAtTickTime(2, () -> {
            a.setShell(12.0F);
            b.setShell(0.0F);
            copies[0] = copy(helper, a);
            copies[1] = copy(helper, b);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(Math.abs(copies[0].shell() - 12.0F) < 0.01F && copies[0].fieldOpen(), "12 left, the field on, " + copies[0].shell());
            helper.assertTrue(copies[1].shell() == 0.0F && !copies[1].fieldOpen() && copies[1].core().stage() == 4, "still cracked, no field");
            helper.succeed();
        });
    }

    private static Chronomite copy(GameTestHelper helper, Chronomite from) {
        ServerLevel level = helper.getLevel();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        from.saveWithoutId(out);
        CompoundTag tag = out.buildResult();
        tag.remove("UUID");
        Chronomite c = ModMobs.CHRONOMITE.get().create(level, EntitySpawnReason.LOAD);
        c.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        from.discard();
        contain(helper, c);
        level.addFreshEntity(c);
        return c;
    }

    // ---- stats and home ---------------------------------------------------------------------------------------------

    /** Health 26, attack 8.5, armour 6, a 0.9 x 0.7 hitbox, a 20-point shell. */
    private static void stats(GameTestHelper helper) {
        Chronomite m = mite(helper, AT);
        m.setNoAi(true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(m.getMaxHealth() == 26.0F && m.getHealth() == 26.0F, "health 26, got " + m.getHealth() + "/" + m.getMaxHealth());
            helper.assertTrue(m.getAttributeValue(Attributes.ATTACK_DAMAGE) == 8.5, "attack 8.5, got " + m.getAttributeValue(Attributes.ATTACK_DAMAGE));
            helper.assertTrue(m.getArmorValue() == 6, "armour 6, got " + m.getArmorValue());
            helper.assertTrue(Math.abs(m.getBbWidth() - 0.9F) < 0.01F && Math.abs(m.getBbHeight() - 0.7F) < 0.01F,
                    "a 0.9 x 0.7 hitbox, got " + m.getBbWidth() + " x " + m.getBbHeight());
            helper.assertTrue(m.shell() == 20.0F, "a 20-point shell, got " + m.shell());
            helper.succeed();
        });
    }

    /** Home: the End midlands and barrens, never the highlands or the main island; weight 4, alone; a monster. */
    private static void home(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(TeemingSpawns.tag("chronomite"));
        helper.assertTrue(tag.isPresent(), "it has a biome tag");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_MIDLANDS)), "its home holds the End midlands");
        helper.assertTrue(tag.get().stream().anyMatch(b -> b.is(Biomes.END_BARRENS)), "and the End barrens");
        helper.assertTrue(tag.get().stream().noneMatch(b -> b.is(Biomes.END_HIGHLANDS) || b.is(Biomes.THE_END)), "never the highlands or the main island");
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, TeemingMod.id("spawns/chronomite")));
        helper.assertTrue(modifier.isPresent() && modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, "it has a spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = ((BiomeModifiers.AddSpawnsBiomeModifier) modifier.get().value()).spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModMobs.CHRONOMITE.get()).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the modifier spawns it");
        helper.assertTrue(entry.weight() == 4, "spawn weight 4, got " + entry.weight());
        helper.assertTrue(entry.value().count().minInclusive() == 1 && entry.value().count().maxInclusive() == 1,
                "alone, got " + entry.value().count().minInclusive() + ".." + entry.value().count().maxInclusive());
        helper.assertTrue(ModMobs.CHRONOMITE.get().getCategory() == MobCategory.MONSTER, "a monster");
        helper.succeed();
    }
}
