package com.hasoook.hasoook.entity.client;

import com.hasoook.hasoook.entity.custom.AmethystShardProjectile;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class AmethystShardProjectileRenderer
        extends ThrownItemRenderer<AmethystShardProjectile> {

    public AmethystShardProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
