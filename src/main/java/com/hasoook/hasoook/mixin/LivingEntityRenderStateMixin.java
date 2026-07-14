package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.duck.HeadRemovedAccess;
import com.hasoook.hasoook.duck.SockFaceAccess;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements HeadRemovedAccess, SockFaceAccess {
    @Unique
    private boolean hasoook$headRemoved;

    @Unique
    private String hasoook$transplantedHeadType;

    @Unique
    private String hasoook$transplantedPlayerUuid;

    @Unique
    private String hasoook$transplantedPlayerName;

    @Unique
    private String hasoook$sockFaceData = "";

    @Override
    public boolean hasoook$isHeadRemoved() {
        return hasoook$headRemoved;
    }

    @Override
    public void hasoook$setHeadRemoved(boolean value) {
        this.hasoook$headRemoved = value;
    }

    @Override
    @Nullable
    public String hasoook$getTransplantedHeadType() {
        return hasoook$transplantedHeadType;
    }

    @Override
    public void hasoook$setTransplantedHeadType(@Nullable String type) {
        this.hasoook$transplantedHeadType = type;
    }

    @Override
    @Nullable
    public String hasoook$getTransplantedPlayerUuid() {
        return hasoook$transplantedPlayerUuid;
    }

    @Override
    public void hasoook$setTransplantedPlayerUuid(@Nullable String uuid) {
        this.hasoook$transplantedPlayerUuid = uuid;
    }

    @Override
    @Nullable
    public String hasoook$getTransplantedPlayerName() {
        return hasoook$transplantedPlayerName;
    }

    @Override
    public void hasoook$setTransplantedPlayerName(@Nullable String name) {
        this.hasoook$transplantedPlayerName = name;
    }

    @Override
    public String hasoook$getSockFaceData() {
        return hasoook$sockFaceData;
    }

    @Override
    public void hasoook$setSockFaceData(String data) {
        this.hasoook$sockFaceData = data;
    }
}
