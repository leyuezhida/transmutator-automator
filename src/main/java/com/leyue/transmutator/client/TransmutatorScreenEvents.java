package com.leyue.transmutator.client;

import com.github.alexthe666.alexsmobs.inventory.MenuTransmutationTable;
import com.leyue.transmutator.client.panel.ItemSelector;
import com.leyue.transmutator.client.panel.TransmutatorPanel;
import com.leyue.transmutator.core.CandidateSnapshot;
import com.leyue.transmutator.core.TransmutatorLoop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.fml.common.Mod;

/**
 * 嬗变台界面的全部交互入口：开关、渲染、点击、按键、字符输入。
 * <p>
 * <b>为什么从 Mixin 改成 Forge 事件</b>（2026-10-19，用户反馈后重写）：
 * 上一版用 Mixin 注入 {@code mouseClicked} 的 <b>TAIL</b> 做兜底，
 * 那是原版与所有模组都处理完之后才轮到我们。装了 JEI 的整合包里表现为：
 * <ul>
 *   <li>画面上本模组的面板盖住了 JEI 的书签，<b>但点上去执行的是 JEI</b>
 *       —— 因为 JEI 监听的是 Forge 事件，它比我们的 TAIL 注入更早拿到点击；</li>
 *   <li>点选择器时，下面的嬗变台槽位先被点中，选择器反而点不动。</li>
 * </ul>
 * 也就是"显示在上层、操作在下层"，两者彻底错位。
 * <p>
 * <b>改法</b>：在 {@link ScreenEvent} 的 <b>Pre</b> 阶段用
 * {@link EventPriority#HIGHEST} 拦截，命中本模组控件就 {@code setCanceled(true)}，
 * 原版与 JEI 都收不到这次输入 —— <b>显示顺序与响应顺序因此一致</b>。
 * <p>
 * <b>渲染用 {@link EventPriority#LOWEST}</b>：最后画，确保面板在最上层，
 * 与"点击归我们"的语义对齐。若反过来画在 JEI 底下，遮挡问题会变成
 * "看不见但点得到"，更难用。
 */
@Mod.EventBusSubscriber(modid = "transmutator_automator", value = Dist.CLIENT)
public final class TransmutatorScreenEvents {

    private TransmutatorScreenEvents() {
    }

    /** 界面是否是嬗变台。 */
    private static boolean isTransmutationTable(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getMenu() instanceof MenuTransmutationTable;
    }

    // ==================== 生命周期 ====================

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (isTransmutationTable(event.getNewScreen())) {
            TransmutatorLoop.onScreenOpened();
            // 面板的目标列表与循环共享同一份数据，打开时同步一次
            TransmutatorPanel.syncTargets();
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        // Closing 继承 ScreenEvent，方法名是 getScreen()（不是 getOldScreen）
        if (isTransmutationTable(event.getScreen())) {
            TransmutatorLoop.onScreenClosed();
            CandidateSnapshot.clear();
            // 关闭界面时才落盘：点击事件里同步写文件会导致卡死
            TransmutatorPanel.closeSelector();
            TransmutatorPanel.flush();
        }
    }

    // ==================== 渲染 ====================

