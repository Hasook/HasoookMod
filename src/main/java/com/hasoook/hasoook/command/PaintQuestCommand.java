package com.hasoook.hasoook.command;

import com.hasoook.hasoook.quest.PlayerQuestManager;
import com.hasoook.hasoook.quest.QuestSyncUtil;
import com.hasoook.hasoook.quest.ServerQuestService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;

public class PaintQuestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("paintquest")
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        // 随机任务（保持原样）
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                            for (ServerPlayer player : players) {
                                                ServerQuestService.assignRandomItemQuest(player);
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("已给 " + players.size() + " 名玩家随机分配了绘画任务"),
                                                    true
                                            );
                                            return players.size();
                                        })
                                        // 指定任务
                                        .then(Commands.argument("quest_target", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                    String input = StringArgumentType.getString(ctx, "quest_target");

                                                    String questId;
                                                    String questName;
                                                    boolean isEntity = false;

                                                    // 尝试将输入解析为带命名空间的 ID
                                                    Identifier id = Identifier.tryParse(input);
                                                    if (id == null && !input.contains(":")) {
                                                        // 自动补全 minecraft 命名空间
                                                        id = Identifier.tryParse("minecraft:" + input);
                                                    }

                                                    if (id != null) {
                                                        // 先尝试作为物品 ID
                                                        Item item = BuiltInRegistries.ITEM.getValue(id);
                                                        if (BuiltInRegistries.ITEM.getKey(item).equals(id)) {
                                                            // 有效物品 ID
                                                            questId = id.toString();
                                                            questName = new ItemStack(item).getHoverName().getString();
                                                        } else {
                                                            // 再尝试作为实体 ID
                                                            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                                                            if (BuiltInRegistries.ENTITY_TYPE.getKey(entityType).equals(id)) {
                                                                questId = id.toString();
                                                                questName = entityType.getDescription().getString();
                                                                isEntity = true;
                                                            } else {
                                                                // 都不是，当作自定义名称
                                                                questId = "custom:" + input;
                                                                questName = input;
                                                            }
                                                        }
                                                    } else {
                                                        // 无法解析为合法 ID，自定义任务
                                                        questId = "custom:" + input;
                                                        questName = input;
                                                    }

                                                    for (ServerPlayer player : players) {
                                                        PlayerQuestManager.setQuest(player, questId, questName, isEntity);
                                                        QuestSyncUtil.sync(player);
                                                    }

                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("已为 " + players.size() + " 名玩家设置任务：" + questName),
                                                            true
                                                    );
                                                    return players.size();
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                            for (ServerPlayer player : players) {
                                                QuestSyncUtil.clearAndSync(player);
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("已清空 " + players.size() + " 名玩家的绘画任务"),
                                                    true
                                            );
                                            return players.size();
                                        }))
                        )
        );
    }
}