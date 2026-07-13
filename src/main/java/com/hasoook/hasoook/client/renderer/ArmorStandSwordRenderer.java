package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.mixin.EntityRenderDispatcherAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 盔甲架剑的 3D 盔甲渲染器。
 * 把存储在剑里的盔甲以立体模型渲染在剑身上（就像玩家穿在身上一样）。
 * <p>
 * 坐标系说明（基于 minecraft:item/handheld 模型空间）：
 * <ul>
 *   <li>X=0.5 水平居中；越大越靠右，越小越靠左</li>
 *   <li>Y 根据装备部位不同：头盔最上方，鞋子最下方</li>
 *   <li>Z=0.65 深度居中；越大越远离镜头，越小越靠近镜头</li>
 *   <li>缩放值控制盔甲大小，越小盔甲越小</li>
 * </ul>
 * 修改 getYOffset() 中的 Y 值即可对齐。
 */
public class ArmorStandSwordRenderer implements SpecialModelRenderer<ItemStack> {

    public static final ArmorStandSwordRenderer INSTANCE = new ArmorStandSwordRenderer();

    /** 缓存的 EquipmentLayerRenderer，用于立体盔甲渲染 */
    @Nullable
    private static EquipmentLayerRenderer cachedEquipmentRenderer;

    /** 缓存的盔甲模型集（头盔 / 胸甲 / 裤子 / 鞋子） */
    @Nullable
    private static ArmorModelSet<HumanoidModel<HumanoidRenderState>> cachedArmorModels;

    /** 缓存的鞘翅模型 */
    @Nullable
    private static ElytraModel cachedElytraModel;

    /**
     * 从剑的数据组件中读取所有盔甲（slot 0=头盔, 1=胸甲, 2=裤子, 3=鞋子），
     * 为每个非空物品附加一个特殊模型层。
     */
    public static void attach(ItemStackRenderState parentState, ItemStack swordStack) {
        ItemContainerContents contents = swordStack.getOrDefault(
                ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(),
                ItemContainerContents.EMPTY);

        List<ItemStack> items = contents.stream().toList();

        // 遍历四个盔甲槽位：0=头盔, 1=胸甲, 2=裤子, 3=鞋子
        for (int i = 0; i < Math.min(items.size(), 4); i++) {
            ItemStack armorStack = items.get(i).copy();
            if (!armorStack.isEmpty()) {
                parentState.newLayer().setupSpecialModel(INSTANCE, armorStack);
            }
        }
    }

    @Override
    public void submit(
            ItemStack armorStack,
            @NonNull ItemDisplayContext context,
            @NonNull PoseStack poseStack,
            @NonNull SubmitNodeCollector collector,
            int light,
            int overlay,
            boolean foil,
            int outline) {

        // GUI 和掉落物不渲染盔甲
        if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND) {
            return;
        }

        if (armorStack.isEmpty()) return;

