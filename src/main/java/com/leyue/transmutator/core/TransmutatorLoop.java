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
     * 在三个候选里挑一个"点了不会让数量变多"的。
     * <p>
     * 实测（2026-10-05）：雪球上限 16 → 木棍上限 64，嬗变后 1 个变 4 个。
     * 数量膨胀会让被换出物品的权重按 log₁₀(N)⁴ 疯涨，把其他物品压得太低，
     * 目标反而更难刷出来。所以优先挑堆叠上限不高于当前物品的。
     * <p>
     * 三个都不满足时退而求其次点第一个 —— 宁可数量涨一点，
     * 也别让循环卡着不动（卡着不动等于完全刷不到东西）。
     *
     * @return 候选下标；全空时 -1
     */
    private static int pickSafe() {
        int fallback = -1;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = CandidateSnapshot.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (fallback < 0) {
                fallback = i;
            }
            if (!wouldGrowCount(stack)) {
                return i;
            }
        }
        return fallback;
    }

    /**
     * 嬗变成这个候选后，槽位里的数量会不会变多。
     * <p>
     * 服务端换算：{@code 新数量 = floor(原数量 / (原上限 / 新上限))}，
     * 等价于 {@code 原数量 × 新上限 / 原上限} —— 新上限更高时结果更大。
     * <p>
     * <b>为什么比堆叠上限而不是比物品大小</b>：决定数量的是"这一组还能装多少个"，
     * 模组物品的堆叠上限常被改写（有的模组给工具设成 1），
     * 直接读 {@link ItemStack#getMaxStackSize()} 才能和服务端算出同一个结果。
     */
    private static boolean wouldGrowCount(ItemStack candidate) {
        ItemStack current = currentSlotStack();
        if (current.isEmpty() || !current.isStackable() || !candidate.isStackable()) {
            return false;
        }
        int currentMax = current.getMaxStackSize();
        int candidateMax = candidate.getMaxStackSize();
        if (currentMax <= 0 || candidateMax <= 0) {
            return false;
        }
        return candidateMax > currentMax;
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
     * 而读错会让 {@link #wouldGrowCount} 判断失效，又回到权重被压的老问题。
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
