package com.panda.teeming.neoforge;

import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Player attachments on NeoForge. */
public final class NeoForgeAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TeemingMod.MODID);

    /** The Field Guide: saved, kept on death, synced to the owning player only. */
    public static final Supplier<AttachmentType<FieldGuideProgress>> FIELD_GUIDE = ATTACHMENTS.register("field_guide",
            () -> AttachmentType.builder(() -> FieldGuideProgress.EMPTY)
                    .serialize(FieldGuideProgress.CODEC.fieldOf("progress"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, FieldGuideProgress.STREAM_CODEC)
                    .build());

    private NeoForgeAttachments() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
