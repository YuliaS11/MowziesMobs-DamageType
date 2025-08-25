package com.bobmowzie.mowziesmobs.server.damage;

import com.bobmowzie.mowziesmobs.MowziesMobs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class DamageTypes {
    public static final ResourceKey<DamageType> HELIOMANCY = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(MowziesMobs.MODID, "heliomancy")
    );
}
