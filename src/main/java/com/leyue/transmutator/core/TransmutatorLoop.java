package com.leyue.transmutator.core;

import com.github.alexthe666.alexsmobs.inventory.MenuTransmutationTable;
import com.leyue.transmutator.config.TransmutatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * 自动嬗变主循环。
 * <p>
 * <b>它做什么</b>（2026-10-05 明确的需求）：
 * 打开嬗变台后<b>一直点</b>，把槽位里的物品反复嬗变，
 * <b>直到三个候选里出现标记物品为止</b> —— 出现就停，不去点它。
 * <p>
 * <b>为什么不是"只点目标"</b>（这是设计的核心）：
 * 嬗变台的权重是累积的。按 wiki 公式，
 * <b>换出物品减少权重 = log₁₀(数量)⁴</b>，而 log 增长极快：
 * 数量 1 时减 0，10 时减 4，100 时减 8 —— 数量越大权重掉得越狠。
 * 只点目标意味着"其他物品被换出的量"不受控，权重会被持续压低，
 * 目标越来越难出现，反而刷得更慢。
 * <p>
 * <b>为什么避开"数量会变多"的选项</b>：
 * 嬗变的数量换算在服务端做，{@code 新数量 = floor(原数量 × 新堆叠上限 / 原堆叠上限)}。
 * 雪球上限 16、木棍上限 64，所以 1 个雪球可能嬗变成 4 个木棍 ——
 * 每点一次物品就变多，被换出物品的权重按 log₁₀(N)⁴ 疯涨，其他物品权重被迅速压低。
 * 所以策略是：<b>优先选堆叠上限不高于当前物品的候选</b>，数量不膨胀、权重扰动最小。
 * <p>
 * <b>线程</b>：Mixin 截获网络包时可能还在网络线程，
 * 而发嬗变包、发提示都必须在主线程，否则会有时序问题。
 */
public final class TransmutatorLoop {

    /** 每次嬗变消耗的经验。 */
    private static final int EXP_PER_TRANSMUTE = 3;

    private static Set<Item> targets = Set.of();
    /** 已完成的嬗变次数。 */
    private static int transmuted;
    /** 已处理过的候选批次版本，避免对同一批候选重复发包。 */
    private static int handledVersion = -1;
    /** 剩余冷却 tick。 */
    private static int cooldown;
    /** 停止原因；null 表示仍在运行。 */
    private static String stopReason;
    /** 是否已经因为"看到目标"而停下过，避免每帧重复记录。 */
    private static boolean reportedFound;
    /** 因经验不足而暂停（不同于停止：经验够了会自动恢复）。 */
    private static boolean paused;

    private TransmutatorLoop() {
    }

    /** 配置变化时由调用方重新解析标记物品。 */
    public static void reloadTargets() {
        targets = MarkerMatcher.parse(TransmutatorConfig.markerList());
    }

    /** 打开嬗变台时重置状态。 */
    public static void onScreenOpened() {
        transmuted = 0;
        handledVersion = -1;
        cooldown = 0;
        stopReason = null;
        reportedFound = false;
        paused = false;
        reloadTargets();
    }

    /** 关闭界面时停止。 */
    public static void onScreenClosed() {
        stopReason = I18n.get("gui.transmutator_automator.closed");
        CandidateSnapshot.clear();
    }

    /** 当前是否仍在自动嬗变。 */
    public static boolean isRunning() {
        return stopReason == null;
    }

    /** 已完成的嬗变次数。 */
    public static int transmuted() {
        return transmuted;
    }

    /** 停止原因；仍在运行时为 null。 */
    public static String stopReason() {
        return stopReason;
    }

    /** 当前是否因经验不足而暂停。 */
    public static boolean isPaused() {
        return paused;
    }

