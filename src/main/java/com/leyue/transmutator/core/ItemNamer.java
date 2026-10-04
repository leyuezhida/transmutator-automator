package com.leyue.transmutator.core;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 按物品的中文（游戏内）名反查物品。
 * <p>
 * <b>为什么需要</b>：MC百科上写的是"钻石""潜影壳"这类中文名，
 * 玩家照着抄进配置是最自然的事。如果只支持注册名，玩家得自己查英文 id。
 * <p>
 * <b>为什么缓存</b>：{@code I18n.get} 依赖语言加载，配置解析时反复调用不划算；
 * 而且物品名在一次游戏会话里不会变，缓存后查找是 O(1)。
 */
public final class ItemNamer {

    /** 物品注册名（小写） → 物品。 */
    private static final Map<String, Item> BY_ID = new HashMap<>();

    /** 物品中文名（小写） → 物品。 */
    private static final Map<String, Item> BY_DISPLAY = new HashMap<>();

    private static boolean built;

    private ItemNamer() {
    }

    /**
     * 按物品的显示名查找。
     * <p>
     * 同时索引中文名与英文名，两种输入都能命中。
     *
     * @param name 用户输入的名字（不区分大小写）
     * @return 物品；找不到时返回 {@code null}
     */
    public static Item byDisplayName(String name) {
        if (name == null) {
            return null;
        }
        ensureBuilt();
        return BY_DISPLAY.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** 语言或资源重载后清缓存（/reload 与语言切换都会走到）。 */
    public static void invalidate() {
        built = false;
        BY_ID.clear();
        BY_DISPLAY.clear();
    }

    private static void ensureBuilt() {
        if (built) {
            return;
        }
        for (Item item : ForgeRegistries.ITEMS) {
            if (!ForgeRegistries.ITEMS.containsValue(item)) {
                continue;
            }
            ResourceLocationKey key = ResourceLocationKey.of(item);
            if (key != null) {
                // 物品栈带数量时 getDisplayName() 会带上 "(x1)"，这里取不带数量的形式
                String display = new ItemStack(item).getHoverName().getString();
                BY_DISPLAY.putIfAbsent(display.toLowerCase(Locale.ROOT), item);
            }
        }
        built = true;
    }

    /**
     * 取物品的注册 id。取不到说明是 Forge 没注册路径的物品（基本不会发生），
     * 返回 null 表示"这个物品跳过"。
     */
    private record ResourceLocationKey(String id) {
        static ResourceLocationKey of(Item item) {
            var key = ForgeRegistries.ITEMS.getKey(item);
            return key == null ? null : new ResourceLocationKey(key.toString());
        }
    }

    /**
     * 供日志与提示使用：把物品渲染成人类可读的名字。
     */
    public static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "(空)";
        }
        return new ItemStack(stack.getItem()).getHoverName().getString();
    }

    /**
     * 供命令回显使用：把物品渲染成注册名，方便玩家直接抄进配置。
     */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }
}
