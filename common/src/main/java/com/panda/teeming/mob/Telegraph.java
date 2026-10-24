package com.panda.teeming.mob;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The particles and sound that go with a tell. The pose comes
 * from the model, which reads the synced phase; this is the rest, played by the server to everyone nearby.
 *
 * @param particle what to emit
 * @param count    how many per {@link #play}
 * @param spread   half-size of the box they appear in, in blocks
 * @param height   0..1 of the mob's height where the box is centred
 * @param speed    the particles' speed value
 * @param sound    played once per {@link #play}, or null
 */
public record Telegraph(ParticleOptions particle, int count, double spread, double height, double speed, @Nullable SoundEvent sound,
                        float volume, float pitch) {
    public static Telegraph particles(ParticleOptions particle, int count, double spread, double height) {
        return new Telegraph(particle, count, spread, height, 0.02, null, 1.0F, 1.0F);
    }

    public Telegraph withSound(SoundEvent sound, float volume, float pitch) {
        return new Telegraph(particle, count, spread, height, speed, sound, volume, pitch);
    }

    public Telegraph withSpeed(double speed) {
        return new Telegraph(particle, count, spread, height, speed, sound, volume, pitch);
    }

    /** Emits the particles (and the sound) around {@code at}. Server only; does nothing on a client. */
    public void play(Entity at) {
        if (!(at.level() instanceof ServerLevel level)) return;
        double y = at.getY() + at.getBbHeight() * height;
        level.sendParticles(particle, at.getX(), y, at.getZ(), count, spread, spread * 0.6, spread, speed);
        if (sound != null) level.playSound(null, at.getX(), y, at.getZ(), sound, at.getSoundSource(), volume, pitch);
    }

    /** Particles only, for the ticks of a held tell (a sound every tick would be noise). */
    public void trail(Entity at, int everyTicks) {
        if (everyTicks <= 0 || at.tickCount % everyTicks != 0 || !(at.level() instanceof ServerLevel level)) return;
        level.sendParticles(particle, at.getX(), at.getY() + at.getBbHeight() * height, at.getZ(), Math.max(1, count / 3), spread, spread * 0.6, spread, speed);
    }
}
