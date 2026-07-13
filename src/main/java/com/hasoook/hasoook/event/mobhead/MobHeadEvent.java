package com.hasoook.hasoook.event.mobhead;

import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModAttachments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 无头生物相关的事件处理。
 * <p>
 * 包含：
 * <ul>
 *   <li>无头失明：当生物头部被移除后，持续施加失明效果（需在配置中开启）</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class MobHeadEvent {

    /** 失明效果持续时间（tick），10 秒，足够长以覆盖渐入动画。 */
    private static final int BLINDNESS_DURATION = 200;
    /** 剩余时间低于此值时提前续期，确保在效果到期前替换，杜绝间隙。 */
    private static final int BLINDNESS_REFRESH_THRESHOLD = 20;

    /**
     * 每 tick（Pre 阶段）检查无头生物是否需要施加/续期失明效果。
     * <p>
     * 使用 {@link EntityTickEvent.Pre} 而非 Post，是因为 Pre 阶段效果尚未 tick，
     * 可以在失明到期 <b>之前</b> 就续期，避免效果先被移除再重新添加造成的闪烁间隙。
     * <p>
     * 持续时间设为 200 tick（10 秒），失明渐入动画约 20 tick，
     * 剩余的 180 tick（9 秒）均为稳定全黑状态。
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        // 仅在服务端处理
        if (event.getEntity().level().isClientSide()) return;

        // 仅处理生物实体
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        // 检查是否开启了无头失明配置
        if (!Config.HEADLESS_BLINDNESS.get()) return;

        // 检查目标是否无头
        if (!entity.hasData(ModAttachments.HEAD_REMOVED.get())
                || !Boolean.TRUE.equals(entity.getData(ModAttachments.HEAD_REMOVED.get()))) {
            return;
        }

        MobEffectInstance existing = entity.getEffect(MobEffects.BLINDNESS);

        // 效果即将到期（剩余 ≤ 20 tick），提前移除以便重新施加。
        // 在 Pre 阶段移除不会造成视觉闪烁，因为同 tick 内会立即补上新效果。
        if (existing != null && !existing.isInfiniteDuration()
                && existing.getDuration() <= BLINDNESS_REFRESH_THRESHOLD) {
            entity.removeEffect(MobEffects.BLINDNESS);
            existing = null;
        }

        // 没有失明效果 → 施加长时间失明
        if (existing == null) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, BLINDNESS_DURATION, 0, false, false, false));
        }
    }
}
