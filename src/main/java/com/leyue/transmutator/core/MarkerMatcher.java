package com.leyue.transmutator.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 判定某个候选物品是不是"标记物品"。
 * <p>
 * <b>为什么支持多种写法</b>：玩家手上可能有同种物品的不同形态
 * （比如潜影壳与潜影壳块），也可能只有中文名。因此按注册名匹配为主，
 * 同时容忍玩家写中文名——找不到对应物品时会给出提示而不是静默失效。
 * <p>
 * <b>注意</b>：只比对物品本身，<b>不看 NBT</b>。孖变台产出的是全新物品，
 * 附魔与耐久没有意义；带自定义 NBT 的"同一物品"对刷取来说也视为同一种。
 */
public final class MarkerMatcher {

    private MarkerMatcher() {
    }

    /**
     * 从配置里解析出可用的目标物品集合。
     * <p>
     * 解析不了的条目会被跳过并计数，交给调用方提示玩家 ——
     * 静默失效会让人以为"模组坏了"，实际是自己写错了名字。
     *
     * @param raw 配置里的原始字符串列表
     * @return 成功解析出的物品集合（保持配置里的书写顺序）
     */
    public static Set<Item> parse(Iterable<String> raw) {
        Set<Item> result = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Item item = resolve(trimmed);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 把一个字符串解析成物品。
     * <p>
     * 接受：{@code minecraft:diamond}、{@code diamond}（默认补 minecraft:）、
     * {@code 钻石}（按物品的中文翻译名查找）。
     *
     * @return 物品；解析不出来时返回 {@code null}
     */
    @SuppressWarnings("deprecation") // BuiltInRegistries 在新版迁移到 Registries，1.20.1 上仍可用
    public static Item resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT);

        // 1) 按注册名，必要时补 minecraft: 命名空间
        ResourceLocation id = ResourceLocation.tryParse(cleaned);
        if (id == null) {
            id = ResourceLocation.tryParse("minecraft:" + cleaned);
        }
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.get(id);
        }

        // 2) 兜底：按中文翻译名找，见 ItemNamer
        return ItemNamer.byDisplayName(cleaned);
    }

    /**
     * 判断物品栈是否命中目标。
     * <p>
     * <b>空栈不算命中</b>：孖变台偶尔会给出空槽，那是"尚未生成"，
     * 不是"出了一个不想要的物品"。把它当命中会导致对着空气点。
     */
    public static boolean isMarked(ItemStack stack, Set<Item> targets) {
        if (targets.isEmpty() || stack == null || stack.isEmpty()) {
            return false;
        }
        return targets.contains(stack.getItem());
    }
}
