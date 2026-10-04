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
     * <b>为什么 {@code remap} 必须是默认的 true</b>：这个类虽然属于 Alex's Mobs，
     * 但 {@code render} 方法<b>是 Minecraft 的方法</b>（父类 {@code AbstractContainerScreen}
     * 声明，Alex's Mobs 只是覆写它）。在 dev 环境的 jar 里它叫 {@code m_88315_}
     * 而不叫 {@code render}，所以 {@code remap = false} 会按字面名字找、找不到目标，
     * <b>静默跳过</b> —— 表现就是"Mixin 没报错、代码也对，但面板就是不出来"。
     * 只有让 Mixin 走 refmap，才能把它映射到当前环境的实际名字。
     * <p>
     * <b>类上的 {@code remap = false} 只影响"目标类名"与"本类新增成员"的映射</b>，
     * 不会阻止方法注入单独指定 remap。所以类保持 {@code remap = false}
     * （{@code GUITransmutationTable} 本身不是 Forge 映射的产物），
     * 而这个注入点单独用默认的 {@code remap = true}。
     * <p>
     * <b>为什么挂 render 而不是 renderBg</b>：父类的 {@code renderBg} 是抽象声明，
     * 调用点用未混淆名，而子类实现叫 {@code m_7286_} —— 注入哪个都匹配不上。
     * {@code render} 一定被调用，且 TAIL 时背景与内容都已画完，
     * 面板不会被原版元素盖住。
     */
    @Inject(method = "m_88315_", at = @At("TAIL"), remap = false, require = 0)
    private void ta$renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                CallbackInfo ci) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        var mc = net.minecraft.client.Minecraft.getInstance();
        // 直接用 AbstractContainerScreen 的 public getGuiLeft()/getGuiTop()。
        // 不用 leftPos 字段是因为它是 protected 且在生产环境被混淆，
        // 而这两个 public 方法在两个环境下名字一致，不需要任何 remap 处理。
        int gx = screen.getGuiLeft();
        int gy = screen.getGuiTop();
        // 一次性诊断：确认注入点真的命中（正常只打一次）
        if (!announced) {
            announced = true;
            com.leyue.transmutator.core.TransmutatorLog.info(
                    "面板已挂上嬗变台界面：gui=({},{}) 界面={}x{}", gx, gy, screen.width, screen.height);
        }
        TransmutatorPanel.render(graphics, mc, screen.width, screen.height, gx, gy, mouseX, mouseY);
    }

    private static boolean announced;
}
