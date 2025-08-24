package com.bobmowzie.mowziesmobs.server.ability.abilities.player.heliomancy;

import com.bobmowzie.mowziesmobs.client.model.tools.MathUtils;
import com.bobmowzie.mowziesmobs.client.particle.ParticleOrb;
import com.bobmowzie.mowziesmobs.server.ability.AbilityHandler;
import com.bobmowzie.mowziesmobs.server.ability.AbilitySection;
import com.bobmowzie.mowziesmobs.server.ability.AbilityType;
import com.bobmowzie.mowziesmobs.server.config.ConfigHandler;
import com.bobmowzie.mowziesmobs.server.damage.DamageTypeHeliomancy;
import com.bobmowzie.mowziesmobs.server.entity.umvuthana.EntityUmvuthi;
import com.bobmowzie.mowziesmobs.server.potion.EffectHandler;
import com.bobmowzie.mowziesmobs.server.sound.MMSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.List;

public class SolarFlareAbility extends HeliomancyAbilityBase {

    public SolarFlareAbility(AbilityType<Player, SolarFlareAbility> abilityType, Player user) {
        super(abilityType, user, EntityUmvuthi.SolarFlareAbility.SECTION_TRACK);
    }

    private static final RawAnimation SOLAR_FLARE_ANIM = RawAnimation.begin().thenPlay("solar_flare");

    @Override
    public void start() {
        super.start();
        getUser().playSound(MMSounds.ENTITY_UMVUTHI_BURST.get(), 1.7f, 1.5f);
        playAnimation(SOLAR_FLARE_ANIM);
        if (getLevel().isClientSide) {
            heldItemMainHandVisualOverride = ItemStack.EMPTY;
            heldItemOffHandVisualOverride = ItemStack.EMPTY;
            firstPersonOffHandDisplay = HandDisplay.FORCE_RENDER;
            firstPersonMainHandDisplay = HandDisplay.FORCE_RENDER;
        }
    }

    @Override
    public boolean canUse() {
        if (getUser() == null || !getUser().getInventory().getSelected().isEmpty()) return false;
        return getUser().hasEffect(EffectHandler.SUNS_BLESSING.get()) && super.canUse();
    }

    @Override
    public void tickUsing() {
        super.tickUsing();
        if (getTicksInUse() < 16) {
            getUser().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 2, false, false));
        }

        if (getTicksInUse() <= 6 && getLevel().isClientSide) {
            int particleCount = 8;
            while (--particleCount != 0) {
                double radius = 2f;
                double yaw = rand.nextFloat() * 2 * Math.PI;
                double pitch = rand.nextFloat() * 2 * Math.PI;
                double ox = radius * Math.sin(yaw) * Math.sin(pitch);
                double oy = radius * Math.cos(pitch);
                double oz = radius * Math.cos(yaw) * Math.sin(pitch);
                getLevel().addParticle(new ParticleOrb.OrbData((float) getUser().getX(), (float) getUser().getY() + getUser().getBbHeight() / 2f, (float) getUser().getZ(), 6), getUser().getX() + ox, getUser().getY() + getUser().getBbHeight() / 2f + oy, getUser().getZ() + oz, 0, 0, 0);
            }
        }

        if (getTicksInUse() == 10) {
            if (getLevel().isClientSide) {
                for (int i = 0; i < 30; i++) {
                    final float velocity = 0.25F;
                    float yaw = i * (MathUtils.TAU / 30);
                    float vy = rand.nextFloat() * 0.1F - 0.05f;
                    float vx = velocity * Mth.cos(yaw);
                    float vz = velocity * Mth.sin(yaw);
                    getLevel().addParticle(ParticleTypes.FLAME, getUser().getX(), getUser().getY() + 1, getUser().getZ(), vx, vy, vz);
                }
            }
        }
    }

    @Override
    protected void beginSection(AbilitySection section) {
        super.beginSection(section);
        if (section.sectionType == AbilitySection.AbilitySectionType.ACTIVE) {
            Player user = getUser();
            float radius = 3.2f;
            List<LivingEntity> hit = getEntityLivingBaseNearby(user, radius, radius, radius, radius);
            for (LivingEntity aHit : hit) {
                if (aHit == getUser()) {
                    continue;
                }
                float damage = 2.0f;
                float knockback = 3.0f;
                damage *= ConfigHandler.COMMON.TOOLS_AND_ABILITIES.SUNS_BLESSING.sunsBlessingAttackMultiplier.get();

                Holder<DamageType> heliomancyDamageTypeHolder = user.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeHeliomancy.HELIOMANCY);
                DamageSource damageSourceHeliomancy = new DamageSource(heliomancyDamageTypeHolder, user);

                if (aHit.hurt(damageSourceHeliomancy, damage)) {
                    if (knockback > 0) {
                        Vec3 vec3 = aHit.position().subtract(user.position()).normalize().scale((double)knockback * 0.6D);
                        if (vec3.lengthSqr() > 0.0D) {
                            aHit.push(vec3.x, 0.1D, vec3.z);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        super.onLeftClickEmpty(event);
        if (event.getEntity() == getUser() && event.getEntity().isShiftKeyDown()) AbilityHandler.INSTANCE.sendPlayerTryAbilityMessage(event.getEntity(), AbilityHandler.SOLAR_FLARE_ABILITY);
    }

    @Override
    public void onLeftClickEntity(AttackEntityEvent event) {
        super.onLeftClickEntity(event);
        if (event.getEntity() == getUser() && event.getEntity().isShiftKeyDown()) AbilityHandler.INSTANCE.sendPlayerTryAbilityMessage(event.getEntity(), AbilityHandler.SOLAR_FLARE_ABILITY);
    }
}
