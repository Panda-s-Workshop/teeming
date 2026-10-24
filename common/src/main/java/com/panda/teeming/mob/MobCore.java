package com.panda.teeming.mob;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.mob.look.LookDef;
import com.panda.teeming.mob.look.Looks;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The state every Teeming mob shares, whatever vanilla class it extends: the phase machine (server-authoritative,
 * mirrored on clients for animation), the look, and a few visual flags. The base classes ({@link TeemingMonster},
 * {@link TeemingAnimal}) own the synced data keys and hand them in.
 *
 * <p>The phase is synced as one int: ordinal (8 bits) | restart counter (4 bits) | length in ticks (the rest), so a
 * phase restarted with the same length still reaches clients, and phase and length always arrive together.
 */
public final class MobCore {
    private static final Identifier LOOK_MODIFIER = TeemingMod.id("look");

    private final Mob mob;
    private final Phase[] phases;
    private final EntityDataAccessor<Integer> phaseKey;
    private final EntityDataAccessor<String> lookKey;
    private final EntityDataAccessor<Integer> flagsKey;
    private final PhaseClock clock = new PhaseClock();
    private int restarts;
    private @Nullable LivingEntity target;

    public MobCore(Mob mob, Phase[] phases, EntityDataAccessor<Integer> phaseKey, EntityDataAccessor<String> lookKey,
                   EntityDataAccessor<Integer> flagsKey) {
        if (phases.length == 0 || phases.length > 255) throw new IllegalArgumentException("1..255 phases, got " + phases.length);
        this.mob = mob;
        this.phases = phases;
        this.phaseKey = phaseKey;
        this.lookKey = lookKey;
        this.flagsKey = flagsKey;
    }

    /** The base classes call this from defineSynchedData. */
    public static void define(SynchedEntityData.Builder builder, EntityDataAccessor<Integer> phaseKey, EntityDataAccessor<String> lookKey,
                              EntityDataAccessor<Integer> flagsKey) {
        builder.define(phaseKey, 0);
        builder.define(lookKey, "");
        builder.define(flagsKey, 0);
    }

    // ---- phases ------------------------------------------------------------------------------------------------

    /**
     * Server: enters a phase for {@code length} ticks (0 = until {@link #end()} or another start). Posts a
     * {@link MechanicEvent} first, except for the idle phase; returns false and changes nothing if it was cancelled.
     */
    public boolean start(Phase phase, int length, @Nullable LivingEntity target) {
        if (phase.ordinal() != 0 && !MechanicEvent.post(mob, phase.step(), target)) return false;
        this.target = target;
        clock.start(phase.ordinal(), length);
        restarts = (restarts + 1) & 0xF;
        mob.getEntityData().set(phaseKey, pack(phase.ordinal(), restarts, clock.length()));
        return true;
    }

    /** Server: back to idle, without an event. */
    public void end() {
        start(phases[0], 0, null);
    }

    /** Both sides, every tick. Returns the phase that just ran out (server only), or null. */
    public @Nullable Phase tick() {
        Phase current = phase();
        boolean ranOut = clock.tick();
        return ranOut && !mob.level().isClientSide() ? current : null;
    }

    /** Client: a new phase value arrived. */
    public void onSynced(EntityDataAccessor<?> key) {
        if (!key.equals(phaseKey) || !mob.level().isClientSide()) return;
        int packed = mob.getEntityData().get(phaseKey);
        clock.start(Math.min(packed & 0xFF, phases.length - 1), packed >>> 12);
    }

    /** The mob's phases, idle first. */
    public Phase[] phases() {
        return phases.clone();
    }

    public Phase phase() {
        return phases[Math.min(clock.phase(), phases.length - 1)];
    }

    public boolean idle() {
        return clock.idle();
    }

    public boolean in(Phase phase) {
        return clock.phase() == phase.ordinal();
    }

    public int elapsed() {
        return clock.elapsed();
    }

