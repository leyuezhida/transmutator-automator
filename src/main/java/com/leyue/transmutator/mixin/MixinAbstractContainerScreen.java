package com.leyue.transmutator.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把面板的点击接到<b>所有</b>容器界面上（实际只有嬗变台会用到）。
 * <p>
 * <b>为什么要挂在父类而不是 {@code GUITransmutationTable}</b>：
 * {@code mouseClicked} 定义在 {@code AbstractContainerScreen} 上，
 * 嬗变台界面只是继承它。在子类里注入父类方法，Mixin 找不到注入目标，
 * 而<b>无效注入默认只给 warning 不报错</b> —— 表现为"面板画出来了但点不动"，
 * 极难定位。日志里搜不到任何关于它的记录。
 * <p>
 * <b>为什么可以挂在父类</b>：所有容器界面都会经过这里，
 * 但 {@link com.leyue.transmutator.client.panel.TransmutatorPanel#mouseClicked}
 * 只在面板本帧登记过区域时才返回 true，且面板只在嬗变台界面渲染时才有区域 ——
 * 所以其他界面不受影响。
 * <p>
 * <b>顺序很重要</b>：原版先处理，它管着嬗变台自己的三个嬗变按钮、输入槽
 * 和玩家背包格 —— 那些点击必须照常生效，否则玩家就没法正常拿取物品了。
 * 面板的按钮贴在 GUI 之外，原版不认，才会落到这里。
 */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public abstract class MixinAbstractContainerScreen {

    @Inject(method = "mouseClicked", at = @At("TAIL"), remap = false)
    private void ta$onClick(double mouseX, double mouseY, int button,
                            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return; // 原版已处理
        }
        if (com.leyue.transmutator.client.panel.TransmutatorPanel
                .mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
