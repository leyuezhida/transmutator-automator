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

    /** 选择器宽度。高度由内容算出，见 {@link #computeLayout()}。 */
    /**
     * 面板宽度。
     * <p>
     * 跟着 {@link #SLOT} 一起放大：9 列 × 24 = 216，左右各留 8，共 232。
     * 宽度和格子大小是绑定的，改一个另一个必须跟着改。
     */
    private static final int W = 232;
    /**
     * 物品格边长。
     * <p>
     * 演变过程：18 → 20 → 24。
     * 18 时 16px 贴图只剩 1px 空隙；20 时留 4px，但玩家的整合包装了
     * Item Borders 一类模组，会在物品周围额外描边，视觉上又贴回去了。
     * 24 留出 8px，即使有描边也能清楚分开。
     */
    private static final int SLOT = 24;
    /** 网格列数。 */
    private static final int COLS = 9;
    /** 网格行数（每页）。物品列表固定 4 行。 */
    private static final int ROWS = 4;
    /** 每页物品数。 */
    private static final int PAGE_SIZE = COLS * ROWS;

    /** 背包区标题相对面板顶部的偏移。 */
    private static final int INV_TITLE_Y = 172;
    /** 背包网格相对面板顶部的偏移。 */
    private static final int INV_GRID_Y = 182;

    private static boolean open;
    private static int left;
    private static int top;
    private static int page;
    /** 面板高度，随可用屏幕高度变化。 */
    private static int height = 252;
    /** 当前实际显示的背包行数（1~4）。 */
    /**
     * 箱子区显示的行数。
     * <p>
     * <b>固定 1 行＝只显示快捷栏</b>（2026-10-19 玩家要求）。
     * 之前显示 4 行（连主背包一起），在矮屏幕上放不下，面板只能压得很紧，
     * 玩家看到的"物品栏重叠"其实就是被压到面板外、和嬗变台原版槽位混在一起的东西。
     * <p>
     * 只留快捷栏之后：高度稳定，不会被屏幕高度逼着变形；
     * 而主背包里的东西仍然可以搜到 —— 上面那 13434 个物品是全注册表的，
     * 不缺主背包那几个。
     */
    private static final int INV_ROWS = 1;
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
        // 先算高度，再据此居中 —— 顺序不能反，否则 top 用的是上一屏的旧高度
        computeLayout(mc.screen.height);
        left = Math.max(4, (mc.screen.width - W) / 2);
        top = Math.max(4, (mc.screen.height - height) / 2);
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
            // 失焦时不接受字符，避免误输入到别的界面
            search.setCanLoseFocus(true);
        }
        search.setValue("");
        // 打开即聚焦：少一次点击，也让"打开→直接打字"这条路径成立。
        // 之前默认不聚焦，玩家点开选择器后打字没反应，得再点一次搜索框才有效。
        search.setFocused(true);

        // 布局诊断：布局是纯坐标计算，一旦算错就会"物品画到面板外面"。
        // 有这行日志，下次再出现重叠能立刻判断是高度、偏移还是屏幕尺寸的问题。
        com.leyue.transmutator.core.TransmutatorLog.info(
                "选择器布局：屏幕={}x{} 面板={}x{} 位置=({},{}) 快捷栏行数={}",
                mc.screen.width, mc.screen.height, W, height, left, top, INV_ROWS);
    }

    /**
     * 按屏幕高度算面板高度与背包可显示行数。
     * <p>
     * <b>为什么必须算</b>（2026-10-19 玩家反馈"背包物品还是有重叠"）：
     * 上一版把高度写死成 176，而内容实际排到 top+230 —— 背包网格有 54px
     * 画到了面板外面，正好叠在嬗变台的槽位上，看起来就是"物品重叠"。
     * <p>
     * 固定部分到背包网格起点为止是 {@link #INV_GRID_Y}，之后每行占 {@link #SLOT}，
     * 再加 6px 底边。屏幕不够高时减少背包行数（最少 1 行），而不是让内容溢出。
     */
    private static void computeLayout(int screenHeight) {
        // 只显示快捷栏一行，高度是固定的，与屏幕高度无关 ——
        // 212px 在任何常见 GUI 高度（240 起）里都放得下，
        // 不再需要"算几行、再钳制"那套逻辑。上一版的溢出就出在钳制上。
        height = INV_GRID_Y + INV_ROWS * SLOT + 6;
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

    /**
     * 字符输入。
     * <p>
     * <b>这是"搜索框打不进字"的修复</b>（2026-10-19 用户反馈）：
     * Minecraft 把输入拆成两条路径 —— {@code keyPressed} 只给键码，
     * 真正带文字的 {@code charTyped} 才是产生字符的地方。
     * 上一版只接管了前者，所以光标在闪、退格有效，却一个字也打不进去。
     * <p>
     * <b>中文为什么也能走这里</b>：输入法组字完成后，最终仍是以
     * {@code char}（UTF-16 码元）的形式送进 {@code charTyped}，
     * 与英文没有区别，因此不需要为中文单独做处理。
     * <p>
     * 过滤掉控制字符：{@code char} 参数在部分输入法或异常状态下
     * 会带进 0~31 与 127，直接塞给 EditBox 会弄乱内容。
     */
    public static void onChar(char ch, int modifiers) {
        if (!open || search == null || !search.isFocused()) {
            return;
        }
        if (ch < 32 || ch == 127) {
            return;
        }
        // EditBox 内部走 setValue，会触发 responder，过滤是自动完成的
        search.charTyped(ch, modifiers);
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

        // ① 全屏暗色遮罩：把下面的嬗变台、背包、JEI 压暗，
        //    明确告诉玩家"现在只有这个窗口能用"。
        //    这既是视觉上的模态提示，也解决了"半透明地显示下方 UI"的观感问题。
        // ① 全屏**不透明**遮罩。
        //    之前是 0xC8（约 78% 黑），只是把下层压暗 ——
        //    玩家反馈的"物品栏重叠"其实是嬗变台原版的背包槽位透过遮罩显形，
        //    和选择器里的快捷栏挨在一起，两套格子风格不同，看起来像叠在一起。
        //    既然这个界面是模态的（点击全归它），背景就该彻底不可见，
        //    否则"看得见但点不动"又会重现。
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        g.fill(0, 0, sw, sh, 0xFF0E0E12);

        // ② 底板用完全不透明（alpha=FF）。
        //    之前是 0xF0（240/255），理论上几乎不透，但叠在亮色 GUI 上
        //    仍能看出底下的槽位轮廓，视觉上像是"半透明浮窗"，容易误以为点得动底下。
        g.fill(left, top, left + W, top + height, 0xFF101014);
        box(g, left, top, W, height, 0xFF505050);
        g.drawString(mc.font,
                Component.translatable("gui.transmutator_automator.selector_title"),
                left + 8, top + 8, 0xFFFFFF, true);

        // 右上角关闭按钮。
        // 之前只能按 Esc 才能退出，界面上没有任何提示 ——
        // 玩家在不熟悉的界面里第一反应是找关闭按钮，找不到会以为"卡住了"。
        // 用字母 X 而不是 ×：Minecraft 默认字体对 U+00D7 的覆盖不可靠，
        // 部分整合包的字体替换后会显示成方块。
        drawButton(g, left + W - 22, top + 5, 16, Component.literal("X"),
                0xFF5A2A2A, ItemSelector::close);

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

        // 快捷栏区（只一行）
        //
        // ⚠️ INV_TITLE_Y / INV_GRID_Y 是**相对面板顶部**的偏移，必须加 top。
        // 上一版直接当绝对坐标用了，快捷栏整体上移了 top 那么多像素，
        // 于是标题压进翻页行、格子叠在物品网格下沿 —— 又一次"重叠"。
        g.drawString(mc.font,
                Component.translatable("gui.transmutator_automator.selector_hotbar"),
                left + 8, top + INV_TITLE_Y, 0x808080, false);
        // 外框：布局错位时能一眼看出物品有没有跑到框外，不用再靠截图猜。
        int invGridH = INV_ROWS * SLOT;
        box(g, left + 6, top + INV_GRID_Y - 2, W - 12, invGridH + 4, 0xFF3A3A3A);
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            // 只画快捷栏（slot 0~8）。主背包不显示 ——
            // 面板高度因此固定，不会再被屏幕高度逼着压缩；
            // 主背包里的物品仍可通过上面的搜索找到。
            for (int slot = 0; slot < COLS; slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                int sx = left + 8 + slot * SLOT;
                int sy = top + INV_GRID_Y;
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
        // 物品贴图 16×16，格子 20×20，左右各留 2px 才不会看起来糊在一起
        g.renderItem(new ItemStack(item), x + 2, y + 2);
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
            return mx >= left && mx < left + W && my >= top && my < top + height;
        }
    }
}
