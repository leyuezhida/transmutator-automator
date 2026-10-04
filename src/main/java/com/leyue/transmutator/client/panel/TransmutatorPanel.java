package com.leyue.transmutator.client.panel;

import com.leyue.transmutator.config.TransmutatorConfig;
import com.leyue.transmutator.core.CandidateSnapshot;
import com.leyue.transmutator.core.MarkerMatcher;
import com.leyue.transmutator.core.TransmutatorLoop;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 依附于嬗变台界面的操作面板。
 * <p>
 * <b>为什么不做成独立界面</b>：刷嬗变台的整个操作都发生在嬗变台界面 opened 期间 ——
 * 选目标、看候选、判断该不该点。独立界面反而要求玩家来回切换，
 * 而且看不到"现在会点哪个"，容易误操作（把想留的物品点掉）。
 * 依附在嬗变台界面上，这些信息与操作同处一屏。
 * <p>
 * <b>为什么自己画而不用 Button 控件</b>：嬗变台的 {@code render} 每帧调用，
 * {@code addRenderableWidget} 会把控件不断累积进 children 列表，
 * 界面开久了必然内存爆炸。所以每帧重建一份"可点击区域"列表，
 * 渲染与命中检测用同一份数据，不会失配。
 * <p>
 * <b>布局</b>：贴在嬗变台 GUI 的<b>左侧空白</b>（那里没有候选槽与背包），
 * 纵向排列：开关 → 目标列表 → 目标数量 → 说明。窄屏时自动折叠成一行提示。
 */
public class TransmutatorPanel {

    /** 格子边长，与原版物品栏一致。 */
    private static final int SLOT = 18;
    /** 面板宽度。 */
    private static final int PANEL_W = 100;
    /** 目标图标最多排几列。 */
    private static final int COLS = 5;
    /** 每行最多显示多少个目标（超出用 "+N" 概括）。 */
    private static final int MAX_ROWS = 4;

    /** 本帧的可点击区域。 */
    private static final List<Hotspot> HOTSPOTS = new ArrayList<>();

    /** 目标物品缓存：只在界面打开时同步一次，不每帧读配置。 */
    private static final List<Item> TARGETS = new ArrayList<>();

    private TransmutatorPanel() {
    }

    /** 从配置同步到面板缓存。 */
    public static void syncTargets() {
        TARGETS.clear();
        TARGETS.addAll(MarkerMatcher.parse(TransmutatorConfig.markerList()));
    }

    /**
     * 在嬗变台界面上绘制面板。
     *
     * @param screenWidth  界面宽度（{@code Screen#width}）
     * @param screenHeight 界面高度
     * @param guiLeft      嬗变台 GUI 左边界 x
     * @param guiTop       嬗变台 GUI 上边界 y
     */
    public static void render(GuiGraphics g, net.minecraft.client.Minecraft mc,
                              int screenWidth, int screenHeight,
                              int guiLeft, int guiTop) {
        // 目标列表由 TransmutatorScreenEvents 在界面打开时 syncTargets() 填好，
        // 这里不重复读配置 —— 否则每帧都要反序列化一遍列表
        HOTSPOTS.clear();

        // 放在嬗变台 GUI 左侧；左侧放不下就改放右侧
        int panelX = guiLeft - PANEL_W - 4;
        if (panelX < 2) {
            panelX = guiLeft + 176 + 4;
        }
        int panelY = Math.max(4, guiTop);
        // 屏幕太矮就只画最上面那块（开关 + 目标），其余略去
        boolean compact = panelY + 150 > screenHeight - 4;

        drawPanel(g, panelX, panelY, compact);

        int y = panelY + 12;

        // ---- 开关 ----
        boolean enabled = TransmutatorConfig.ENABLED.get();
        drawButton(g, panelX + 6, y, PANEL_W - 12,
                Component.translatable(enabled
                        ? "gui.transmutator_automator.on"
                        : "gui.transmutator_automator.off"),
                enabled ? 0xFF2E7D32 : 0xFF6A6A6A,
                () -> {
                    TransmutatorConfig.ENABLED.set(!enabled);
                    save();
                });
        y += 26;

        // ---- 目标列表 ----
        drawTargetList(g, mc, panelX, y);
        y += (TARGETS.isEmpty() ? 20 : MAX_ROWS * (SLOT + 1) + 4);

        if (compact) {
            drawStatus(g, panelX, y + 2);
            return;
        }
        y += 6;

        // ---- 目标数量 ----
        drawButton(g, panelX + 6, y, 18, Component.literal("-"), 0xFF4A4A4A, () -> {
            int next = Math.max(1, TransmutatorConfig.TARGET_COUNT.get() - 1);
            TransmutatorConfig.TARGET_COUNT.set(next);
            save();
            TransmutatorLoop.setTargetTotal(next);
        });
        drawValueBox(g, panelX + 26, y, 40, String.valueOf(TransmutatorConfig.TARGET_COUNT.get()));
        drawButton(g, panelX + 68, y, 18, Component.literal("+"), 0xFF4A4A4A, () -> {
            int next = TransmutatorConfig.TARGET_COUNT.get() + 1;
            TransmutatorConfig.TARGET_COUNT.set(next);
            save();
            TransmutatorLoop.setTargetTotal(next);
        });
        y += 24;

        // ---- 模式 ----
        boolean all = TransmutatorConfig.REQUIRE_ALL_THREE.get();
        drawButton(g, panelX + 6, y, PANEL_W - 12,
                Component.translatable(all
                        ? "gui.transmutator_automator.mode_all"
                        : "gui.transmutator_automator.mode_any"),
                0xFF4A4A4A,
                () -> {
                    TransmutatorConfig.REQUIRE_ALL_THREE.set(!all);
                    save();
                });
        y += 26;

        // ---- 候选预览：显示"会点哪个" ----
        drawCandidatePreview(g, panelX, y);
        y += 34;

        drawStatus(g, panelX, y);
    }

