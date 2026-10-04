package com.leyue.transmutator.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置。
 * <p>
 * <b>注意 INSTANCE 的用法</b>：Forge 要求有一个"无参构造 + configure(Builder)"的类，
 * 但所有配置项都是 {@code static} 字段——因为 {@code configure} 在实例化之前就会被调用，
 * 此时 static 字段必须<b>已经</b>由静态块准备好，不能依赖构造函数。
 * 所以这里 INSTANCE 只是 Forge 的形式要求，实际读写都走静态字段。
 * <p>
 * 几处默认值来自嬗变台自身的权重规律（见 MC百科 item/635753）：
 * <ul>
 *   <li><b>增重用 2 个</b>：放入时权重 = 默认权重 + log₁₀(数量)³。wiki 推荐 3 个最佳，
 *       但 2 个材料省一半、权重增益已可观，所以做成可配置且默认 2；</li>
 *   <li><b>刷新用 1 个</b>：只想刷新候选列表、不污染权重池时，放 1 个不产生任何权重变化。</li>
 * </ul>
 */
public final class TransmutatorConfig {

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        // ==================== 基本行为 ====================
        b.comment("嬗变台自动化的基本行为").push("general");

        ENABLED = b
                .comment("总开关。关闭后模组完全不介入，嬗变台行为与原版一致。",
                        "提示：即使关闭，/ta 命令仍然可用。")
                .define("enabled", false);

        INTERVAL_TICKS = b
                .comment("两次嬗变之间的间隔（tick）。1 tick = 0.05 秒。",
                        "每次嬗变消耗 3 级经验，间隔太短会瞬间耗尽经验。",
                        "若觉得不流畅或出现物品来不及结算，可调大此值。")
                .defineInRange("intervalTicks", 4, 1, 20);

        STOP_ON_LOW_EXP = b
                .comment("经验不足以支付下一次嬗变时，是否自动停止。",
                        "强烈建议保持开启：嬗变台每转一次消耗 3 级经验，",
                        "经验不足时服务端会拒绝请求，玩家会被卡在 GUI 里。")
                .define("stopOnLowExp", true);

        b.pop();

        // ==================== 停止条件 ====================
        b.comment("停止条件").push("target");

        TARGET_COUNT = b
                .comment("刷到多少个目标物品后停止。")
                .defineInRange("targetCount", 64, 1, 2304);

        REQUIRE_ALL_THREE = b
                .comment("true  = 三个候选里出现任一目标物品就点它（推荐）",
                        "false = 只有三个候选全为目标物品时才点（更保守）",
                        "前者适合「只要目标出现就立刻拿下」，后者适合「不想浪费其他物品」")
                .define("requireAllThree", false);

        MARKER_ITEMS = b
                .comment("目标物品的注册名，例如 minecraft:diamond。",
                        "留空则视为未设置目标，模组不会自动停手。",
                        "推荐用命令 /ta mark <物品> 添加，不必手改文件。",
                        "也接受中文名（如 钻石），会按游戏内译名查找。")
                .define("markerItems", new ArrayList<String>());

        b.pop();

        // ==================== 摆放建议 ====================
        b.comment("嬗变台内的物品摆放建议（模组不自动执行，仅用于日志提示）").push("advice");

        WEIGHT_GAIN_COUNT = b
                .comment("想提升某个物品权重时，一次放入几个。",
                        "wiki 的结论是 3 个最佳，但 2 个性价比更高（材料省一半，权重增益已可观）。")
                .defineInRange("weightGainCount", 2, 1, 64);

        REROLL_COUNT = b
                .comment("只刷新候选列表、不想动权重时放几个。放 1 个不会产生任何权重变化。")
                .defineInRange("rerollCount", 1, 1, 64);

        b.pop();

        SPEC = b.build();
    }

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue STOP_ON_LOW_EXP;
    public static final ForgeConfigSpec.IntValue TARGET_COUNT;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_ALL_THREE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MARKER_ITEMS;
    public static final ForgeConfigSpec.IntValue WEIGHT_GAIN_COUNT;
    public static final ForgeConfigSpec.IntValue REROLL_COUNT;

    private TransmutatorConfig() {
    }

    /** 供命令使用的可写列表。 */
    @SuppressWarnings("unchecked") // Forge 的 ListValue 在 1.20.1 返回 List<? extends T>，元素确实是 String
    public static List<String> markerList() {
        return (List<String>) MARKER_ITEMS.get();
    }
}
