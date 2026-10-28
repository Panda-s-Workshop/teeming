package com.panda.teeming.mob.end;

import com.panda.teeming.mob.Phase;
import com.panda.teeming.mob.Pin;
import com.panda.teeming.mob.TeemingAnimal;
import com.panda.teeming.mob.TeemingAttributes;
import com.panda.teeming.mob.overworld.VinedropStrangler;
import com.panda.teeming.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pearlshell Snail: a tiny, slow snail of the small End islands with a spiral shell. It never attacks.
 *
 * <p>A chorus fruit used on it starts a pearl growing in the mouth of its shell: the pearl is grown 6000 ticks later,
 * swelling through three stages on the way. It grows one pearl at a time: a snail with a pearl, growing or grown, won't
 * take another fruit. Shears on a snail with a grown pearl take exactly one ender pearl and leave the mouth empty, ready
 * for another fruit; on any other snail they do nothing and cost nothing. Dispensers shear it too.
 *
 * <p>Hurt, it snaps into its shell for 60 ticks: blows that land while it is shut do a fifth of their damage, it doesn't
 * move (it only falls) and it won't take food or give up its pearl until it comes out. The blow that shuts it lands in
 * full, and a blow while it is shut doesn't hold it in longer. The pearl keeps growing in there.
 *
 * <p>Nothing it is given is lost for good: a snail that dies drops its grown pearl, or gives back the chorus fruit
 * if the pearl was still growing. It doesn't breed: chorus fruit is the pearl's food, and one food meaning two things
 * would make every feeding a guess; the snails on an island are what it has.
 *
 * <p>Counter (it has no fight to counter): feed it, wait, and shear it calmly. Failing it, by hitting it, costs time:
 * three seconds shut in its shell, out of reach of food and shears.
 */
public class PearlshellSnail extends TeemingAnimal implements Shearable {
    /** IDLE, nibbling a chorus fruit, shut in its shell, giving up its pearl to the shears. */
    public enum Step implements Phase { IDLE, FEED, HIDE, HARVEST }

    /** A pearl takes 5 minutes to grow from the fruit to the shears. */
    public static final int GROW_TICKS = 6000;
    /** Hurt, it stays shut in its shell for 3 s. */
    public static final int HIDE_TICKS = 60;
    /** Blows that land while it is shut do this share of their damage. */
    public static final float HIDDEN_DAMAGE = 0.2F;
    /** Shears take exactly this many ender pearls from a grown pearl. */
    public static final int PEARLS_PER_HARVEST = 1;
    /** It nibbles the fruit for 1 s, and shakes after the shears for 1 s. */
    public static final int FEED_TICKS = 20;
    public static final int HARVEST_TICKS = 20;
    /** The pearl's visual stages: none, three while it grows (each a third of the growing time), and grown. */
    public static final int NO_PEARL = 0, GROWN_STAGE = 4, STAGES = 5;
    /** The growth counter's value when it holds no pearl. */
    public static final int NONE = -1;
    /** A grown pearl glints now and then, so it shows from across an island. */
    public static final int GLINT_EVERY = 40;

    private static final DustParticleOptions PEARL_DUST = new DustParticleOptions(0x2FA58D, 0.7F);

    /** Ticks the current pearl has grown ({@link #NONE} with no pearl, {@link #GROW_TICKS} once grown); saved. */
    private int growth = NONE;

