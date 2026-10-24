package com.panda.teeming.registry;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.platform.registry.DeferredHolder;
import com.panda.teeming.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

/** The mod's sound events; {@code assets/teeming/sounds.json} says what each one plays, so resource packs can remap any cue. */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, TeemingMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIMOTH_AMBIENT = sound("levimoth.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIMOTH_HURT = sound("levimoth.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIMOTH_DEATH = sound("levimoth.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIMOTH_FLARE = sound("levimoth.flare");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIMOTH_TELL = sound("levimoth.tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVIMOTH_RELEASE = sound("levimoth.release");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAGHEART_GOLEM_AMBIENT = sound("slagheart_golem.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAGHEART_GOLEM_HURT = sound("slagheart_golem.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAGHEART_GOLEM_DEATH = sound("slagheart_golem.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAGHEART_GOLEM_TELL = sound("slagheart_golem.tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAGHEART_GOLEM_ERUPT = sound("slagheart_golem.erupt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAGHEART_GOLEM_QUENCH = sound("slagheart_golem.quench");
    public static final DeferredHolder<SoundEvent, SoundEvent> THORNHORN_AMBIENT = sound("thornhorn.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> THORNHORN_HURT = sound("thornhorn.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> THORNHORN_DEATH = sound("thornhorn.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> THORNHORN_TELL = sound("thornhorn.tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> THORNHORN_RAM = sound("thornhorn.ram");
    public static final DeferredHolder<SoundEvent, SoundEvent> THORNHORN_STUNNED = sound("thornhorn.stunned");
    public static final DeferredHolder<SoundEvent, SoundEvent> THORNHORN_HORN_CRACK = sound("thornhorn.horn_crack");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(TeemingMod.id(name)));
    }
}
