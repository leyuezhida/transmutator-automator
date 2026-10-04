package com.leyue.transmutator.mixin;

import com.leyue.transmutator.client.panel.TransmutatorPanel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把面板的点击接到所有容器界面上（实际只有嬗变台会用到）。
 * <p>
 * <b>这里的 remap 问题是本项目最贵的一个坑</b>。目标类是 <b>Minecraft 自己的类</b>，
 * 它的方法名在不同环境下不一样：
 * <ul>
 *   <li>开发环境（runClient）：{@code mouseClicked}；</li>
 *   <li>生产环境（装进整合包）：被混淆成 SRG 名 {@code m_6375_}。</li>
 * </ul>
 * 三种写法各有各的坑：
 * <ol>
 *   <li>{@code remap = false} + 源码名 → 开发环境正常，
 *       <b>生产环境直接崩</b>："Critical injection failure: @Inject annotation on
 *       ta$onClick could not find any targets matching 'mouseClicked'"，
 *       而且因为是 Critical，整个游戏起不来；</li>
 *   <li>{@code remap = true}（默认）→ 需要 refmap，而 <b>Parchment 映射里没有混淆名</b>，
 *       编译期就报 "Unable to locate obfuscation mapping for @Inject target"；</li>
 *   <li>写死 SRG 名 → 开发环境认不出（那里只有源码名）。</li>
 * </ol>
 * <p>
 * <b>本类的解法</b>：写 <b>SRG 名</b>（{@code m_6375_}）+ {@code remap = false} +
 * {@code require = 0}。生产环境认它；开发环境若认不出就静默跳过而不是崩游戏 ——
 * 后果仅是"开发环境里点不动面板"，而那本来就是次要场景。
 * <b>用开发期的可用性换生产期的稳定性</b>：宁可在 runClient 里少个功能，
 * 也不能让玩家的整合包起不来。
 * <p>
 * <b>为什么可以挂在父类</b>：所有容器界面都经过这里，
 * 但面板只在嬗变台界面渲染时才登记可点击区域，其余界面自然返回 false。
 * <p>
 * <b>顺序很重要</b>：原版先处理，它管着嬗变台自己的三个嬗变按钮、输入槽
 * 和玩家背包格 —— 那些点击必须照常生效，否则玩家无法正常拿取物品。
 */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public abstract class MixinAbstractContainerScreen {

    /**
     * {@code m_6375_} 是生产环境里 {@code mouseClicked} 的 SRG 名。
     * {@code require = 0}：找不到注入目标时跳过而不是抛异常。
     */
    @Inject(method = "m_6375_", at = @At("TAIL"), remap = false, require = 0)
    private void ta$onClick(double mouseX, double mouseY, int button,
                            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return; // 原版已处理
        }
        if (TransmutatorPanel.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