    public PearlshellSnail(EntityType<? extends PearlshellSnail> type, Level level) {
        super(type, level, Step.values());
        xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TeemingAttributes.animal("pearlshell_snail", 0.12);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 4.0F) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return core().idle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return core().idle() && super.canContinueToUse();
            }
        });
    }

    // ---- the pearl -------------------------------------------------------------------------------------------------

    /** The pearl's growth as plain numbers, with no game types, so it can be checked on its own. */
    public static final class Growth {
        private Growth() {}

        /** The visual stage for a growth counter: 0 with no pearl, 1 to 3 while it grows (a third each), 4 grown. */
        public static int stage(int growth) {
            if (growth < 0) return NO_PEARL;
            if (growth >= GROW_TICKS) return GROWN_STAGE;
            return 1 + Math.min(2, growth * 3 / GROW_TICKS);
        }
    }

    /** Ticks the pearl has grown, {@link #NONE} with none (server side). */
    public int growth() {
        return growth;
    }

    /** True while it holds a pearl, growing or grown (server side). */
    public boolean hasPearl() {
        return growth >= 0;
    }

    /** True once the pearl is grown: from the counter on the server, from the synced stage on a client. */
    public boolean pearlGrown() {
        return level().isClientSide() ? core().stage() == GROWN_STAGE : growth >= GROW_TICKS;
    }

    /** True while it is shut in its shell (both sides). */
    public boolean hidden() {
        return core().in(Step.HIDE);
    }

    /** Server: sets the growth counter (clamped to {@link #NONE}..{@link #GROW_TICKS}) and the stage, quietly. Public as a test hook. */
    public void setGrowth(int ticks) {
        growth = Math.clamp(ticks, NONE, GROW_TICKS);
        core().setStage(Growth.stage(growth));
    }

    /**
     * Server: grows the pearl {@code ticks} further, as that many ticks of growing would, with the swell at each new
     * stage and the chime when it is grown. Nothing happens with no pearl. Public as a test hook.
     */
    public void advanceGrowth(int ticks) {
        if (growth < 0 || growth >= GROW_TICKS || ticks <= 0) return;
        int before = Growth.stage(growth);
        growth = (int) Math.min(GROW_TICKS, (long) growth + ticks);
        int after = Growth.stage(growth);
        if (after == before) return;
        core().setStage(after);
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 mouth = mouth();
        if (after == GROWN_STAGE) {
            level.sendParticles(ParticleTypes.END_ROD, mouth.x, mouth.y, mouth.z, 6, 0.15, 0.15, 0.15, 0.02);
            level.sendParticles(PEARL_DUST, mouth.x, mouth.y, mouth.z, 8, 0.12, 0.12, 0.12, 0.0);
            playSound(ModSounds.PEARLSHELL_SNAIL_RIPE.get(), 0.8F, 1.0F);
        } else {
            level.sendParticles(PEARL_DUST, mouth.x, mouth.y, mouth.z, 5, 0.1, 0.1, 0.1, 0.0);
            playSound(ModSounds.PEARLSHELL_SNAIL_SWELL.get(), 0.5F, 1.0F);
        }
    }

    /** Where the pearl sits: the mouth of the shell, low on its front. */
    private Vec3 mouth() {
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yBodyRot).scale(0.28);
        return position().add(ahead.x, 0.25, ahead.z);
    }

    /**
     * Server: eats one of {@code food} (a chorus fruit) from a player's hand and starts a pearl growing. False, with
     * nothing eaten, when it holds a pearl already, is shut in its shell or busy, or a listener vetoes the "feed" step.
     * Public as a test hook.
     */
    public boolean feed(Player player, ItemStack food) {
        if (level().isClientSide() || !isAlive() || !food.is(Items.CHORUS_FRUIT) || hasPearl() || !core().idle()) return false;
        if (!startPhase(Step.FEED, FEED_TICKS, player)) return false;
        food.consume(1, player);
        setGrowth(0);
        getNavigation().stop();
        playSound(ModSounds.PEARLSHELL_SNAIL_FED.get(), 1.0F, getVoicePitch());
        if (level() instanceof ServerLevel level) {
            Vec3 mouth = mouth();
            level.sendParticles(PEARL_DUST, mouth.x, mouth.y, mouth.z, 4, 0.08, 0.08, 0.08, 0.0);
        }
        return true;
    }

    /** Ready for the shears: alive, out of its shell, calm, with a grown pearl. Both sides. */
    @Override
    public boolean readyForShearing() {
        return isAlive() && pearlGrown() && core().idle();
    }

    /**
     * Server: the shears take the grown pearl, exactly {@link #PEARLS_PER_HARVEST} ender pearl, and the mouth is empty
     * again. Called by a player's shears and by a dispenser's. Nothing happens if it isn't ready or a listener vetoes the
     * "harvest" step.
     */
    @Override
    public void shear(ServerLevel level, SoundSource source, ItemStack shears) {
        if (!readyForShearing() || !startPhase(Step.HARVEST, HARVEST_TICKS, null)) return;
        setGrowth(NONE);
        getNavigation().stop();
        level.playSound(null, this, ModSounds.PEARLSHELL_SNAIL_HARVEST.get(), source, 1.0F, 1.0F);
        Vec3 mouth = mouth();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.ENDER_PEARL), mouth.x, mouth.y, mouth.z, 8, 0.1, 0.1, 0.1, 0.05);
        level.sendParticles(ParticleTypes.PORTAL, mouth.x, mouth.y, mouth.z, 10, 0.15, 0.15, 0.15, 0.2);
        ItemEntity drop = spawnAtLocation(level, new ItemStack(Items.ENDER_PEARL, PEARLS_PER_HARVEST), 0.3F);
        if (drop != null) drop.setDeltaMovement(drop.getDeltaMovement().add(0.0, 0.1, 0.0));
    }

    // ---- the shell ---------------------------------------------------------------------------------------------------

    /** Shut in its shell, it is rooted: it only falls, and nothing pushes it or knocks it back. */
    @Override
    public Pin pin() {
        return hidden() ? Pin.ROOTED : Pin.FREE;
    }

    /** A blow on a shut snail does {@link #HIDDEN_DAMAGE} of its damage; one that lands on an open snail shuts it. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableTo(level, source) || isDeadOrDying()) return super.hurtServer(level, source, amount);
        boolean shut = hidden();
        boolean hurt = super.hurtServer(level, source, shut ? amount * HIDDEN_DAMAGE : amount);
        if (hurt && !shut && isAlive()) hide(source.getEntity() instanceof LivingEntity by ? by : null);
        return hurt;
    }

    /** Server: snaps into its shell for {@link #HIDE_TICKS}; false if a listener vetoes the "hide" step. Public as a test hook. */
    public boolean hide(@Nullable LivingEntity by) {
        if (!(level() instanceof ServerLevel level) || !startPhase(Step.HIDE, HIDE_TICKS, by)) return false;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below()));
        level.sendParticles(dust, getX(), getY() + 0.05, getZ(), 12, 0.25, 0.02, 0.25, 0.08);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.8, getZ(), 5, 0.15, 0.1, 0.15, 0.15);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.PEARLSHELL_SNAIL_HIDE.get(), getSoundSource(), 1.0F, getVoicePitch());
        return true;
    }

    @Override
    public void onPhaseEnd(Phase ended) {
        core().end();
        if (ended == Step.HIDE && level() instanceof ServerLevel) playSound(ModSounds.PEARLSHELL_SNAIL_EMERGE.get(), 0.8F, getVoicePitch());
    }

    // ---- interaction, ticking ----------------------------------------------------------------------------------------

    /**
     * Shears take a grown pearl; a chorus fruit feeds it. A fruit is always taken up by the snail, fed or not, so a
     * refused one is never eaten by the player instead (chorus fruit would teleport them, next to the void).
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (VinedropStrangler.shears(held) && readyForShearing()) {
            if (level() instanceof ServerLevel level) {
                shear(level, SoundSource.PLAYERS, held);
                if (hasPearl()) return InteractionResult.PASS;         // vetoed: nothing taken, the shears untouched
                gameEvent(GameEvent.SHEAR, player);
                held.hurtAndBreak(1, player, hand.asEquipmentSlot());
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.CHORUS_FRUIT)) {
            if (level().isClientSide()) return InteractionResult.CONSUME;
            return feed(player, held) ? InteractionResult.SUCCESS_SERVER : InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    /** The pearl grows whatever the snail is doing, shut in its shell too. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        if (hasPearl() && !pearlGrown()) advanceGrowth(1);
        if (pearlGrown() && !hidden() && tickCount % GLINT_EVERY == 0) {
            Vec3 mouth = mouth();
            level.sendParticles(ParticleTypes.END_ROD, mouth.x, mouth.y + 0.05, mouth.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (core().idle()) return;
        getNavigation().stop();
        getMoveControl().setWait();
        if (core().in(Step.FEED) && core().elapsed() % 5 == 2) {
            Vec3 mouth = mouth();
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.CHORUS_FRUIT), mouth.x, mouth.y, mouth.z, 2, 0.05, 0.03, 0.05, 0.03);
        }
    }

    /**
     * It gives back what it was given: a grown pearl, or the chorus fruit if the pearl was still growing. Not a step a
     * listener can veto, and not loot: it comes back whatever the loot rules say.
     */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level)) return;
        if (growth >= GROW_TICKS) spawnAtLocation(level, new ItemStack(Items.ENDER_PEARL));
        else if (growth >= 0) spawnAtLocation(level, new ItemStack(Items.CHORUS_FRUIT));
        growth = NONE;
    }

    @Override
    public int visualStages() {
        return STAGES;
    }

    // ---- save and load ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PearlGrowth", growth);
    }

    /** The pearl and its growth survive a reload; being shut in its shell doesn't, as a reload ends any mechanic. */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setGrowth(input.getIntOr("PearlGrowth", NONE));
    }

    // ---- sounds ------------------------------------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return core().idle() ? ModSounds.PEARLSHELL_SNAIL_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PEARLSHELL_SNAIL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PEARLSHELL_SNAIL_DEATH.get();
    }
}
