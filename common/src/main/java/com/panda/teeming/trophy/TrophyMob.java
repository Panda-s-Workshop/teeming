package com.panda.teeming.trophy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The {@code teeming:trophy_mob} component: which mob a trophy shows, in which look. */
public record TrophyMob(String mob, String look) {
    public static final TrophyMob NONE = new TrophyMob("", "");

    public static final Codec<TrophyMob> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("mob").forGetter(TrophyMob::mob),
            Codec.STRING.optionalFieldOf("look", "base").forGetter(TrophyMob::look)
    ).apply(i, TrophyMob::new));

    public static final StreamCodec<ByteBuf, TrophyMob> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TrophyMob::mob, ByteBufCodecs.STRING_UTF8, TrophyMob::look, TrophyMob::new);

    public boolean isEmpty() {
        return mob.isEmpty();
    }

    public TrophyMob withLook(String look) {
        return new TrophyMob(mob, look);
    }
}
