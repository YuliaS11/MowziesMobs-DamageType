package com.bobmowzie.mowziesmobs.server.ability.abilities.player.geomancy;

import com.bobmowzie.mowziesmobs.MowziesMobs;
import com.bobmowzie.mowziesmobs.client.model.tools.geckolib.MowzieGeoBone;
import com.bobmowzie.mowziesmobs.client.model.tools.geckolib.MowzieGeoModel;
import com.bobmowzie.mowziesmobs.client.particle.ParticleHandler;
import com.bobmowzie.mowziesmobs.client.particle.util.AdvancedParticleBase;
import com.bobmowzie.mowziesmobs.client.particle.util.ParticleComponent;
import com.bobmowzie.mowziesmobs.client.render.entity.player.GeckoPlayer;
import com.bobmowzie.mowziesmobs.client.sound.IGeomancyRumbler;
import com.bobmowzie.mowziesmobs.server.ability.*;
import com.bobmowzie.mowziesmobs.server.capability.AbilityCapability;
import com.bobmowzie.mowziesmobs.server.config.ConfigHandler;
import com.bobmowzie.mowziesmobs.server.damage.DamageTypes;
import com.bobmowzie.mowziesmobs.server.entity.EntityHandler;
import com.bobmowzie.mowziesmobs.server.entity.effects.EntityBlockSwapper;
import com.bobmowzie.mowziesmobs.server.entity.effects.EntityFallingBlock;
import com.bobmowzie.mowziesmobs.server.item.ItemEarthrendGauntlet;
import com.bobmowzie.mowziesmobs.server.item.ItemHandler;
import com.bobmowzie.mowziesmobs.server.potion.EffectGeomancy;
import com.bobmowzie.mowziesmobs.server.sound.MMSounds;
import com.bobmowzie.mowziesmobs.server.tag.TagHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;

public class TunnelingAbility extends PlayerAbility implements IGeomancyRumbler {
    private int doubleTapTimer = 0;
    public boolean prevUnderground;
    public BlockState justDug = Blocks.DIRT.defaultBlockState();
    boolean underground = false;

    private float spinAmount = 0;
    private float pitch = 0;

    private int timeUnderground = 0;
    private int timeAboveGround = 0;

    private InteractionHand whichHand;
    private ItemStack gauntletStack;

    private boolean isRumbling;

    public TunnelingAbility(AbilityType<Player, ? extends Ability> abilityType, Player user) {
        super(abilityType, user, new AbilitySection[] {
                new AbilitySection.AbilitySectionInfinite(AbilitySection.AbilitySectionType.ACTIVE)
        });
    }

    @Override
    public void tickNotUsing() {
        super.tickNotUsing();
        if (doubleTapTimer > 0) doubleTapTimer--;
    }

    public void playGauntletAnimation() {
        if (getUser() != null && !getLevel().isClientSide()) {
            if (gauntletStack != null && gauntletStack.getItem() == ItemHandler.EARTHREND_GAUNTLET.get()) {
                Player player = (Player) getUser();
                ItemHandler.EARTHREND_GAUNTLET.get().triggerAnim(player, GeoItem.getOrAssignId(gauntletStack, (ServerLevel) player.level()), ItemEarthrendGauntlet.CONTROLLER_NAME, ItemEarthrendGauntlet.OPEN_ANIM_NAME);
            }
        }
    }

    public void stopGauntletAnimation() {
        if (getUser() != null && !getLevel().isClientSide()) {
            if (gauntletStack != null && gauntletStack.getItem() == ItemHandler.EARTHREND_GAUNTLET.get()) {
                Player player = (Player) getUser();
                ItemHandler.EARTHREND_GAUNTLET.get().triggerAnim(player, GeoItem.getOrAssignId(gauntletStack, (ServerLevel) player.level()), ItemEarthrendGauntlet.CONTROLLER_NAME, ItemEarthrendGauntlet.IDLE_ANIM_NAME);
            }
        }
    }

    @Override
    public void start() {
        super.start();
        underground = false;
        prevUnderground = false;
        if (getUser().onGround()) getUser().push(0, 0.8f, 0);
        whichHand = getUser().getUsedItemHand();
        gauntletStack = getUser().getUseItem();
        if (getUser().level().isClientSide()) {
            spinAmount = 0;
            pitch = 0;
        }
        if (getLevel().isClientSide())
            MowziesMobs.PROXY.playGeomancyRumbleSound(this);
    }

