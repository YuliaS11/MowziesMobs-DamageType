package com.bobmowzie.mowziesmobs.server.ability.abilities.player.geomancy;

import com.bobmowzie.mowziesmobs.client.particle.ParticleHandler;
import com.bobmowzie.mowziesmobs.client.particle.util.AdvancedParticleBase;
import com.bobmowzie.mowziesmobs.client.particle.util.ParticleComponent;
import com.bobmowzie.mowziesmobs.server.ability.*;
import com.bobmowzie.mowziesmobs.server.damage.DamageTypes;
import com.bobmowzie.mowziesmobs.server.entity.effects.EntityCameraShake;
import com.bobmowzie.mowziesmobs.server.potion.EffectGeomancy;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import software.bernie.geckolib.core.animation.RawAnimation;

public class GroundSlamAbility extends PlayerAbility {
    public GroundSlamAbility(AbilityType<Player, ? extends Ability> abilityType, Player user) {
        super(abilityType, user,  new AbilitySection[] {
                new AbilitySection.AbilitySectionDuration(AbilitySection.AbilitySectionType.STARTUP, 2),
                new AbilitySection.AbilitySectionInfinite(AbilitySection.AbilitySectionType.ACTIVE),
                new AbilitySection.AbilitySectionDuration(AbilitySection.AbilitySectionType.RECOVERY, 21)
        });
    }

    private static final RawAnimation GROUND_POUND_LOOP_ANIM = RawAnimation.begin().thenLoop("ground_pound_loop");
    private static final RawAnimation GROUND_POUND_LAND_ANIM = RawAnimation.begin().thenPlay("ground_pound_land");

    @Override
    public void start() {
        super.start();
        playAnimation(GROUND_POUND_LOOP_ANIM);

    }

    @Override
    public void tickUsing() {
        super.tickUsing();
        if (getCurrentSection().sectionType == AbilitySection.AbilitySectionType.STARTUP) {
            //getUser().setDeltaMovement(0d,0d,0d);
        }
        if (getCurrentSection().sectionType == AbilitySection.AbilitySectionType.ACTIVE) {
            if(getUser().onGround()){
                nextSection();

                Holder<DamageType> geomancyDamageTypeHolder = getUser().level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GEOMANCY);
                DamageSource damageSourceGeomancy = new DamageSource(geomancyDamageTypeHolder, getUser());

                for(LivingEntity livingentity : getUser().level().getEntitiesOfClass(LivingEntity.class, getUser().getBoundingBox().inflate(5.2D, 2.0D, 5.2D))) {
                    livingentity.hurt(damageSourceGeomancy,10f);
                }

                EntityCameraShake.cameraShake(getUser().level(), getUser().position(), 45, 0.09f, 20, 20);

                BlockState blockBeneath = getUser().level().getBlockState(getUser().blockPosition());
                if (getUser().level().isClientSide) {
                    getUser().playSound(SoundEvents.GENERIC_EXPLODE, 1.5f, 1.0f);
                    for(int i = 0; i < 50;i++ ){
                        getUser().level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockBeneath), getUser().getRandomX(5.8D), getUser().getBlockY() + 0.1f, getUser().getRandomZ(5.8D), 0, 0.38d,0);
                        getUser().level().addParticle(ParticleTypes.POOF, getUser().getRandomX(5f), getUser().getY(), getUser().getRandomZ(5f),0d,0.08d,0d);
                    }
                    AdvancedParticleBase.spawnParticle(getUser().level(), ParticleHandler.RING2.get(), (float) getUser().getX(), (float) getUser().getY() + 0.01f, (float) getUser().getZ(), 0, 0, 0, false, 0, Math.PI / 2f, 0, 0, 3.5F, 0.83f, 1, 0.39f, 1, 1, 10, true, true, new ParticleComponent[]{
                            new ParticleComponent.PropertyControl(ParticleComponent.PropertyControl.EnumParticleProperty.ALPHA, ParticleComponent.KeyTrack.startAndEnd(0.8f, 0.0f), false),
                            new ParticleComponent.PropertyControl(ParticleComponent.PropertyControl.EnumParticleProperty.SCALE, ParticleComponent.KeyTrack.startAndEnd(0, (0.8f + 2.7f * 20f / 60f) * 80f), false)
                    });
                }
            }
        }
        if (getCurrentSection().sectionType == AbilitySection.AbilitySectionType.RECOVERY) {
            //playAnimation("ground_pound_land", true);
            getUser().setDeltaMovement(0d,0d,0d);
        }
    }

    @Override
    public boolean canUse() {
        if (getUser() instanceof Player && !((Player)getUser()).getInventory().getSelected().isEmpty()) return false;
        return EffectGeomancy.canUse(getUser()) && getUser().fallDistance > 2 &&super.canUse();
    }

    @Override
    public void nextSection() {
        super.nextSection();
        if (getCurrentSection().sectionType == AbilitySection.AbilitySectionType.ACTIVE) {
            //playAnimation("ground_pound_loop", true);
        }
        if (getCurrentSection().sectionType == AbilitySection.AbilitySectionType.RECOVERY) {
            playAnimation(GROUND_POUND_LAND_ANIM);
        }
    }

    @Override
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        super.onRightClickEmpty(event);
        if (!getUser().onGround() && getUser().isCrouching()){
            AbilityHandler.INSTANCE.sendPlayerTryAbilityMessage(event.getEntity(), AbilityHandler.GROUND_SLAM_ABILITY);
        }
    }
}
