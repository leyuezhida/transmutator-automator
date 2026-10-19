package com.leyue.transmutator.mixin;

import com.github.alexthe666.alexsmobs.client.gui.GUITransmutationTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在嬗变台界面里隐藏玩家主背包（27 格），只保留热键栏。
 * <p>
 * <b>为什么保留热键栏</b>：玩家需要往嬗变台的输入槽里放物品，而放物品靠的是
 * 从背包点起再点进槽位。整块藏掉就连物品都放不进去了，功能直接废掉。
 * 保留最下面一行 9 格，够用。
 * <p>
 * <b>为什么渲染和点击都要拦</b>：只取消渲染的话，格子在视觉上消失，
 * 但点击检测仍然按原坐标生效 —— 玩家点到一块空白会触发看不见的背包操作，
 * 这种"幽灵格子"比直接显示更糟糕。
 * <p>
 * <b>为什么要判断 {@code instanceof GUITransmutationTable}</b>：
 * 注入点是 {@code AbstractContainerScreen}，那是所有容器界面（箱子、工作台、
 * 熔炉、背包）的父类。不加判断会把全游戏的背包格都藏掉。
 * <p>
 * <b>SRG 名</b>：{@code renderSlot} = {@code m_280092_}，{@code slotClicked} = {@code m_6597_}。
 * 两者都是父类方法，生产环境用 SRG 名，所以 {@code remap = false} 直接按字面匹配。
 * {@code require = 0} 表示找不到目标就静默跳过 —— 宁可功能不生效，也别让游戏起不来。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {

    /**
     * 隐藏主背包槽位的渲染。
     * <p>
     * 只取消槽位本身（物品图标 + 高亮），GUI 背景图仍会透出来，
     * 所以视觉上是"一块空白区域"而不是黑洞。
     */
    @Inject(method = "m_280092_", at = @At("HEAD"), remap = false,
            require = 0, cancellable = true)
    private void ta$hideMainInventorySlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (ta$isHiddenSlot(slot)) {
            ci.cancel();
        }
    }

    /**
     * 拦截主背包槽位的点击。
     * <p>
     * 与上面的渲染取消成对存在：只藏不拦会留下"点得到但看不见"的格子。
     */
    @Inject(method = "m_6597_", at = @At("HEAD"), remap = false,
            require = 0, cancellable = true)
    private void ta$blockMainInventoryClick(Slot slot, int slotId, int mouseButton,
                                            ClickType type, CallbackInfo ci) {
        if (ta$isHiddenSlot(slot)) {
            ci.cancel();
        }
    }

    /**
     * 是否是要隐藏的槽位：嬗变台界面里的玩家主背包。
     * <p>
     * 判据是 {@code getContainerSlot() >= 9} —— 玩家 Inventory 的 0~8 是热键栏，
     * 9~35 是主背包。用这个而不是槽位下标，是因为嬗变台自己的槽位排在玩家背包之前，
     * 下标会随对方调整菜单布局而变。
     */
    private boolean ta$isHiddenSlot(Slot slot) {
        if (!((Object) this instanceof GUITransmutationTable)) {
            return false;
        }
        return slot != null
                && slot.container instanceof Inventory
                && slot.getContainerSlot() >= 9;
    }
}
