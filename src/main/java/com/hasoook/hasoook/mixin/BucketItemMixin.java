package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
    /**
     * 允许在地狱倒水（需要火焰保护）
     */
    @Inject(
            method = "emptyContents(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void allowWaterInNether(
            @Nullable LivingEntity entity,
            Level level,
            BlockPos pos,
            @Nullable BlockHitResult hitResult,
            @Nullable ItemStack container,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (container == null) return;
        if (!(container.getItem() instanceof BucketItem bucket)) return;
        if (!bucket.getContent().is(FluidTags.WATER)) return;

        int enchantLevel = ModEnchantmentHelper.getEnchantmentLevel(
                Enchantments.FIRE_PROTECTION,
                container
        );
        if (enchantLevel <= 0) return;

        boolean evaporates = level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos);
        if (!evaporates) return;

        if (!level.isClientSide()) {
            level.setBlock(pos, Fluids.WATER.defaultFluidState().createLegacyBlock(), 11);
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));

            consumeFireProtection(container, level);
        }

        level.playSound(entity, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(entity, GameEvent.FLUID_PLACE, pos);
        cir.setReturnValue(true);
    }

    /**
     * 保留所有附魔，但火焰保护等级减1
     */
    @Inject(
            method = "getEmptySuccessItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void keepFireProtection(
            ItemStack bucketStack,
            Player player,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (player.hasInfiniteMaterials()) {
            cir.setReturnValue(bucketStack);
            return;
        }

        ItemEnchantments oldEnchants = bucketStack.get(DataComponents.ENCHANTMENTS);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
                oldEnchants != null ? oldEnchants : ItemEnchantments.EMPTY
        );

        // 火焰保护等级减1
        var registryAccess = player.level().registryAccess();
        var enchantLookup = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        var fireProt = enchantLookup.getOrThrow(Enchantments.FIRE_PROTECTION);
        int fireLevel = mutable.getLevel(fireProt);
        if (fireLevel > 1) {
            mutable.set(fireProt, fireLevel - 1);
        } else if (fireLevel == 1) {
            mutable.removeIf(ench -> ench == fireProt);
        }
        ItemEnchantments newEnchants = mutable.toImmutable();

        ItemStack newBucket = new ItemStack(Items.BUCKET);
        if (!newEnchants.isEmpty()) {
            newBucket.set(DataComponents.ENCHANTMENTS, newEnchants);
        }
        if (bucketStack.has(DataComponents.CUSTOM_NAME)) {
            newBucket.set(DataComponents.CUSTOM_NAME, bucketStack.get(DataComponents.CUSTOM_NAME));
        }

        // 记录上次流体
        String fluidKey = null;
        Item item = bucketStack.getItem();
        if (item instanceof BucketItem bucketItem) {
            var content = bucketItem.getContent();
            if (content != Fluids.EMPTY) {
                fluidKey = content.builtInRegistryHolder().key().identifier().toString();
            }
        } else if (item == Items.POWDER_SNOW_BUCKET) {
            fluidKey = "minecraft:powder_snow";
        } else if (item == Items.MILK_BUCKET) {
            fluidKey = "minecraft:milk";
        }
        if (fluidKey != null) {
            newBucket.set(ModDataComponents.LAST_FLUID.get(), fluidKey);
        }

        // 重置进度条
        newBucket.set(ModDataComponents.PROGRESS.get(), 0);

        cir.setReturnValue(newBucket);
    }

    /**
     * 装桶时（拾取流体）保留附魔和上次流体记录
     */
    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack keepEnchantWhenPickup(
            ItemStack emptyBucket,
            Player player,
            ItemStack filledBucket
    ) {
        // 保存原桶的附魔
        ItemEnchantments enchantments = emptyBucket.get(DataComponents.ENCHANTMENTS);

        ItemStack result;
        if (player.hasInfiniteMaterials()) {
            if (!player.getInventory().contains(filledBucket)) {
                player.getInventory().add(filledBucket);
            }
            result = emptyBucket;
        } else {
            emptyBucket.shrink(1);
            if (emptyBucket.isEmpty()) {
                result = filledBucket;
            } else {
                if (!player.getInventory().add(filledBucket)) {
                    player.drop(filledBucket, false);
                }
                result = emptyBucket;
            }
        }

        // 复制附魔到最终结果
        if (enchantments != null && !enchantments.isEmpty()) {
            result.set(DataComponents.ENCHANTMENTS, enchantments);
            if (player.hasInfiniteMaterials()) {
                filledBucket.set(DataComponents.ENCHANTMENTS, enchantments);
            }
        }

        // 清除满桶的进度
        if (result.getItem() instanceof BucketItem rb && rb.getContent() != Fluids.EMPTY) {
            result.remove(ModDataComponents.PROGRESS.get());
        }
        if (player.hasInfiniteMaterials() && filledBucket.getItem() instanceof BucketItem fb
                && fb.getContent() != Fluids.EMPTY) {
            filledBucket.remove(ModDataComponents.PROGRESS.get());
        }

        return result;
    }

    private static void consumeFireProtection(ItemStack stack, Level level) {
        ItemEnchantments enchants =
                stack.getOrDefault(
                        DataComponents.ENCHANTMENTS,
                        ItemEnchantments.EMPTY
                );

        ItemEnchantments.Mutable mutable =
                new ItemEnchantments.Mutable(enchants);

        var enchantLookup = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        var fireProt =
                enchantLookup.getOrThrow(Enchantments.FIRE_PROTECTION);

        int levelValue = mutable.getLevel(fireProt);

        if (levelValue <= 0) {
            return;
        }

        if (levelValue > 1) {
            mutable.set(fireProt, levelValue - 1);
        } else {
            mutable.removeIf(holder -> holder.equals(fireProt));
        }

        ItemEnchantments newEnchants = mutable.toImmutable();

        if (newEnchants.isEmpty()) {
            stack.remove(DataComponents.ENCHANTMENTS);
        } else {
            stack.set(DataComponents.ENCHANTMENTS, newEnchants);
        }
    }
}