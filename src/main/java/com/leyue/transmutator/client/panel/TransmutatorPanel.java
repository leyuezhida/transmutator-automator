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
import net.minecraft.world.item.Items;
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
    /** 面板宽度：够放 8 列图标（8*19=152 太宽，5 列只 95 又太少，6 列折中）。 */
    private static final int PANEL_W = 116;
    /** 目标图标每行几个（受 PANEL_W 限制：6 个 * 19px = 114，正好放下）。 */
    private static final int COLS = 6;
    /** 每行最多显示多少个目标（超出用 "+N" 概括）。 */
    private static final int MAX_ROWS = 3;
    /** 底板左右内边距。 */
    private static final int PAD = 6;
    /** 底板上下内边距。 */
    private static final int PAD_Y = 8;
    /** 按钮高度。 */
    private static final int BTN_H = 18;
    /** 元素之间的竖直间距。 */
    private static final int GAP = 6;
    /** 目标数量上限：36 组 × 64 个，超过就没意义了。 */
    private static final int MAX_COUNT = 2304;
    /**
     * 完整面板高度。
     * <p>
     * <b>按最坏情况（{@link #MAX_ROWS} 行目标）算出来的</b>，不是按当前内容 ——
     * 早先写死 152 时，1 个目标刚好、7 个就溢出 16px。加目标却看着面板变矮变歪。
     * 公式：{@code PAD_Y + (BTN_H+GAP) + (MAX_ROWS*(SLOT+1) + BTN_H + GAP)
     * + (BTN_H+GAP) + (BTN_H+GAP) + (SLOT+GAP) + 状态文字行}
     */
    private static final int PANEL_H = 194;

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
                              int guiLeft, int guiTop,
                              int mouseX, int mouseY) {
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
        boolean compact = panelY + PANEL_H > screenHeight - 4;

        drawPanel(g, panelX, panelY, compact);

        int y = panelY + PAD_Y;

        // ---- 开关 ----
        boolean enabled = TransmutatorConfig.ENABLED.get();
        drawButton(g, panelX + PAD, y, PANEL_W - PAD * 2,
                Component.translatable(enabled
                        ? "gui.transmutator_automator.on"
                        : "gui.transmutator_automator.off"),
                enabled ? 0xFF2E7D32 : 0xFF6A6A6A,
                () -> {
                    TransmutatorConfig.ENABLED.set(!enabled);
                    save();
                });
        y += BTN_H + GAP;

        // ---- 目标列表（图标 + 清空按钮）----
        y = drawTargetList(g, panelX, y, mouseX, mouseY);

        if (compact) {
            drawStatus(g, panelX + PAD, y);
            return;
        }

        // ---- 目标数量：可直接点数字框输入 ----
        drawButton(g, panelX + PAD, y, 18, Component.literal("-"), 0xFF4A4A4A, () -> {
            applyCount(TransmutatorConfig.TARGET_COUNT.get() - 1);
        });
        drawCountBox(g, mc, panelX + PAD + 20, y, 44, mouseX, mouseY);
        drawButton(g, panelX + PAD + 66, y, 18, Component.literal("+"), 0xFF4A4A4A, () -> {
            applyCount(TransmutatorConfig.TARGET_COUNT.get() + 1);
        });
        y += BTN_H + GAP;

        // ---- 模式 ----
        boolean all = TransmutatorConfig.REQUIRE_ALL_THREE.get();
        drawButton(g, panelX + PAD, y, PANEL_W - PAD * 2,
                Component.translatable(all
                        ? "gui.transmutator_automator.mode_all"
                        : "gui.transmutator_automator.mode_any"),
                0xFF4A4A4A,
                () -> {
                    TransmutatorConfig.REQUIRE_ALL_THREE.set(!all);
                    save();
                });
        y += BTN_H + GAP;

        // ---- 候选预览 ----
        drawCandidatePreview(g, panelX + PAD, y);
        y += SLOT + GAP;

        drawStatus(g, panelX + PAD, y);

        // 选择器最后画：它铺在孖变台之上
        if (ItemSelector.isOpen()) {
            ItemSelector.beginFrame();
            ItemSelector.render(g, mouseX, mouseY);
        }
    }

    /**
     * 目标列表：图标横排，悬停高亮，点一下移除。
     * <p>
     * 直接显示物品图标而不是名字 —— 一眼就能认出是什么，
     * 比读文字快，也不必担心名字太长挤爆面板。
     */
    private static int drawTargetList(GuiGraphics g, int panelX, int panelY,
                                      int mouseX, int mouseY) {
        int y = panelY;
        if (TARGETS.isEmpty()) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            g.drawString(mc.font,
                    Component.translatable("gui.transmutator_automator.no_targets"),
                    panelX + PAD, y + 4, 0x808080, false);
            y += BTN_H;
        } else {
            int rows = Math.min(MAX_ROWS, (TARGETS.size() + COLS - 1) / COLS);
            var mc = net.minecraft.client.Minecraft.getInstance();
            for (int i = 0; i < TARGETS.size() && i < COLS * rows; i++) {
                int ix = panelX + PAD + (i % COLS) * (SLOT + 1);
                int iy = y + (i / COLS) * (SLOT + 1);
                g.renderItem(new ItemStack(TARGETS.get(i)), ix, iy);
                // 悬停时描边高亮，提示"点一下移除"
                if (hover(mouseX, mouseY, ix, iy, SLOT, SLOT)) {
                    outlineItem(g, ix, iy, 0xFFFF5555);
                    g.renderTooltip(mc.font, new ItemStack(TARGETS.get(i)), mouseX, mouseY);
                }
                final int idx = i;
                HOTSPOTS.add(new Hotspot(ix, iy, SLOT, SLOT, () -> {
                    TARGETS.remove(idx);
                    save();
                }));
            }
            int lineW = COLS * (SLOT + 1);
            if (TARGETS.size() > COLS * rows) {
                g.drawString(mc.font,
                        Component.literal("+" + (TARGETS.size() - COLS * rows)),
                        panelX + PAD + lineW, y + 6, 0xA0A0A0, false);
            }
            y += rows * (SLOT + 1);
        }

        // 底下一行：左"添加"（打开选择器）、右"清空"
        drawButton(g, panelX + PAD, y, 60,
                Component.translatable("gui.transmutator_automator.add"),
                0xFF2E5D2E, () -> ItemSelector.open());
        drawButton(g, panelX + PAD + 62, y, PANEL_W - PAD * 2 - 62,
                Component.translatable("gui.transmutator_automator.clear"),
                0xFF5A3A3A, () -> {
                    TARGETS.clear();
                    save();
                });
        return y + BTN_H + GAP;
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void outlineItem(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + SLOT, y + 1, color);
        g.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, color);
        g.fill(x, y + 1, x + 1, y + SLOT - 1, color);
        g.fill(x + SLOT - 1, y + 1, x + SLOT, y + SLOT - 1, color);
    }

    /** 全部已注册物品（缓存，避免每帧遍历注册表）。 */
    public static List<Item> allItems() {
        if (cachedItems == null) {
            cachedItems = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                if (ForgeRegistries.ITEMS.containsValue(item) && item != Items.AIR) {
                    cachedItems.add(item);
                }
            }
        }
        return cachedItems;
    }

    private static List<Item> cachedItems;

    /** 把一个物品加为目标（已存在则忽略，避免选择器里反复点同一个）。 */
    public static void addTarget(Item item) {
        if (item != null && item != Items.AIR && !TARGETS.contains(item)) {
            TARGETS.add(item);
            save();
        }
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

    /**
     * 画面板底板。
     * <p>
     * <b>刻意不画标题</b>：早先标题画在 y+2，而底板从 y-2 起画，
     * 结果标题正好压在上边框上（截图里可见）。删掉标题后：
     * 边框从 y 开始、内容从 {@link #PAD_Y} 开始，两者不再打架。
     * <p>
     * 面板没有标题也不影响辨识 —— 它就贴在嬗变台旁边，位置本身就是标识。
     */
    private static void drawPanel(GuiGraphics g, int x, int y, boolean compact) {
        int h = compact ? 56 : PANEL_H;
        // 半透明底板：不遮住嬗变台本体，又能看清边界
        g.fill(x, y, x + PANEL_W, y + h, 0xC0101010);
        outlineBox(g, x, y, PANEL_W, h, 0xFF4A4A4A);
    }

    /** 画一个矩形边框（不是物品格那种固定 18x18）。 */
    private static void outlineBox(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void drawButton(GuiGraphics g, int x, int y, int w, Component text,
                                   int color, Runnable onClick) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        // 垂直居中：按文字实际高度算，而不是写死偏移 ——
        // 写死偏移在按钮高度或字体变化时就会偏出去压到边框上
        int th = mc.font.lineHeight;
        g.fill(x, y, x + w, y + BTN_H, color);
        g.drawString(mc.font, text,
                x + (w - mc.font.width(text)) / 2, y + (BTN_H - th) / 2, 0xFFFFFF, false);
        if (onClick != null) {
            HOTSPOTS.add(new Hotspot(x, y, w, BTN_H, onClick));
        }
    }

    /**
     * 目标数量框：显示当前值，<b>点击即可直接输入</b>。
     * <p>
     * 早先只能点加减号，从 64 调到 128 要点 64 次 —— 明显不合理。
     * 现在点一下就变成输入框，回车或点别处生效。
     */
    private static void drawCountBox(GuiGraphics g, net.minecraft.client.Minecraft mc,
                                     int x, int y, int w, int mouseX, int mouseY) {
        int th = mc.font.lineHeight;
        boolean editing = countBox != null && countBox.isFocused();
        g.fill(x, y, x + w, y + BTN_H, editing ? 0xFFFFFFFF : 0xFF303030);
        outlineBox(g, x, y, w, BTN_H, editing ? 0xFF3B6D11 : 0xFF5A5A5A);
        String text = editing
                ? countBox.getValue()
                : String.valueOf(TransmutatorConfig.TARGET_COUNT.get());
        g.drawString(mc.font, text,
                x + (w - mc.font.width(text)) / 2, y + (BTN_H - th) / 2,
                editing ? 0x173404 : 0xDDDDDD, false);
        // 提示可以输入
        if (!editing && hover(mouseX, mouseY, x, y, w, BTN_H)) {
            g.renderTooltip(mc.font,
                    Component.translatable("gui.transmutator_automator.count_hint"),
                    mouseX, mouseY);
        }
        if (editing) {
            // 真正的输入框画在同一位置
            countBox.setX(x);
            countBox.setY(y);
            countBox.setWidth(w);
            countBox.render(g, mouseX, mouseY, 0);
        } else {
            // 未编辑时登记点击区：点了就进入编辑
            final int bx = x;
            final int by = y;
            final int bw = w;
            HOTSPOTS.add(new Hotspot(x, y, w, BTN_H, () -> beginCountEdit(bx, by, bw)));
        }
    }

    /** 数字输入框（懒创建，关闭时丢弃）。 */
    private static net.minecraft.client.gui.components.EditBox countBox;

    /**
     * 打开数字输入。
     * <p>
     * <b>不调 save()</b> —— 与其他按钮不同，这里只打开输入框；
     * 真正生效是在 {@link #onKey} 里按回车/点别处时才写。
     */
    public static void beginCountEdit(int x, int y, int w) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (countBox == null) {
            countBox = new net.minecraft.client.gui.components.EditBox(
                    mc.font, x, y, w, BTN_H,
                    Component.translatable("gui.transmutator_automator.count"));
            // 只收数字与退格，防止输入无效字符
            countBox.setFilter(str -> str.chars().allMatch(Character::isDigit));
            countBox.setMaxLength(5);
        }
        countBox.setX(x);
        countBox.setY(y);
        countBox.setWidth(w);
        countBox.setValue(String.valueOf(TransmutatorConfig.TARGET_COUNT.get()));
        countBox.setFocused(true);
        countEditing = true;
    }

    /** 数字框是否正在编辑。 */
    private static boolean countEditing;

    /**
     * 提交数字输入。空串或非法输入时保持原值。
     */
    public static void commitCountEdit() {
        if (countBox == null || !countEditing) {
            return;
        }
        countEditing = false;
        countBox.setFocused(false);
        String text = countBox.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            applyCount(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            // 输入框已过滤数字，理论上到不了这里；真到了就保持原值
        }
    }

    public static boolean isCountEditing() {
        return countEditing;
    }

    /**
     * 数字框的按键。
     *
     * @return true 表示按键已被消费
     */
    public static boolean onCountKey(int key, int scanCode, int modifiers) {
        if (countBox == null || !countEditing) {
            return false;
        }
        if (key == 257 || key == 258) { // ENTER / TAB：提交
            commitCountEdit();
            return true;
        }
        if (key == 256) { // ESC：放弃编辑
            countEditing = false;
            countBox.setFocused(false);
            return true;
        }
        countBox.keyPressed(key, scanCode, modifiers);
        return true;
    }

    /** 应用新的目标数量（夹到合法范围并落内存，磁盘由 flush 统一处理）。 */
    private static void applyCount(int raw) {
        int next = Math.max(1, Math.min(MAX_COUNT, raw));
        TransmutatorConfig.TARGET_COUNT.set(next);
        TransmutatorLoop.setTargetTotal(next);
        save();
    }

    private static void outline(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + SLOT, y + 1, color);
        g.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, color);
        g.fill(x, y + 1, x + 1, y + SLOT - 1, color);
        g.fill(x + SLOT - 1, y + 1, x + SLOT, y + SLOT - 1, color);
    }

    /**
     * 把界面上的列表写回配置。
     * <p>
     * <b>只改内存，不落盘。</b>点击时立刻 {@code SPEC.save()} 会同步写磁盘，
     * 而点击正发生在 GUI 事件处理中 —— 实测这样会让游戏卡死无响应。
     * 落盘推迟到 {@link #flush()}，由<b>关闭字变台界面</b>时统一做：
     * 那时事件处理早已结束，一次会话也最多写一次盘。
     */
    private static void save() {
        List<String> list = TransmutatorConfig.markerList();
        list.clear();
        for (Item item : TARGETS) {
            var key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null) {
                list.add(key.toString());
            }
        }
        // 只让运行时立刻生效（重新解析目标物品），不碰磁盘
        TransmutatorLoop.reloadTargets();
        dirty = true;
    }

    /** 关闭嬖变台界面时一并关掉选择器。 */
    public static void closeSelector() {
        // 数字框里没提交的值就此作废，不写盘
        countEditing = false;
        if (countBox != null) {
            countBox.setFocused(false);
        }
        if (ItemSelector.isOpen()) {
            ItemSelector.close();
            ItemSelector.beginFrame();
        }
    }

    /** 真正落盘。由界面关闭时调用。 */
    public static void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        TransmutatorConfig.SPEC.save();
    }

    /** 是否有尚未落盘的改动。 */
    private static boolean dirty;

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