        // 检查装备类型
        Equippable equippable = armorStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return;
        }

        EquipmentSlot slot = equippable.slot();

        // 只处理护甲槽位（头盔 / 胸甲 / 裤子 / 鞋子 / 身体）
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET
                && slot != EquipmentSlot.BODY) {
            return;
        }

        poseStack.pushPose();

        // ═══════════════════════════════════════════════════════════════
        // 位置调整入口：修改 getYOffset() 中的 Y 值来对齐各部位
        // X = 左右  |  Y = 上下  |  Z = 前后
        // ═══════════════════════════════════════════════════════════════

        float x = 0.5F;
        float y = getYOffset(slot);
        float z = 0.65F;
        float scale = 0.6F;

        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            // 第一人称主手
            poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
            poseStack.translate(x, y, z-0.1);
        } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            // 第一人称副手
            poseStack.translate(x, y, z);
        } else if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            // 第三人称
            poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
            poseStack.translate(x, y, z);
        } else {
            // 实体渲染（弹射物身上）
            poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poseStack.translate(x, y - 0.65F, z - 1.35F);
        }

        if (!equippable.assetId().isEmpty()) {
            // ═══════════════════════════════════════════════════════════════
            // 使用 EquipmentLayerRenderer 渲染立体盔甲模型
            // 参考 vanilla HumanoidArmorLayer + WingsLayer
            // ═══════════════════════════════════════════════════════════════

            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.scale(scale, scale, scale);

            EquipmentLayerRenderer equipmentRenderer = getOrCreateEquipmentRenderer();
            ResourceKey<EquipmentAsset> assetId = equippable.assetId().orElseThrow();
            HumanoidRenderState renderState = new HumanoidRenderState();

            if (armorStack.is(Items.ELYTRA)) {
                // 鞘翅
                renderState.elytraRotX = (float) (Math.PI / -6);
                renderState.elytraRotZ = (float) (Math.PI / -6);
                poseStack.mulPose(Axis.XP.rotationDegrees(35.0F));
                poseStack.scale(-1.0F, 1.0F, 1.0F);
                ElytraModel elytraModel = getOrCreateElytraModel();
                elytraModel.setupAnim(renderState);
                equipmentRenderer.renderLayers(
                        EquipmentClientInfo.LayerType.WINGS,
                        assetId,
                        elytraModel,
                        renderState,
                        armorStack,
                        poseStack,
                        collector,
                        light,
                        null,
                        0,
                        0
                );
            } else {
                // 普通护甲：复刻 HumanoidArmorLayer 的渲染方式
                Model armorModel = getOrCreateArmorModels().get(slot);
                EquipmentClientInfo.LayerType layerType = (slot == EquipmentSlot.LEGS)
                        ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                        : EquipmentClientInfo.LayerType.HUMANOID;
                equipmentRenderer.renderLayers(
                        layerType,
                        assetId,
                        armorModel,
                        renderState,
                        armorStack,
                        poseStack,
                        collector,
                        light,
                        0  // outlineColor
                );
            }
        } else {
            // 非标准装备（头颅、南瓜等）
            poseStack.mulPose(Axis.YN.rotationDegrees(90.0F));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0, 0.5, 0);

            renderAsStandardItem(armorStack, poseStack, collector, light);
        }

        poseStack.popPose();
    }

    private static void renderAsStandardItem(ItemStack stack, PoseStack poseStack,
                                             SubmitNodeCollector collector, int light) {
        Minecraft mc = Minecraft.getInstance();
        ItemStackRenderState itemState = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(
                itemState, stack, ItemDisplayContext.NONE, (Level) null, (ItemOwner) null, 0);
        itemState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    /**
     * 根据装备槽位返回对应的 Y 轴偏移。
     * 头盔在最上方，鞋子在最下方。
     */
    private static float getYOffset(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 1.1F;    // 头盔
            case CHEST, BODY -> 1.2F;  // 胸甲 / 鞘翅
            case LEGS -> 1.4F;   // 裤子
            case FEET -> 1.5F;  // 鞋子
            default -> 0.0F;
        };
    }

    /**
     * 获取或创建 {@link EquipmentLayerRenderer}。
     * 使用 {@link EntityRenderDispatcherAccessor} 从 EntityRenderDispatcher 获取
     * EquipmentAssetManager，再结合 TextureAtlas 构建渲染器。
     */
    private static EquipmentLayerRenderer getOrCreateEquipmentRenderer() {
        if (cachedEquipmentRenderer == null) {
            Minecraft minecraft = Minecraft.getInstance();
            EquipmentAssetManager equipmentAssets =
                    ((EntityRenderDispatcherAccessor) minecraft.getEntityRenderDispatcher()).getEquipmentAssets();
            TextureAtlas armorTrimsAtlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.ARMOR_TRIMS);
            cachedEquipmentRenderer = new EquipmentLayerRenderer(equipmentAssets, armorTrimsAtlas);
        }
        return cachedEquipmentRenderer;
    }

    /**
     * 获取或创建全套盔甲的 {@link ArmorModelSet}。
     * 使用 {@link ArmorModelSet#bake} 一次性 bake 头盔 / 胸甲 / 裤子 / 鞋子四个模型。
     */
    private static ArmorModelSet<HumanoidModel<HumanoidRenderState>> getOrCreateArmorModels() {
        if (cachedArmorModels == null) {
            Minecraft minecraft = Minecraft.getInstance();
            cachedArmorModels = ArmorModelSet.bake(
                    ModelLayers.PLAYER_ARMOR,
                    minecraft.getEntityModels(),
                    modelPart -> new HumanoidModel<>(modelPart)
            );
        }
        return cachedArmorModels;
    }

    /**
     * 获取或创建鞘翅模型 {@link ElytraModel}。
     */
    private static ElytraModel getOrCreateElytraModel() {
        if (cachedElytraModel == null) {
            Minecraft minecraft = Minecraft.getInstance();
            cachedElytraModel = new ElytraModel(
                    minecraft.getEntityModels().bakeLayer(ModelLayers.ELYTRA));
        }
        return cachedElytraModel;
    }

    @Override
    public void getExtents(@NonNull Consumer<Vector3fc> consumer) {
    }

    @Override
    public @Nullable ItemStack extractArgument(@NonNull ItemStack stack) {
        return stack;
    }
}
