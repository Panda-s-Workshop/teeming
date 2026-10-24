package com.panda.teeming.trophy;

import com.panda.teeming.registry.ModBlockEntities;
import com.panda.teeming.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The mob and look a placed trophy shows: from the item when placed, back onto the item when broken, synced to clients. */
public class TrophyBlockEntity extends BlockEntity {
    private TrophyMob mob = TrophyMob.NONE;

    public TrophyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TROPHY.get(), pos, state);
    }

    public TrophyMob mob() {
        return mob;
    }

    public void setMob(TrophyMob mob) {
        this.mob = mob;
        setChanged();
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        mob = components.getOrDefault(ModComponents.TROPHY_MOB.get(), TrophyMob.NONE);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!mob.isEmpty()) components.set(ModComponents.TROPHY_MOB.get(), mob);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("trophy");
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!mob.isEmpty()) out.store("trophy", TrophyMob.CODEC, mob);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        mob = in.read("trophy", TrophyMob.CODEC).orElse(TrophyMob.NONE);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