    public boolean damageGauntlet() {
        ItemStack stack = getUser().getUseItem();
        if (stack.getItem() == ItemHandler.EARTHREND_GAUNTLET.get()) {
            InteractionHand handIn = getUser().getUsedItemHand();
            if (stack.getDamageValue() + 5 < stack.getMaxDamage()) {
                stack.hurtAndBreak(5, getUser(), p -> p.broadcastBreakEvent(handIn));
                return true;
            }
            else {
                if (ConfigHandler.COMMON.TOOLS_AND_ABILITIES.EARTHREND_GAUNTLET.breakable.get()) {
                    stack.hurtAndBreak(5, getUser(), p -> p.broadcastBreakEvent(handIn));
                }
                return false;
            }
        }
        return false;
    }

    public void restoreGauntlet(ItemStack stack) {
        if (stack.getItem() == ItemHandler.EARTHREND_GAUNTLET.get()) {
            if (!ConfigHandler.COMMON.TOOLS_AND_ABILITIES.EARTHREND_GAUNTLET.breakable.get()) {
                stack.setDamageValue(Math.max(stack.getDamageValue() - 1, 0));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        AbilityCapability.IAbilityCapability abilityCapability = AbilityHandler.INSTANCE.getAbilityCapability(getUser());
        if (abilityCapability == null) return;
        if (abilityCapability.getActiveAbility() == null || (abilityCapability.getActiveAbility().getAbilityType() != AbilityHandler.SPAWN_PILLAR_ABILITY && abilityCapability.getActiveAbility().getAbilityType() != AbilityHandler.TUNNELING_ABILITY)) {
            Player player = (Player) getUser();
            for (ItemStack stack : player.getInventory().items) {
                restoreGauntlet(stack);
            }
            for (ItemStack stack : player.getInventory().offhand) {
                restoreGauntlet(stack);
            }
        }
    }

    @Override
    public void tickUsing() {
        super.tickUsing();
        getUser().getAbilities().flying = false;
        underground = !getUser().level().getEntitiesOfClass(EntityBlockSwapper.EntityBlockSwapperTunneling.class, getUser().getBoundingBox().inflate(0.3)).isEmpty();
        Vec3 lookVec = getUser().getLookAngle();
        float tunnelSpeed = 0.3f;
        ItemStack stack = getUser().getUseItem();
        boolean usingGauntlet = stack.getItem() == ItemHandler.EARTHREND_GAUNTLET.get();
        if (underground) {
            timeUnderground++;
            if (usingGauntlet && damageGauntlet()) {
                getUser().setDeltaMovement(lookVec.normalize().scale(tunnelSpeed));
            }
            else {
                getUser().setDeltaMovement(lookVec.multiply(0.3, 0, 0.3).add(0, 1, 0).normalize().scale(tunnelSpeed));
            }

            Holder<DamageType> geomancyDamageTypeHolder = getUser().level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GEOMANCY);
            DamageSource damageSourceGeomancy = new DamageSource(geomancyDamageTypeHolder, getUser());

            for (LivingEntity entityHit : getEntityLivingBaseNearby(getUser(),2, 2, 2, 2)) {
                entityHit.hurt(damageSourceGeomancy, 3 * ConfigHandler.COMMON.TOOLS_AND_ABILITIES.EARTHREND_GAUNTLET.attackMultiplier.get().floatValue());
            }
        }
        else {
            timeAboveGround++;
            getUser().setDeltaMovement(getUser().getDeltaMovement().subtract(0, 0.07, 0));
            if (getUser().getDeltaMovement().y() < -1.3) getUser().setDeltaMovement(getUser().getDeltaMovement().x(), -1.3, getUser().getDeltaMovement().z());
        }

        if ((underground && (prevUnderground || lookVec.y < 0) && timeAboveGround > 5) || (getTicksInUse() > 1 && usingGauntlet && lookVec.y < 0 && stack.getDamageValue() + 5 < stack.getMaxDamage())) {
            Vec3 userCenter = getUser().position().add(0, getUser().getBbHeight() / 2f, 0);
            float radius = 2f;
            AABB aabb = new AABB(-radius, -radius, -radius, radius, radius, radius);
            aabb = aabb.move(userCenter);
            for (int i = 0; i < getUser().getDeltaMovement().length() * 4; i++) {
                for (int x = (int) Math.floor(aabb.minX); x <= Math.floor(aabb.maxX); x++) {
                    for (int y = (int) Math.floor(aabb.minY); y <= Math.floor(aabb.maxY); y++) {
                        for (int z = (int) Math.floor(aabb.minZ); z <= Math.floor(aabb.maxZ); z++) {
                            Vec3 posVec = new Vec3(x, y, z);
                            if (posVec.add(0.5, 0.5, 0.5).subtract(userCenter).lengthSqr() > radius * radius) continue;
                            Vec3 motionScaled = getUser().getDeltaMovement().normalize().scale(i);
                            posVec = posVec.add(motionScaled);
                            BlockPos pos = new BlockPos((int) posVec.x, (int) posVec.y, (int) posVec.z);
                            BlockState blockState = getUser().level().getBlockState(pos);
                            if (EffectGeomancy.checkBlock(blockState, TagHandler.GEOMANCY_TUNNELABLE) && blockState.getBlock() != Blocks.BEDROCK) {
                                justDug = blockState;
                                if (!getLevel().isClientSide) {
                                    EntityBlockSwapper.EntityBlockSwapperTunneling swapper = new EntityBlockSwapper.EntityBlockSwapperTunneling(EntityHandler.BLOCK_SWAPPER_TUNNELING.get(), getLevel(), pos, Blocks.AIR.defaultBlockState(), 15, false, false, getUser());
                                    getLevel().addFreshEntity(swapper);
                                }
                            }
                        }
                    }
                }
            }
        }
        isRumbling = underground;
        if (!prevUnderground && underground) {
            timeUnderground = 0;
            getUser().playSound(MMSounds.EFFECT_GEOMANCY_BREAK_MEDIUM.get(rand.nextInt(3)).get(), 1f, 0.9f + rand.nextFloat() * 0.1f);
            if (getUser().level().isClientSide)
                AdvancedParticleBase.spawnParticle(getUser().level(), ParticleHandler.RING2.get(), (float) getUser().getX(), (float) getUser().getY() + 0.02f, (float) getUser().getZ(), 0, 0, 0, false, 0, Math.PI/2f, 0, 0, 3.5F, 0.83f, 1, 0.39f, 1, 1, 10, true, true, new ParticleComponent[]{
                        new ParticleComponent.PropertyControl(ParticleComponent.PropertyControl.EnumParticleProperty.ALPHA, ParticleComponent.KeyTrack.startAndEnd(1f, 0f), false),
                        new ParticleComponent.PropertyControl(ParticleComponent.PropertyControl.EnumParticleProperty.SCALE, ParticleComponent.KeyTrack.startAndEnd(10f, 30f), false)
                });
            playGauntletAnimation();
        }
        if (prevUnderground && !underground) {
            timeAboveGround = 0;
            getUser().playSound(MMSounds.EFFECT_GEOMANCY_BREAK.get(), 1f, 0.9f + rand.nextFloat() * 0.1f);
            if (getUser().level().isClientSide)
                AdvancedParticleBase.spawnParticle(getUser().level(), ParticleHandler.RING2.get(), (float) getUser().getX(), (float) getUser().getY() + 0.02f, (float) getUser().getZ(), 0, 0, 0, false, 0, Math.PI/2f, 0, 0, 3.5F, 0.83f, 1, 0.39f, 1, 1, 10, true, true, new ParticleComponent[]{
                        new ParticleComponent.PropertyControl(ParticleComponent.PropertyControl.EnumParticleProperty.ALPHA, ParticleComponent.KeyTrack.startAndEnd(1f, 0f), false),
                        new ParticleComponent.PropertyControl(ParticleComponent.PropertyControl.EnumParticleProperty.SCALE, ParticleComponent.KeyTrack.startAndEnd(10f, 30f), false)
                });
            if (timeUnderground > 10)
                getUser().setDeltaMovement(getUser().getDeltaMovement().scale(10f));
            else
                getUser().setDeltaMovement(getUser().getDeltaMovement().multiply(3, 7, 3));

            for (int i = 0; i < 6; i++) {
                if (justDug == null) justDug = Blocks.DIRT.defaultBlockState();
                EntityFallingBlock fallingBlock = new EntityFallingBlock(EntityHandler.FALLING_BLOCK.get(), getUser().level(), 80, justDug);
                fallingBlock.setPos(getUser().getX(), getUser().getY() + 1, getUser().getZ());
                fallingBlock.setDeltaMovement(getUser().getRandom().nextFloat() * 0.8f - 0.4f, 0.4f + getUser().getRandom().nextFloat() * 0.8f, getUser().getRandom().nextFloat() * 0.8f - 0.4f);
                getUser().level().addFreshEntity(fallingBlock);
            }
            stopGauntletAnimation();
        }
        prevUnderground = underground;
    }

    @Override
    public void end() {
        super.end();
        stopGauntletAnimation();
    }

    @Override
    public boolean canUse() {
        return super.canUse() && ConfigHandler.COMMON.TOOLS_AND_ABILITIES.EARTHREND_GAUNTLET.enableTunneling.get();
    }

    @Override
    protected boolean canContinueUsing() {
        ItemStack stack = getUser().getUseItem();
        boolean usingGauntlet = stack.getItem() == ItemHandler.EARTHREND_GAUNTLET.get();
        if (whichHand == null) return false;
        boolean canContinueUsing = (getTicksInUse() <= 1 || !(getUser().onGround() || (getUser().isInWater() && !usingGauntlet)) || underground) && getUser().getItemInHand(whichHand).getItem() == ItemHandler.EARTHREND_GAUNTLET.get() && super.canContinueUsing();
        return canContinueUsing;
    }

    @Override
    public boolean preventsItemUse(ItemStack stack) {
        if (stack.getItem() == ItemHandler.EARTHREND_GAUNTLET.get()) return false;
        return super.preventsItemUse(stack);
    }

    private static final RawAnimation FALL_ANIM = RawAnimation.begin().thenPlayAndHold("tunneling_fall");
    private static final RawAnimation DRILL_ANIM = RawAnimation.begin().thenLoop("tunneling_drill");

    @Override
    public <E extends GeoEntity> PlayState animationPredicate(AnimationState<E> e, GeckoPlayer.Perspective perspective) {
        e.getController().transitionLength(4);
        if (perspective == GeckoPlayer.Perspective.THIRD_PERSON) {
            float yMotionThreshold = getUser() == Minecraft.getInstance().player ? 1 : 2;
            if (!underground && getUser().getUseItem().getItem() != ItemHandler.EARTHREND_GAUNTLET.get() && getUser().getDeltaMovement().y() < yMotionThreshold) {
                e.getController().setAnimation(FALL_ANIM);
            }
            else {
                e.getController().setAnimation(DRILL_ANIM);
            }
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void codeAnimations(MowzieGeoModel<? extends GeoEntity> model, float partialTick) {
        super.codeAnimations(model, partialTick);
        float faceMotionController = 1f - model.getControllerValueInverted("FaceVelocityController");
        Vec3 moveVec = getUser().getDeltaMovement().normalize();
        pitch = (float) Mth.lerp(0.3 * partialTick, pitch, moveVec.y());
        MowzieGeoBone com = model.getMowzieBone("CenterOfMass");
        com.setRotX((float) (-Math.PI/2f + Math.PI/2f * pitch) * faceMotionController);

        float spinSpeed = 0.35f;
        if (faceMotionController < 1 && spinAmount < Math.PI * 2f - 0.01 && spinAmount > 0.01) {
            float f = (float) ((Math.PI * 2f - spinAmount) / (Math.PI * 2f));
            f = (float) Math.pow(f, 0.5);
            spinAmount += partialTick * spinSpeed * f;
            if (spinAmount > Math.PI * 2f) {
                spinAmount = 0;
            }
        }
        else {
            spinAmount += faceMotionController * partialTick * spinSpeed;
            spinAmount = (float) (spinAmount % (Math.PI * 2));
        }
        MowzieGeoBone waist = model.getMowzieBone("Waist");
        waist.addRotY(-spinAmount);
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag compound = super.writeNBT();
        if (isUsing() && whichHand != null) {
            compound.putInt("whichHand", whichHand.ordinal());
        }
        return compound;
    }

    @Override
    public void readNBT(Tag nbt) {
        super.readNBT(nbt);
        if (isUsing()) {
            CompoundTag compound = (CompoundTag) nbt;
            whichHand = InteractionHand.values()[compound.getInt("whichHand")];
        }
    }

    @Override
    public void onFall(LivingFallEvent event) {
        super.onFall(event);
        if (event.getEntity() == getUser() && isUsing()) {
            event.setDamageMultiplier(0);
        }
    }

    @Override
    public boolean isRumbling() {
        return isRumbling && isUsing();
    }

    @Override
    public boolean isFinishedRumbling() {
        return !isUsing();
    }

    @Override
    public float getRumblerX() {
        return (float) getUser().getX();
    }

    @Override
    public float getRumblerY() {
        return (float) getUser().getY();
    }

    @Override
    public float getRumblerZ() {
        return (float) getUser().getZ();
    }
}
