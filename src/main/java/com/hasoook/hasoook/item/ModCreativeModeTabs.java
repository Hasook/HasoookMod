package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Hasoook.MOD_ID);

    public static final Supplier<CreativeModeTab> HASOOOK_TAB = CREATIVE_MODE_TAB.register("hasoook_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.RECOVERY_CLOCK.get()))
                    .title(Component.translatable("creativetab.hasoook"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.SLIME_SPEAR);
                        output.accept(ModItems.FIREWORK_ROCKET_SPEAR);
                        output.accept(ModItems.HEAVY_SPEAR);
                        output.accept(ModItems.AMETHYST_SHARD_SPEAR);
                        output.accept(ModItems.END_CRYSTAL_SPEAR);
                        output.accept(ModItems.NETHER_STAR_SPEAR);
                        output.accept(ModItems.PISTON_SPEAR);
                        output.accept(ModItems.STICKY_PISTON_SPEAR);
                        output.accept(ModItems.END_ROD_SPEAR);
                        output.accept(ModItems.SEVOWER);
                        output.accept(ModItems.ECHO_ARROW);
                        output.accept(ModItems.RECOVERY_CLOCK);
                        output.accept(ModItems.MAGIC_PAINTBRUSH_AND_PAPER);
                        output.accept(ModItems.HEAVY_HALBERD);
                        output.accept(ModBlocks.PHANTOM_LAMP);
                        output.accept(ModItems.MOB_HEAD);
                        output.accept(ModItems.ARMOR_STAND_SWORD);
                        output.accept(ModItems.SOCKS);
                        output.accept(ModItems.SOCK);
                        output.accept(ModItems.COPPER_GOLEM_BATTLE_CHIP);
                        output.accept(ModItems.CHARGED_COPPER_SWORD);
                        output.accept(ModItems.CHARGED_COPPER_PICKAXE);

                        // 自定义附魔书
                        var enchantmentLookup = itemDisplayParameters.holders()
                                .lookupOrThrow(Registries.ENCHANTMENT);
                        addEnchantedBook(output, enchantmentLookup, ModEnchantments.COMMONERS_RESOLVE, 1);
                        addEnchantedBook(output, enchantmentLookup, ModEnchantments.UNDERTOW, 3);
                        addEnchantedBook(output, enchantmentLookup, ModEnchantments.HOLLOW, 5);
                        addEnchantedBook(output, enchantmentLookup, ModEnchantments.DEBTSHOT, 1);
                        addEnchantedBook(output, enchantmentLookup, ModEnchantments.LOUIS, 16);
                        addEnchantedBook(output, enchantmentLookup, ModEnchantments.GIVING, 1);
                    }).build());

    /**
     * 向创造模式选项卡添加最高等级的附魔书
     */
    private static void addEnchantedBook(CreativeModeTab.Output output,
                                         HolderLookup.RegistryLookup<Enchantment> lookup,
                                         ResourceKey<Enchantment> enchantmentKey,
                                         int maxLevel) {
        Holder<Enchantment> enchantment = lookup.getOrThrow(enchantmentKey);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, maxLevel);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        output.accept(book);
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}