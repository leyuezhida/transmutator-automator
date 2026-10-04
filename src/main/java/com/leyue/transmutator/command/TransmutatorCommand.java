package com.leyue.transmutator.command;

import com.leyue.transmutator.config.TransmutatorConfig;
import com.leyue.transmutator.core.MarkerMatcher;
import com.leyue.transmutator.core.TransmutatorLoop;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * {@code /ta} 命令。
 * <p>
 * 目的是让玩家<b>不必手改配置文件</b>就能设定标记物品 —— 手改 toml 容易写错名字
 * （中文名、注册名、命名空间都可能漏掉），而"解析不了就静默失效"是最糟的体验。
 * <p>
 * <b>为什么参数用字符串而不是 ItemArgument</b>：{@code ItemArgument} 在 1.20.1
 * 位于 {@code net.minecraft.commands.arguments.item} 包，且只接受已注册物品的解析式；
 * 我们希望玩家能直接输 {@code 钻石} 或 {@code diamond}，
 * 自己的 {@link MarkerMatcher} 能同时处理这两种，也能在解析失败时给出明确提示。
 */
public final class TransmutatorCommand {

    private TransmutatorCommand() {
    }

    /** 由主类注册到 MinecraftForge.EVENT_BUS（不是 modBus）。 */
    public static void onRegisterCommands(
            net.minecraftforge.event.RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ta")
                .executes(ctx -> usage(ctx.getSource()))
                .then(Commands.literal("mark")
                        .executes(ctx -> usage(ctx.getSource()))
                        .then(Commands.argument("item", StringArgumentType.string())
                                .executes(ctx -> add(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("unmark")
                        .then(Commands.argument("item", StringArgumentType.string())
                                .executes(ctx -> remove(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("count")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                .executes(ctx -> setCount(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "value"))))));
    }

    private static int usage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("commands.ta.usage"), false);
        return 1;
    }

    /** 把玩家输入解析成物品；解析不出来时直接回报，不静默吞掉。 */
    private static Item resolveOrWarn(CommandSourceStack source, String input) {
        Item item = MarkerMatcher.resolve(input);
        if (item == null) {
            source.sendFailure(Component.translatable("commands.ta.unresolved", input));
        }
        return item;
    }

    private static int add(CommandSourceStack source, String input) {
        Item item = resolveOrWarn(source, input);
        if (item == null) {
            return 0;
        }
        List<String> list = TransmutatorConfig.markerList();
        String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString();
        if (list.contains(id)) {
            source.sendFailure(Component.translatable("commands.ta.already", id));
            return 0;
        }
        list.add(id);
        TransmutatorConfig.SPEC.save();
        TransmutatorLoop.reloadTargets();
        source.sendSuccess(() -> Component.translatable("commands.ta.added", id), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String input) {
        Item item = resolveOrWarn(source, input);
        if (item == null) {
            return 0;
        }
        List<String> list = TransmutatorConfig.markerList();
        String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString();
        if (list.remove(id)) {
            TransmutatorConfig.SPEC.save();
            TransmutatorLoop.reloadTargets();
            source.sendSuccess(() -> Component.translatable("commands.ta.removed", id), true);
        } else {
            source.sendFailure(Component.translatable("commands.ta.not_marked", id));
        }
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<String> list = TransmutatorConfig.markerList();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ta.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.ta.list"), false);
        for (String id : list) {
            Item item = MarkerMatcher.resolve(id);
            String name = item == null
                    ? "§c无法识别§r"
                    : new ItemStack(item).getHoverName().getString();
            source.sendSuccess(() -> Component.literal(" • " + id + "  " + name), false);
        }
        return list.size();
    }

    private static int clear(CommandSourceStack source) {
        TransmutatorConfig.markerList().clear();
        TransmutatorConfig.SPEC.save();
        TransmutatorLoop.reloadTargets();
        source.sendSuccess(() -> Component.translatable("commands.ta.cleared"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        boolean on = TransmutatorConfig.ENABLED.get();
        source.sendSuccess(() -> Component.translatable("commands.ta.status.line1",
                on ? "§a开" : "§c关",
                TransmutatorLoop.collected(),
                TransmutatorLoop.targetTotal()), false);
        String reason = TransmutatorLoop.stopReason();
        if (reason != null) {
            source.sendSuccess(() -> Component.literal("停止原因：" + reason)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setCount(CommandSourceStack source, int value) {
        TransmutatorConfig.TARGET_COUNT.set(value);
        TransmutatorConfig.SPEC.save();
        TransmutatorLoop.setTargetTotal(value);
        source.sendSuccess(() -> Component.translatable("commands.ta.count_set", value), true);
        return 1;
    }
}