    public int length() {
        return clock.length();
    }

    public float progress(float partialTick) {
        return clock.progress(partialTick);
    }

    /** Who the current phase is aimed at (server only; null on clients). */
    public @Nullable LivingEntity target() {
        return target;
    }

    static int pack(int ordinal, int restarts, int length) {
        return (ordinal & 0xFF) | (restarts & 0xF) << 8 | Math.min(length, 0xFFFFF) << 12;
    }

    // ---- look and flags ----------------------------------------------------------------------------------------

    public String look() {
        return mob.getEntityData().get(lookKey);
    }

    /** Sets the look and applies its scale and attribute multipliers; {@code fresh} also fills health (a new spawn). */
    public void setLook(String look, boolean fresh) {
        mob.getEntityData().set(lookKey, look);
        applyLook(fresh);
    }

    /** Server: picks a look for where the mob stands. Before any look data loads, the base look. */
    public void pickLook(LevelReader level) {
        List<LookDef> looks = Looks.of(mobId());
        if (looks.isEmpty()) {
            setLook(LookDef.BASE, true);
            return;
        }
        Holder<Biome> biome = level.getBiome(mob.blockPosition());
        LookDef pick = LookDef.pick(looks, spec -> inBiome(biome, spec), bound -> mob.getRandom().nextInt(bound));
        setLook(pick.id(), true);
    }

    /** Server: the look's modifiers, replacing any old ones (they are permanent modifiers, saved with the mob). */
    public void applyLook(boolean fresh) {
        if (mob.level().isClientSide()) return;
        LookDef def = Looks.get(mobId(), look()).orElse(null);
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(attribute -> {
            AttributeInstance instance = mob.getAttribute(attribute);
            if (instance != null) instance.removeModifier(LOOK_MODIFIER);
        });
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (def != null) {
            def.attributes().forEach((key, multiplier) -> {
                Identifier id = Identifier.tryParse(key);
                if (id == null || multiplier == 1.0) return;
                BuiltInRegistries.ATTRIBUTE.get(id).ifPresent(attribute -> {
                    AttributeInstance instance = mob.getAttribute(attribute);
                    if (instance != null) instance.addOrReplacePermanentModifier(
                            new AttributeModifier(LOOK_MODIFIER, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                });
            });
            if (scale != null && def.scale() != 1.0F) scale.addOrReplacePermanentModifier(
                    new AttributeModifier(LOOK_MODIFIER, def.scale() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        mob.refreshDimensions();
        if (fresh || mob.getHealth() > mob.getMaxHealth()) mob.setHealth(mob.getMaxHealth());
    }

    private static boolean inBiome(Holder<Biome> biome, String spec) {
        if (spec.startsWith("#")) {
            Identifier tag = Identifier.tryParse(spec.substring(1));
            return tag != null && biome.is(TagKey.create(Registries.BIOME, tag));
        }
        Identifier id = Identifier.tryParse(spec);
        return id != null && biome.is(id);
    }

    /** The mob's registry path: its roster id and its folder under looks/ and textures/entity/. */
    public String mobId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
    }

    /** A visual stage 0..15 kept in the top bits of the flags (heat, growth); picks a staged glowmask. */
    public int stage() {
        return flags() >>> 24 & 0xF;
    }

    public void setStage(int stage) {
        int flags = flags();
        mob.getEntityData().set(flagsKey, (flags & 0x00FFFFFF) | (Math.clamp(stage, 0, 15) << 24));
    }

    public int flags() {
        return mob.getEntityData().get(flagsKey);
    }

    public boolean flag(int bit) {
        return (mob.getEntityData().get(flagsKey) & 1 << bit) != 0;
    }

    public void setFlag(int bit, boolean on) {
        int flags = mob.getEntityData().get(flagsKey);
        mob.getEntityData().set(flagsKey, on ? flags | 1 << bit : flags & ~(1 << bit));
    }
}