    /**
     * 目标列表：图标横排，悬停高亮，点一下移除。
     * <p>
     * 直接显示物品图标而不是名字 —— 一眼就能认出是什么，
     * 比读文字快，也不必担心名字太长挤爆面板。
     */
    private static void drawTargetList(GuiGraphics g, net.minecraft.client.Minecraft mc,
                                       int panelX, int panelY) {
        if (TARGETS.isEmpty()) {
            g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    Component.translatable("gui.transmutator_automator.no_targets"),
                    panelX + 6, panelY + 5, 0x808080, false);
            return;
        }
        for (int i = 0; i < TARGETS.size() && i < COLS * MAX_ROWS; i++) {
            int ix = panelX + 6 + (i % COLS) * (SLOT + 1);
            int iy = panelY + (i / COLS) * (SLOT + 1);
            g.renderItem(new ItemStack(TARGETS.get(i)), ix, iy);
            final int idx = i;
            HOTSPOTS.add(new Hotspot(ix, iy, SLOT, SLOT, () -> {
                TARGETS.remove(idx);
                save();
            }));
        }
        if (TARGETS.size() > COLS * MAX_ROWS) {
            g.drawString(mc.font, Component.literal("+" + (TARGETS.size() - COLS * MAX_ROWS)),
                    panelX + 6 + COLS * (SLOT + 1), panelY + 6, 0xA0A0A0, false);
        }
        // 清空
        drawButton(g, panelX + 6, panelY + MAX_ROWS * (SLOT + 1), PANEL_W - 12,
                Component.translatable("gui.transmutator_automator.clear"),
                0xFF5A3A3A, () -> {
                    TARGETS.clear();
                    save();
                });
    }

    /**
     * 候选预览：把嬗变台当前三个候选的小图标搬过来，命中的标绿框。
     * <p>
     * 这样玩家不用在嬗变台界面与本面板之间来回看 —— 伸手前就知道会点掉什么。
     */
    private static void drawCandidatePreview(GuiGraphics g, int panelX, int panelY) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        Set<Item> targets = new LinkedHashSet<>(TARGETS);
        for (int i = 0; i < 3; i++) {
            ItemStack stack = CandidateSnapshot.get(i);
            int ix = panelX + 6 + i * (SLOT + 2);
            g.fill(ix, panelY, ix + SLOT, panelY + SLOT, 0xFF1A1A1A);
            outline(g, ix, panelY, 0xFF3A3A3A);
            if (stack.isEmpty()) {
                continue;
            }
            boolean hit = targets.contains(stack.getItem());
            outline(g, ix, panelY, hit ? 0xFF66DD66 : 0xFF3A3A3A);
            g.renderItem(stack, ix + 1, panelY + 1);
        }
    }

    private static void drawStatus(GuiGraphics g, int panelX, int panelY) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        String reason = TransmutatorLoop.stopReason();
        String text = TransmutatorLoop.collected() + "/" + TransmutatorLoop.targetTotal()
                + (reason == null ? "" : " " + reason);
        g.drawString(mc.font, text, panelX + 6, panelY, 0x9A9A9A, false);
    }

    // ==================== 背包点选 ====================

    /**
     * 处理玩家背包区的点击（嬗变台界面下方那 27 格）。
     * <p>
     * <b>这一段是整个依附式设计的关键</b>：嬗变台界面本来就画出了玩家背包，
     * 所以点背包就能加目标，不需要另开界面、也不用让玩家从物品网格里翻找。
     *
     * @param slot 玩家背包槽位（0..35；>=9 为主背包，<9 为快捷栏）
     * @return true 表示点击被消费
     */
    public static boolean onInventorySlotClicked(int slot) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || slot < 0 || slot >= 36) {
            return false;
        }
        ItemStack stack = mc.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (TARGETS.contains(item)) {
            TARGETS.remove(item); // 再点一次取消标记
        } else {
            TARGETS.add(item);
        }
        save();
        return true;
    }

    // ==================== 绘制工具 ====================

    private static void drawPanel(GuiGraphics g, int x, int y, boolean compact) {
        int h = compact ? 60 : 158;
        // 半透明底板：不遮住嬗变台本体，又能看清边界
        g.fill(x - 2, y - 2, x + PANEL_W + 2, y + h, 0xB0101010);
        outline(g, x - 2, y - 2, 0xFF505050);
        g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("gui.transmutator_automator.short_title"),
                x + 4, y + 2, 0x606060, false);
    }

    private static void drawButton(GuiGraphics g, int x, int y, int w, Component text,
                                   int color, Runnable onClick) {
        g.fill(x, y, x + w, y + 18, color);
        var mc = net.minecraft.client.Minecraft.getInstance();
        g.drawString(mc.font, text,
                x + (w - mc.font.width(text)) / 2, y + 5, 0xFFFFFF, false);
        if (onClick != null) {
            HOTSPOTS.add(new Hotspot(x, y, w, 18, onClick));
        }
    }

    private static void drawValueBox(GuiGraphics g, int x, int y, int w, String value) {
        g.fill(x, y, x + w, y + 18, 0xFF303030);
        var mc = net.minecraft.client.Minecraft.getInstance();
        g.drawString(mc.font, value, x + (w - mc.font.width(value)) / 2, y + 5, 0xDDDDDD, false);
    }

    private static void outline(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + SLOT, y + 1, color);
        g.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, color);
        g.fill(x, y + 1, x + 1, y + SLOT - 1, color);
        g.fill(x + SLOT - 1, y + 1, x + SLOT, y + SLOT - 1, color);
    }

    private static void writeConfig() {
        TransmutatorConfig.SPEC.save();
        TransmutatorLoop.reloadTargets();
    }

    private static void save() {
        List<String> list = TransmutatorConfig.markerList();
        list.clear();
        for (Item item : TARGETS) {
            var key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null) {
                list.add(key.toString());
            }
        }
        writeConfig();
    }

    /**
     * 命中检测：坐标落在本帧任一区域内则执行其动作。
     * <p>
     * 倒序遍历：后加入的区域画在上层，应当优先响应。
     *
     * @return true 表示点击已被消费（应阻止原版处理）
     */
    public static boolean mouseClicked(double mx, double my, int button) {
        for (int i = HOTSPOTS.size() - 1; i >= 0; i--) {
            Hotspot spot = HOTSPOTS.get(i);
            if (spot.x <= mx && mx < spot.x + spot.w
                    && spot.y <= my && my < spot.y + spot.h) {
                spot.action().run();
                return true;
            }
        }
        return false;
    }

    /** 可点击区域。 */
    private record Hotspot(int x, int y, int w, int h, Runnable action) {
    }

    /** 供外部（如背包高亮）查询当前目标集合。 */
    public static Set<Item> currentTargets() {
        return new LinkedHashSet<>(TARGETS);
    }
}
