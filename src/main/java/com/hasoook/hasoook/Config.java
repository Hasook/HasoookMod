package com.hasoook.hasoook;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;


public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("纸与笔相关配置 (Pen and Paper Configuration)")
                .push("paper_and_quill");
    }

    // 纸与笔的 API 密钥
    public static final ModConfigSpec.ConfigValue<String> PAQ_API_KEY = BUILDER
            .comment("AI 绘画识别所用的 API 密钥。")
            .translation("hasoook.config.api_key")
            .define("apiKey", "");

    // 纸与笔的 API 地址
    public static final ModConfigSpec.ConfigValue<String> PAQ_API_URL = BUILDER
            .comment("AI 绘画识别所用的接口地址。")
            .translation("hasoook.config.api_url")
            .define("apiUrl", "");

    // 纸与笔的 API 模型名称
    public static final ModConfigSpec.ConfigValue<String> PAQ_MODEL_NAME = BUILDER
            .comment("AI 绘画识别所用的模型名称。")
            .translation("hasoook.config.model_name")
            .define("modelName", "");

    // 玩家加入/重生时是否给予神奇的笔与纸 + 书与笔
    public static final ModConfigSpec.BooleanValue PAQ_GIVE_ON_JOIN = BUILDER
            .comment("玩家加入游戏或重生时，给予一份（神奇的笔与纸 + 书与笔）")
            .translation("hasoook.config.give_on_join")
            .define("giveOnJoin", false);

    // 限制玩家破坏方块时的掉落物
    public static final ModConfigSpec.BooleanValue PAQ_LIMIT_DROPS = BUILDER
            .comment("限制玩家破坏方块时的掉落物")
            .translation("hasoook.config.limit_drops")
            .define("limitDrops", false);

    // 描绘任务概率（1/X）
    public static final ModConfigSpec.IntValue PAQ_TASK_CHANCE = BUILDER
            .comment("绘画任务触发概率（0=关闭，>0=1/X）")
            .translation("hasoook.config.painting_task_chance")
            .defineInRange("taskChance", 10, 0, Integer.MAX_VALUE);

    // 识别成功后给予地图
    public static final ModConfigSpec.BooleanValue PAQ_GIVE_MAP = BUILDER
            .comment("绘画识别成功时，都会生成一张相同的地图给予玩家")
            .translation("hasoook.config.give_map")
            .define("giveMapOnSuccess", false);

    // 展示型地图：允许玩家直接看到其他玩家手中的地图
    public static final ModConfigSpec.BooleanValue PAQ_DISPLAY_MAP = BUILDER
            .comment("启用展示型地图，允许玩家直接看到其他玩家手中的地图")
            .translation("hasoook.config.display_map")
            .define("displayMap", false);

    static {
        BUILDER.pop(); // 退出纸与笔配置组
    }

    // 活塞矛的最大长度
    public static final ModConfigSpec.IntValue PISTON_SPEAR_LENGTH = BUILDER
            .comment("活塞矛的最大长度")
            .translation("hasoook.config.piston_spear_length")
            .defineInRange("pistonSpearLength", 10, 0, Integer.MAX_VALUE);

    // ==================== 生物头相关配置 ====================
    static {
        BUILDER.comment("生物头相关配置 (Mob Head Configuration)")
                .push("mob_head");
    }

    // 剪头时造成伤害的百分比
    public static final ModConfigSpec.DoubleValue HEAD_CUT_DAMAGE_PERCENT = BUILDER
            .comment("剪掉生物头时造成的伤害百分比，基于目标最大生命值（0.0=不造成伤害，0.1=10%，1.0=100%）")
            .translation("hasoook.config.head_cut_damage_percent")
            .defineInRange("headCutDamagePercent", 0.1, 0.0, 1.0);

    // 无头时是否造成失明效果
    public static final ModConfigSpec.BooleanValue HEADLESS_BLINDNESS = BUILDER
            .comment("生物或玩家在无头状态下是否会获得失明效果")
            .translation("hasoook.config.headless_blindness")
            .define("headlessBlindness", false);

    static {
        BUILDER.pop(); // 退出生物头配置组
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName
                && BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
    }
}