    /**
     * 在界面渲染完成后画面板与选择器。
     * <p>
     * <b>为什么用 {@code Render.Post} 而不是 Mixin 注入 render 的 TAIL</b>：
     * Forge 事件带优先级，能明确表达"我最后画"；而 Mixin 的 TAIL 顺序
     * 取决于其他模组的注入点，装了 JEI 之类的东西后就不确定了。
     * <p>
     * 优先用 {@code LOWEST}：在 JEI 之后画，面板压在最上层。
     * 这与 {@link #onMousePressed} 里"面板优先响应"是同一套语义。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isTransmutationTable(screen)) {
            return;
        }
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        TransmutatorPanel.render(
                event.getGuiGraphics(),
                Minecraft.getInstance(),
                screen.width, screen.height,
                container.getGuiLeft(), container.getGuiTop(),
                event.getMouseX(), event.getMouseY());

        // 选择器单独画在这里，不放进 TransmutatorPanel.render 里。
        // 原因：面板在屏幕过矮时会走 compact 分支提前 return，
        // 而选择器此时仍然 isOpen、仍然会吞掉所有点击 —— 就会出现
        // "点哪儿都没反应，但屏幕上根本看不到选择器"。
        // 两者的绘制条件必须解耦。
        if (ItemSelector.isOpen()) {
            ItemSelector.beginFrame();
            ItemSelector.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    // ==================== HUD ====================

    /**
     * 选择器打开时隐藏原版 HUD 热键栏。
     * <p>
     * <b>为什么全屏遮罩盖不住它</b>（2026-10-19 玩家第二次指出"怎么把嬗变台界面的
     * 快捷栏也显示了"）：HUD 由 {@code Gui} 在 <b>界面渲染之后</b> 单独绘制，
     * 不在 {@code Screen.render} 的调用链里。所以哪怕用不透明色铺满整个屏幕，
     * 热键栏仍会画在遮罩之上。
     * <p>
     * 只取消 {@code HOTBAR} 这一个覆盖层，生命/饥饿/经验等保持原样 ——
     * 玩家在选物品时仍然能看见自己的血量。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!ItemSelector.isOpen()) {
            return;
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);
        }
    }

    // ==================== 输入 ====================
    /**
     * 鼠标按下。
     * <p>
     * <b>{@code HIGHEST} 是必须的</b>：JEI 也监听这个事件，谁的优先级高谁先跑。
     * 上一版用 Mixin TAIL 时永远排在 JEI 后面，于是出现了
     * "面板盖在书签上、点击却落到书签"的怪象。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isTransmutationTable(event.getScreen())) {
            return;
        }
        double mx = event.getMouseX();
        double my = event.getMouseY();

        // 选择器是模态：开着的时候吞掉所有点击，绝不透到下面的嬗变台与背包
        if (ItemSelector.isOpen()) {
            ItemSelector.onClick(mx, my, event.getButton());
            event.setCanceled(true);
            return;
        }

        if (TransmutatorPanel.mouseClicked(mx, my, event.getButton())) {
            event.setCanceled(true);
        }
    }

    /** 松开鼠标：选择器模态期间同样吞掉，避免拖拽状态泄漏到下层。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (ItemSelector.isOpen() && isTransmutationTable(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    /**
     * 键盘按下。
     * <p>
     * 注意这里<b>只管功能键</b>（Esc、Tab、退格、方向键）。
     * 文字输入走 {@link #onCharTyped} —— 这是上一版打不进字的根因：
     * 只处理了 {@code keyPressed}，而 Minecraft 的字符输入走 {@code charTyped}。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!isTransmutationTable(event.getScreen())) {
            return;
        }
        if (ItemSelector.isOpen()) {
            if (ItemSelector.onKey(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
                event.setCanceled(true);
            }
            return;
        }
    }

    /**
     * 字符输入。
     * <p>
     * <b>这是搜索框打不进字的原因</b>（2026-10-19 用户反馈"中英文都无法输入"）：
     * {@code keyPressed} 只拿到键码，不含实际字符；真正产生文字的是
     * {@code charTyped(char, int)}。上一版从来没接管过它，
     * 所以 EditBox 的聚焦状态是对的、光标在闪，但一个字也进不去。
     * <p>
     * 中文与英文都走这条路：输入法组字完成后同样以 char 形式送到这里，
     * 因此不需要为中文单独处理。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!isTransmutationTable(event.getScreen())) {
            return;
        }
        if (!ItemSelector.isOpen()) {
            return;
        }
        ItemSelector.onChar(event.getCodePoint(), event.getModifiers());
        event.setCanceled(true);
    }
}
