package com.hasoook.hasoook.entity.client;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.entity.custom.EchoArrowProjectile;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public class EchoArrowRenderer extends ArrowRenderer<EchoArrowProjectile, ArrowRenderState> {
    public EchoArrowRenderer(EntityRendererProvider.Context p_173917_) {
        super(p_173917_);
    }

    @Override
    public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }

    @Override
    protected @NonNull Identifier getTextureLocation(ArrowRenderState arrowRenderState) {
        return Identifier.fromNamespaceAndPath(
                Hasoook.MOD_ID,
                "textures/entity/projectiles/echo_arrow.png"
        );
    }
}