package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.duck.SockFaceAccess;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements SockFaceAccess {
    @Unique
    private String hasoook$sockFaceData = "";

    @Override
    public String hasoook$getSockFaceData() {
        return hasoook$sockFaceData;
    }

    @Override
    public void hasoook$setSockFaceData(String data) {
        this.hasoook$sockFaceData = data;
    }
}
