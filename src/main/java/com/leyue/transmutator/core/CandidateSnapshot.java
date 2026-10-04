package com.leyue.transmutator.core;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 持有当前孖变台GUI 上的三个候选物品。
 * <p>
 * <b>数据从哪来</b>：Alex's Mobs 的服务端在每次刷新候选后，会给客户端发
 * {@code MessageUpdateTransmutablesToDisplay}，而它的三个字段
 * {@code stack1 / stack2 / stack3} 都是 <b>public</b>。
 * 用 Mixin 在该消息构造完成时把内容抄到这里，
 * 这样主逻辑就能像读本地数据一样判断"候选里有没有目标物品"。
 * <p>
 * <b>为什么不用 TileEntity</b>：{@code TileEntityTransmutationTable} 是服务端侧对象，
 * 客户端拿不到；客户端侧的 {@code GUITransmutationTable} 只有三个按钮、
 * <b>不持有候选物品</b>。所以网络包是唯一可靠的入口。
 * <p>
 * <b>线程</b>：Mixin 在网络包处理线程上调用，这里不做重活，
 * 只存快照；真正判断在客户端主线程（见 {@link TransmutatorLoop}）。
 */
public final class CandidateSnapshot {

    private static final int SLOTS = 3;

    /** 三个候选的物品快照。空数组表示"尚未收到任何候选"。 */
    private static final ItemStack[] CANDIDATES = new ItemStack[SLOTS];
    /** 候选所属的容器 id，用于识别"换了一张台子"。 */
    private static int containerId = -1;
    /** 版本号：每次收到新候选自增，GUI 那边可据此判断"这一批已处理过"。 */
    private static int version;

    private CandidateSnapshot() {
    }

    /**
     * 由 Mixin 调用：抄下服务端发来的三个候选。
     *
     * @param id        玩家 id（该包用它标识"谁的候选"）
     * @param first     候选 1
     * @param second    候选 2
     * @param third     候选 3
     * @param container 当前打开的孖变台菜单 id
     */
    public static void accept(int id, ItemStack first, ItemStack second, ItemStack third,
                              int container) {
        CANDIDATES[0] = first;
        CANDIDATES[1] = second;
        CANDIDATES[2] = third;
        containerId = container;
        version++;
    }

    /** 关闭 GUI 时清空，避免下次打开读到上一次的残留。 */
    public static void clear() {
        for (int i = 0; i < SLOTS; i++) {
            CANDIDATES[i] = null;
        }
        containerId = -1;
        version++;
    }

    /** 当前是否有一批可用候选。 */
    public static boolean hasCandidates() {
        return CANDIDATES[0] != null;
    }

    /** 三个候选的副本（顺序固定为界面上的 1、2、3）。 */
    public static List<ItemStack> candidates() {
        List<ItemStack> result = new ArrayList<>(SLOTS);
        for (ItemStack stack : CANDIDATES) {
            if (stack != null && !stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * 第 {@code index} 个候选（0 基）。
     *
     * @return 物品栈；该位置没有候选时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack get(int index) {
        if (index < 0 || index >= SLOTS || CANDIDATES[index] == null) {
            return ItemStack.EMPTY;
        }
        return CANDIDATES[index];
    }

    /** 当前批次版本号。 */
    public static int version() {
        return version;
    }

    /** 当前菜单 id；未打开孖变台时为 -1。 */
    public static int containerId() {
        return containerId;
    }
}