    /**
     * 每客户端 tick 调用一次。
     *
     * @return 本轮是否发出了嬗变请求
     */
    public static boolean tick() {
        if (!isRunning() || !TransmutatorConfig.ENABLED.get()) {
            return false;
        }

        // 经验检查放最前面（2026-10-05）：必须早于冷却与候选检查。
        // 放后面的话，玩家在冷却期间掉经验会"感觉不到暂停"，
        // 而冷却期间本来就不该继续消耗经验。
        // 经验不足 → 暂停，不是永久停止：经验会自然回复、也能用经验瓶补满，
        // 等够了要能自动接着刷，一次掉级就废掉整个功能不合理。
        //
        // 刻意不设 stopReason —— 那个字段表示"真的该停"（界面关闭、看到目标物品），
        // 会永久阻断循环。暂停只用 paused 标记。
        Player player = Minecraft.getInstance().player;
        if (player != null && player.experienceLevel < EXP_PER_TRANSMUTE) {
            if (!paused) {
                paused = true;
                TransmutatorLog.infoT("log.exp_paused",
                        player.experienceLevel, EXP_PER_TRANSMUTE);
                TransmutatorLog.infoT("log.exp_resume_hint");
            }
            return false;
        }
        if (paused) {
            paused = false;
            TransmutatorLog.infoT("log.exp_resumed",
                    player == null ? 0 : player.experienceLevel);
        }
        if (!CandidateSnapshot.hasCandidates()) {
            return false;
        }
        int version = CandidateSnapshot.version();
        if (version == handledVersion) {
            return false; // 这批候选已判断过，等服务端发下一批
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        // 1) 目标出现 → 停手。这才是玩家的目的，不能把目标点掉
        int marked = findMarked();
        if (marked >= 0) {
            handledVersion = version;
            if (!reportedFound) {
                reportedFound = true;
                String desc = ItemNamer.describe(CandidateSnapshot.get(marked));
                stopReason = I18n.get("gui.transmutator_automator.found", desc);
                TransmutatorLog.infoT("log.target_found",
                        marked + 1, desc, transmuted);
            }
            return false;
        }

        // 2) 没目标 → 继续嬗变，优先选不会让数量变多的
        int choice = pickSafe();
        handledVersion = version;
        if (choice < 0) {
            return false; // 三个候选都空
        }

        ItemStack picked = CandidateSnapshot.get(choice);
        TransmutatorNetwork.sendTransmute(choice);
        transmuted++;
        cooldown = TransmutatorConfig.INTERVAL_TICKS.get();
        // 每 20 次打一条，否则会刷屏
        if (TransmutatorConfig.logEveryTransmute() || transmuted % 20 == 0) {
            TransmutatorLog.infoT("log.transmuted",
                    transmuted, choice + 1, ItemNamer.describe(picked));
        }
        // 诊断（1.0.1 新增）：避让规则是本版的核心改动，必须能验证它真的在生效。
        // 每 20 次打一条当前上限与三个候选的上限，一眼就能看出有没有避开。
        if (transmuted % 20 == 0) {
            TransmutatorLog.infoT("log.avoidance",
                    currentStackLimit(),
                    CandidateSnapshot.get(0).getMaxStackSize(),
                    CandidateSnapshot.get(1).getMaxStackSize(),
                    CandidateSnapshot.get(2).getMaxStackSize(),
                    choice + 1);
        }
        return true;
    }

    /**
     * 找出第一个标记物品的候选。
     *
     * @return 下标；没有则 -1
     */
    private static int findMarked() {
        if (targets.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < 3; i++) {
            ItemStack stack = CandidateSnapshot.get(i);
            if (!stack.isEmpty() && MarkerMatcher.isMarked(stack, targets)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 在三个候选里挑一个"堆叠上限与当前物品相同"的。
     * <p>
     * <b>1.0.1 的规则：严格避开所有堆叠上限不同的候选</b>，
     * 不只是"会变多"的那些。
     * <p>
     * <b>为什么连"上限变小"的也要避</b>（这是 1.0.0 漏掉的）：
     * 0.1.0 只避开 {@code candidateMax > currentMax}，理由是"避免立即膨胀"。
     * 但上限变小同样有隐患 —— 数量当时虽然不变（被 {@code max(候选数, 1)} 兜住），
     * <b>下一次转换就会突然放大</b>：
     * <pre>
     *   泥土(64) → 雪球(16)  得到 1 个雪球（数量没变，看起来安全）
     *   雪球(16) → 木棍(64)  得到 4 个木棍 ← 突然膨胀
     * </pre>
     * 也就是说"变小的安全"只是延后了爆炸，物品在上限不同的空间里跳来跳去，
     * 每次跳都是一次权重扰动。所以只要上限对不上就跳过。
     * <p>
     * <b>三个都不满足时选"上限数值最接近"的</b>，而不是第一个：
     * 数量换算是 {@code 新数量 = 原数量 × 新上限 / 原上限}，
     * 上限越接近倍数越接近 1，扰动越小。卡着不动等于完全刷不到东西，
     * 所以必须退让，但退让也要退得最省。
     *
     * @return 候选下标；全空时 -1
     */
    private static int pickSafe() {
        int currentMax = currentStackLimit();
        int fallback = -1;
        int fallbackGap = Integer.MAX_VALUE;

        for (int i = 0; i < 3; i++) {
            ItemStack stack = CandidateSnapshot.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            int candidateMax = stack.getMaxStackSize();
            // 上限相同：完美，数量必然不变
            if (currentMax <= 0 || candidateMax == currentMax) {
                return i;
            }
            // 记下"差距最小"的作为退让目标
            int gap = Math.abs(candidateMax - currentMax);
            if (fallback < 0 || gap < fallbackGap) {
                fallback = i;
                fallbackGap = gap;
            }
        }
        return fallback;
    }

    /**
     * 当前孖变台输入槽的堆叠上限。
     * <p>
     * 槽位为空或不可堆叠时返回 0，表示"没有可比的基准"，
     * 此时 {@link #pickSafe()} 会直接接受第一个候选。
     * <p>
     * <b>为什么用 {@code ItemStack} 而不是裸 {@code Item}</b>：
     * 服务端换算用的是 {@code Item.getMaxStackSize(ItemStack)} 这个重载，
     * 它<b>不是 final</b>（无参版本才是），模组可以按栈内容动态改上限。
     * {@link ItemStack#getMaxStackSize()} 内部正是调那个可覆写的重载，
     * 所以读到的值和服务端算出来的完全一致。
     */
    private static int currentStackLimit() {
        ItemStack current = currentSlotStack();
        if (current.isEmpty() || !current.isStackable()) {
            return 0;
        }
        return current.getMaxStackSize();
    }

    /**
     * 当前嬗变台输入槽里的物品。
     * <p>
     * <b>怎么可靠找到那个槽</b>：菜单的 {@code slots} 里前几个是容器自己的槽，
     * 后面才是玩家背包。判据是 {@link Slot#container} —— 输入槽属于孖变台的容器，
     * 玩家背包槽的 container 是玩家自己的 Inventory。
     * <p>
     * <b>为什么不按下标硬编码</b>：布局由 Alex's Mobs 决定，
     * 猜"第 0 个是输入槽"在对方改布局时会静默读错，
     * 而读错会让 {@link #currentStackLimit()} 拿到别的物品的上限，
     * 避让规则就会按错误的基准生效。
     */
    private static ItemStack currentSlotStack() {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof MenuTransmutationTable menu)) {
            return ItemStack.EMPTY;
        }
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory()) {
                return slot.getItem();
            }
        }
        return ItemStack.EMPTY;
    }
}
