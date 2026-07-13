package com.hasoook.hasoook.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link EntityRenderDispatcher} 的私有字段 {@code equipmentAssets}，
 * 以便在 ArmorStandSwordRenderer 中渲染立体盔甲。
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("equipmentAssets")
    EquipmentAssetManager getEquipmentAssets();
}
