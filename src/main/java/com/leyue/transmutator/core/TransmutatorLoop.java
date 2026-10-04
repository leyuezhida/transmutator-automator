package com.leyue.transmutator.core;

import com.leyue.transmutator.config.TransmutatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * 自动嬗变主循环。
 * <p>
 * <b>它在客户端主线程上跑</b>：Mixin 截获网络包时可能还在网络线程，
 * 而发嬗变包、发提示都必须在主线程，否则会出现时序问题。
 * <p>
 * <b>每一轮做什么</b>：
 * <ol>
 *   <li>确认总开关打开、GUI 已打开、且确实有候选；</li>
 *   <li>检查经验：不足 3 级就停（否则服务端会反复拒绝，玩家被卡在界面里）；</li>
 *   <li>在三个候选里找标记物品；</li>
 *   <li>找到就发嬗变包，并累计已刷数量；</li>
 *   <li>没找到就什么都不做，等下一次候选刷新（服务端发新包后版本号自增）。</li>
 * </ol>
 * <p>
 * <b>为什么"没找到就等"是对的</b>：嬗变台每次只能刷一次才换候选，
 * 客户端无法要求"重掷一次"。所以想拿目标物品，只能等它自然出现在候选里
 * ——这也是嬗变台规则的一部分（权重由服务端维护）。
 */
public final class TransmutatorLoop {

    /** 每次嬗变消耗的经验。 */
    private static final int EXP_PER_TRANSMUTE = 3;

    private static Set<Item> targets = Set.of();
    /** 目标物品总共需要刷到的数量。 */
    private static int targetTotal;
    /** 已经刷到的目标物品数量。 */
    private static int collected;
    /** 已处理过的候选批次版本，避免对同一批候选重复发包。 */
    private static int handledVersion = -1;
    /** 剩余冷却 tick。 */
    private static int cooldown;
    /** 停止原因，用于给玩家提示；null 表示仍在运行。 */
    private static String stopReason;

    private TransmutatorLoop() {
    }

    /** 配置变化时由调用方重新解析标记物品。 */
    public static void reloadTargets() {
        targets = MarkerMatcher.parse(TransmutatorConfig.markerList());
        targetTotal = TransmutatorConfig.TARGET_COUNT.get();
        if (collected >= targetTotal) {
            collected = targetTotal;
            stopReason = "目标数量已达成";
        }
    }

    /** 打开嬗变台时重置状态。 */
    public static void onScreenOpened() {
        collected = 0;
        handledVersion = -1;
        cooldown = 0;
        stopReason = null;
        reloadTargets();
    }

    /** 关闭界面时停止。 */
    public static void onScreenClosed() {
        stopReason = "界面已关闭";
        CandidateSnapshot.clear();
    }

    /** 当前是否正在自动嬗变。 */
    public static boolean isRunning() {
        return stopReason == null;
    }

    /** 已刷到的目标物品数量。 */
    public static int collected() {
        return collected;
    }

    /** 停止原因；仍在运行时为 null。 */
    public static String stopReason() {
        return stopReason;
    }

    /**
     * 每客户端 tick 调用一次。
     *
     * @return 本轮是否发出了嬗变请求（供调用方决定要不要播放音效等）
     */
    public static boolean tick() {
        if (!isRunning() || !TransmutatorConfig.ENABLED.get()) {
            return false;
        }
        if (!CandidateSnapshot.hasCandidates()) {
            return false;
        }
        int version = CandidateSnapshot.version();
        if (version == handledVersion) {
            // 这一批候选已经判断过了，等服务端发下一批
            return false;
        }

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        // 经验不足必须停：服务端会拒绝请求，玩家被卡在 GUI 里出不来。
        // 这一段照旧强制生效，配置项只影响"是否额外提示"——关掉它并不会让
        // 经验不足时继续发包，因为那样只是徒劳地刷日志。
        Player player = Minecraft.getInstance().player;
        if (player != null && player.experienceLevel < EXP_PER_TRANSMUTE) {
            if (TransmutatorConfig.STOP_ON_LOW_EXP.get() && stopReason == null) {
                stopReason = "经验不足（需要 " + EXP_PER_TRANSMUTE + " 级）";
                TransmutatorLog.info("经验不足，自动嬗变已停止：当前 {} 级",
                        player.experienceLevel);
            }
            return false;
        }

        int choice = pickTarget();
        handledVersion = version;

        if (choice < 0) {
            // 本批没有目标物品，等下一次
            return false;
        }

        ItemStack picked = CandidateSnapshot.get(choice);
        TransmutatorNetwork.sendTransmute(choice);
        collected++;
        cooldown = TransmutatorConfig.INTERVAL_TICKS.get();

        TransmutatorLog.info("已选第 {} 个候选：{}（{}/{}）",
                choice + 1, ItemNamer.describe(picked), collected, targetTotal);
        if (collected >= targetTotal) {
            stopReason = "已刷够目标数量";
        }
        return true;
    }

    /**
     * 在三个候选里挑一个目标物品。
     *
     * @return 候选下标（0 基）；没找到时返回 -1
     */
    private static int pickTarget() {
        boolean needAll = TransmutatorConfig.REQUIRE_ALL_THREE.get();
        int found = -1;
        boolean allMarked = true;

        for (int i = 0; i < 3; i++) {
            ItemStack stack = CandidateSnapshot.get(i);
            boolean marked = !stack.isEmpty() && MarkerMatcher.isMarked(stack, targets);
            if (marked) {
                if (found < 0) {
                    found = i;
                }
            } else {
                allMarked = false;
            }
        }

        if (needAll) {
            return allMarked ? found : -1;
        }
        return found;
    }

    /** 供命令用：手动改目标数量时同步内部计数。 */
    public static void setTargetTotal(int total) {
        targetTotal = Math.max(1, total);
        if (collected >= targetTotal) {
            stopReason = "目标数量已达成";
        }
    }

    public static int targetTotal() {
        return targetTotal;
    }
}
