package com.hasoook.hasoook.network.handler;

import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.network.payload.GiveResultPayload;
import com.hasoook.hasoook.quest.PlayerQuestManager;
import com.hasoook.hasoook.quest.QuestSyncUtil;
import com.hasoook.hasoook.quest.ServerQuestService;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GiveResultHandler {
    public static void handle(ServerPlayer player, GiveResultPayload payload) {
        String questId = payload.id();
        boolean isEntity = payload.isEntity();

        boolean isCustomQuest = questId.startsWith("custom:");

        if (!isCustomQuest) {
            int playerLevel = Math.max(1, player.experienceLevel);
            int count = ThreadLocalRandom.current().nextInt(1, playerLevel + 1);

            if (isEntity) {
                EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.tryParse(questId));
                if (entityType != null) {
                    int entityCount = Math.min(count, 20);
                    for (int i = 0; i < entityCount; i++) {
                        Entity entity = entityType.create(player.level(), EntitySpawnReason.COMMAND);
                        if (entity != null) {
                            var look = player.getLookAngle();
                            entity.snapTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
                            double power = 0.6;
                            entity.setDeltaMovement(
                                    look.x * power + (Math.random() - 0.5) * 0.2,
                                    look.y * power + (Math.random() - 0.5) * 0.1,
                                    look.z * power + (Math.random() - 0.5) * 0.2
                            );
                            player.level().addFreshEntity(entity);
                            entity.hurtMarked = true;
                        }
                    }
                }
            } else {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(questId));
                if (item != null) {
                    ItemStack itemStack = new ItemStack(item);
                    int maxStackSize = itemStack.getMaxStackSize();
                    int finalCount = Math.min(count, maxStackSize);
                    itemStack.setCount(finalCount);

                    // 附魔书：给予随机附魔
                    if (itemStack.is(Items.ENCHANTED_BOOK)) {
                        applyRandomEnchantment(itemStack, player);
                    }

                    player.getInventory().add(itemStack);
                    player.getInventory().setChanged();
                }
            }
        } else {
            // 自定义任务仅发送一条个人提示，不给予物品
            player.sendSystemMessage(Component.literal("§a你的画作被认可了！但你什么都没得到..."));
        }

        // 播放完成音效、粒子效果并给予少量经验
        ServerLevel serverLevel = player.level();
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.5, 0.5, 0.5, 0.0);
        player.giveExperiencePoints(5);

        // 清除当前任务，并视几率分配新任务
        if (PlayerQuestManager.hasQuest(player)) {
            QuestSyncUtil.clearAndSync(player);
        }

        int taskChance = Config.PAQ_TASK_CHANCE.get();
        if (taskChance > 0 && ThreadLocalRandom.current().nextInt(taskChance) == 0) {
            ServerQuestService.assignRandomItemQuest(player);
        }
    }

    /**
     * 给附魔书赋予一个随机附魔（随机种类 + 随机等级）。
     */
    private static void applyRandomEnchantment(ItemStack stack, ServerPlayer player) {
        var enchantmentRegistry = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> allEnchantments = enchantmentRegistry.listElements()
                .map(h -> (Holder<Enchantment>) h)
                .toList();

        if (allEnchantments.isEmpty()) return;

        Holder<Enchantment> randomEnchant = allEnchantments.get(
                ThreadLocalRandom.current().nextInt(allEnchantments.size()));
        int maxLevel = randomEnchant.value().getMaxLevel();
        int level = ThreadLocalRandom.current().nextInt(maxLevel) + 1;

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(randomEnchant, level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
    }
}