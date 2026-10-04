package com.leyue.transmutator.mixin;

import com.github.alexthe666.alexsmobs.client.gui.GUITransmutationTable;
import com.leyue.transmutator.client.panel.TransmutatorPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把操作面板挂到嬗变台界面上。
 * <p>
 * <b>为什么依附而不是独立界面</b>：刷嬗变台的操作全发生在嬗变台界面开着的时候 ——
 * 选目标、看候选、判断该不该点。独立界面要来回切换，而且在独立界面里
 * 看不到"现在会点哪个"，容易把想留的物品点掉。挂上去之后信息与操作同处一屏。
 * <p>
 * <b>为什么不用 addRenderableWidget</b>：嬗变台的渲染每帧调用，
 * 而 {@code addRenderableWidget} 会把控件累积进 children 列表，
 * 界面开久了必然内存爆炸。所以面板自己每帧重建可点击区域列表。
 * <p>
 * <b>注入点</b>：
 * <ul>
 *   <li>{@code renderBg}（m_7286_）之后 —— 背景已画、内容未画，
 *       面板不会被原版元素盖住；</li>
 *   <li>{@code mouseClicked} TAIL —— 先让原版处理（容器点击、翻页），
 *       没消费掉的再交给面板。</li>
 * </ul>
 * <p>
 * 目标类属于 Alex's Mobs，方法名不是 Forge 映射产物，所以 {@code remap = false}。
 */
@Mixin(value = GUITransmutationTable.class, remap = false)
public abstract class MixinTransmutationTableGui {

    /**
     * 在界面的 {@code render} 末尾画面板。
     * <p>
     * <b>为什么挂 render 而不是 renderBg</b>：这是本次踩过的坑。
     * 父类 {@code AbstractContainerScreen} 里 {@code renderBg} 是<b>抽象声明</b>，
     * 调用点用的方法名是未混淆的 {@code renderBg}；
     * 而子类 {@code GUITransmutationTable} 的实现才叫 {@code m_7286_}。
     * 往子类注入 {@code m_7286_} 时 Mixin 找不到目标，
     * 而<b>无效注入默认只 warning 不报错</b> ——
     * 表现是"代码写对了、Mixin 也没报错、但面板就是不出来"。
     * <p>
     * 挂在 {@code render} 的 TAIL 最稳：它一定被调用，且此时背景与内容都已画完，
     * 面板不会被原版元素盖住。
     * <p>
     * <b>为什么不挂父类的 render</b>：那会让所有容器界面都走这段绘制。
     * 限定在嬗变台子类上，只有它会用到面板。
     */
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void ta$renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                CallbackInfo ci) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        var mc = net.minecraft.client.Minecraft.getInstance();
        // leftPos / topPos 是 protected，跨包取不到，必须走 @Accessor
        var accessor = (MixinTransmutationTableGuiAccessor) (Object) this;
        int gx = accessor.ta$getLeftPos();
        int gy = accessor.ta$getTopPos();
        // 一次性诊断：确认注入点真的命中（正常只打一次）
        if (!announced) {
            announced = true;
            com.leyue.transmutator.core.TransmutatorLog.info(
                    "面板已挂上嬗变台界面：gui=({},{}) 界面={}x{}", gx, gy, screen.width, screen.height);
        }
        TransmutatorPanel.render(graphics, mc, screen.width, screen.height, gx, gy);
    }

    private static boolean announced;
}
