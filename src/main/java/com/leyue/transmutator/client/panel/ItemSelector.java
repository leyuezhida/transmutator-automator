package com.leyue.transmutator.client.panel;

import com.leyue.transmutator.core.MarkerMatcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 物品选择器：叠在孖变台界面之上的小窗，解决"目标物品不在背包里"的问题。
 * <p>
 * <b>为什么是叠层而不是独立界面</b>：选目标这一个动作不该让人离开孖变台 ——
 * 独立界面会遮住当前候选，而"这次会点哪个"恰恰是选目标时要对照的信息。
 * 这与之前"删掉独立 GUI"是同一条判断。
 * <p>
 * <b>三个区域，从上到下</b>：
 * <ol>
 *   <li><b>搜索框</b> —— 支持中文名与注册名片段，输"钻石"或"diamond"都能过滤；</li>
 *   <li><b>全部物品网格</b> —— 来自 Forge 注册表，模组矿石/任何注册物品都能选，
 *       翻页浏览；</li>
 *   <li><b>玩家背包</b> —— 直接看到自己有的东西，不用先关窗口去翻背包。</li>
 * </ol>
 * <p>
 * <b>不写盘</b>：这里只做读操作 + 改内存里的目标列表（{@link TransmutatorPanel#addTarget}），
 * 落盘仍由关闭孖变台界面时统一做 —— 点击事件里同步写文件会导致卡死，那已经踩过一次。
 */
public final class ItemSelector {

    /** 选择器尺寸。 */
    private static final int W = 200;
    private static final int H = 176;
    /** 物品格边长。 */
    private static final int SLOT = 18;
    /** 网格列数。 */
    private static final int COLS = 9;
    /** 网格行数（每页）。 */
    private static final int ROWS = 4;
    /** 每页物品数。 */
    private static final int PAGE_SIZE = COLS * ROWS;

    private static boolean open;
    private static int left;
    private static int top;
    private static int page;
    private static EditBox search;

    /** 过滤后的物品列表（缓存搜索结果，翻页不重复过滤）。 */
    private static List<Item> filtered = new ArrayList<>();

    private ItemSelector() {
    }

    /** 打开选择器。 */
    public static void open() {
        if (open) {
            return;
        }
        open = true;
        page = 0;
        var mc = net.minecraft.client.Minecraft.getInstance();
        // 物品很多时网格会很高，限制在屏幕内
        left = (mc.screen.width - W) / 2;
        top = Math.max(4, (mc.screen.height - H) / 2);
        applyFilter("");
        if (search == null) {
            search = new EditBox(mc.font, left + 8, top + 26, W - 16, 16,
                    Component.translatable("gui.transmutator_automator.search"));
            search.setMaxLength(64);
            search.setHint(Component.translatable("gui.transmutator_automator.search_hint"));
            // 边打字边过滤
            search.setResponder(text -> {
                page = 0;
                applyFilter(text);
            });
        }
        search.setValue("");
        search.setFocused(false);
    }

    /** 关闭选择器。 */
    public static void close() {
        open = false;
    }

    public static boolean isOpen() {
        return open;
    }

    /**
     * 按搜索词过滤全部物品。
     * <p>
     * <b>同时匹配中文名与注册名</b>：玩家会照着 wiki 输中文，也会习惯性输英文，
     * 两种都支持才不用去查注册名。
     */
    private static void applyFilter(String query) {
        List<Item> all = TransmutatorPanel.allItems();
        if (query == null || query.isBlank()) {
            filtered = all;
            return;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Item> result = new ArrayList<>();
        for (Item item : all) {
            if (matches(item, q)) {
                result.add(item);
            }
        }
        filtered = result;
    }

    private static boolean matches(Item item, String q) {
        var key = ForgeRegistries.ITEMS.getKey(item);
        if (key != null && key.getPath().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        // 中文名匹配
        String display = new ItemStack(item).getHoverName().getString();
        return display.toLowerCase(Locale.ROOT).contains(q);
    }

    // ==================== 交互 ====================

    /**
     * 处理点击。
     * <p>
     * <b>先点搜索框</b>：点它才获得焦点，否则玩家没法打字。
     *
     * @return true 表示点击已被选择器消费（不该再传给孖变台）
     */
    public static boolean onClick(double mx, double my, int button) {
        if (!open) {
            return false;
        }
        // 1) 搜索框：点它获得焦点
        if (search != null
                && mx >= search.getX() && mx < search.getX() + search.getWidth()
                && my >= search.getY() && my < search.getY() + search.getHeight()) {
            search.setFocused(true);
            return true;
        }
        // 2) 其他区域：清掉搜索框焦点，避免打字时字符跑到别处
        if (search != null && search.isFocused()) {
            search.setFocused(false);
        }
        // 3) 本帧登记的控件（物品格、翻页按钮）
        Hit[] found = new Hit[1];
        if (ItemSelectorHit.hit(mx, my, found)) {
            if (found[0] != null) {
                found[0].action().run();
            }
            return true;
        }
        return true; // 底板内的一切点击都被消费
    }

    /**
     * 处理按键。
     * <p>
     * Esc 关闭选择器；其余按键在搜索框有焦点时交给它处理。
     *
     * @return true 表示按键已被消费
     */
    public static boolean onKey(int key, int scanCode, int modifiers) {
        if (!open) {
            return false;
        }
        if (key == 256) { // ESC
            close();
            return true;
        }
        if (key == 257) { // ENTER：收尾，把过滤结果留在当前页
            return true;
        }
        if (key == 258) { // TAB：切换搜索框焦点
            search.setFocused(!search.isFocused());
            return true;
        }
        if (search != null && search.isFocused()) {
            // 让 EditBox 自己处理字符、退格等
            boolean handled = search.keyPressed(key, scanCode, modifiers);
            page = 0;
            return true;
        }
        // 翻页快捷键：没在输入时用 PageUp/PageDown 或左右方向键
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (key == 262 || key == 32) { // RIGHT / SPACE
            if (page < pages - 1) {
                page++;
            }
            return true;
        }
        if (key == 263) { // LEFT
            if (page > 0) {
                page--;
            }
            return true;
        }
        return false;
    }

    /** 每帧渲染前清空命中区。 */
    public static void beginFrame() {
        ItemSelectorHit.clear();
    }

    // ==================== 渲染 ====================

    public static void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();

        // 底板
        g.fill(left, top, left + W, top + H, 0xF0101010);
        box(g, left, top, W, H, 0xFF505050);
        g.drawString(mc.font,
                Component.translatable("gui.transmutator_automator.selector_title"),
                left + 8, top + 8, 0xFFFFFF, true);

        // 搜索框
        search.setX(left + 8);
        search.setY(top + 24);
        search.render(g, mouseX, mouseY, 0);

        int gridTop = top + 46;

        // 物品网格
        Set<Item> targets = TransmutatorPanel.currentTargets();
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= filtered.size()) {
                break;
            }
            Item item = filtered.get(index);
            int sx = left + 8 + (i % COLS) * SLOT;
            int sy = gridTop + (i / COLS) * SLOT;
            drawItem(g, sx, sy, item, targets.contains(item), mouseX, mouseY, () -> {
                TransmutatorPanel.addTarget(item);
            });
        }

        // 翻页 + 计数
        int py = gridTop + ROWS * SLOT + 4;
        drawButton(g, left + 8, py, 40, Component.literal("◀"),
                page > 0 ? 0xFF4A4A4A : 0xFF303030, page > 0 ? () -> page-- : null);
        g.drawString(mc.font,
                Component.literal((page + 1) + " / " + pages + "   共 " + filtered.size()),
                left + 52, py + 6, 0xA0A0A0, false);
        drawButton(g, left + W - 48, py, 40, Component.literal("▶"),
                page < pages - 1 ? 0xFF4A4A4A : 0xFF303030, page < pages - 1 ? () -> page++ : null);

        // 玩家背包区
        int invTop = py + 26;
        g.drawString(mc.font,
                Component.translatable("gui.transmutator_automator.selector_inventory"),
                left + 8, invTop, 0x808080, false);
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                int sx = left + 8 + (slot % COLS) * SLOT;
                int sy = invTop + 10 + (slot / COLS) * SLOT;
                Item it = stack.getItem();
                drawItem(g, sx, sy, it, targets.contains(it), mouseX, mouseY, () -> {
                    TransmutatorPanel.addTarget(it);
                });
            }
        }
    }

    private static void drawItem(GuiGraphics g, int x, int y, Item item, boolean already,
                                 int mouseX, int mouseY, Runnable onClick) {
        g.fill(x, y, x + SLOT, y + SLOT, 0xFF212121);
        box(g, x, y, SLOT, SLOT, already ? 0xFF3A5A3A : 0xFF4A4A4A);
        g.renderItem(new ItemStack(item), x + 1, y + 1);
        if (hover(mouseX, mouseY, x, y, SLOT, SLOT)) {
            box(g, x, y, SLOT, SLOT, 0xFFFFFFFF);
            g.renderTooltip(net.minecraft.client.Minecraft.getInstance().font,
                    new ItemStack(item), mouseX, mouseY);
            ItemSelectorHit.add(new Hit(x, y, SLOT, SLOT, onClick));
        }
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void drawButton(GuiGraphics g, int x, int y, int w, Component text,
                                   int color, Runnable onClick) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        g.fill(x, y, x + w, y + 18, color);
        g.drawString(mc.font, text, x + (w - mc.font.width(text)) / 2, y + 5, 0xFFFFFF, false);
        if (onClick != null) {
            ItemSelectorHit.add(new Hit(x, y, w, 18, onClick));
        }
    }

    public record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** 本帧的可点击区域（由 render 登记，mouseClicked 消费）。 */
    public static final class ItemSelectorHit {
        private static final List<Hit> HITS = new ArrayList<>();

        public static void add(Hit hit) {
            HITS.add(hit);
        }

        public static void clear() {
            HITS.clear();
        }

        /**
         * 命中检测。
         * <p>
         * <b>返回值语义</b>：点到选择器内部的空白也算消费掉 ——
         * 否则那次点击会继续传到嬗变台的原版处理，表现为"点了选择器却把
         * 嬗变台里的东西也一起点了"。
         *
         * @param out 若命中，记录对应动作
         * @return true 表示点击应被选择器消费
         */
        public static boolean hit(double mx, double my, Hit[] out) {
            for (int i = HITS.size() - 1; i >= 0; i--) {
                Hit h = HITS.get(i);
                if (h.contains(mx, my)) {
                    out[0] = h;
                    return true;
                }
            }
            // 点在选择器底板内但没在任何控件上：仍然消费
            return mx >= left && mx < left + W && my >= top && my < top + H;
        }
    }
}
