package com.leyue.transmutator.mixin;

import com.github.alexthe666.alexsmobs.message.MessageUpdateTransmutablesToDisplay;
import com.leyue.transmutator.core.CandidateSnapshot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 截获 Alex's Mobs 发给客户端的"更新嬗变台候选"消息。
 * <p>
 * <b>为什么必须用 Mixin</b>：这个包的内容
 * （{@code stack1 / stack2 / stack3}）虽然字段是 public，
 * 但<b>没有任何公开的 API 能拿到它</b>：
 * <ul>
 *   <li>客户端的 {@code GUITransmutationTable} 只有三个按钮字段，不持有候选；</li>
 *   <li>{@code TileEntityTransmutationTable} 是服务端对象，客户端拿不到；</li>
 *   <li>包的 {@code Handler.handle} 是静态私有逻辑，外面无从订阅。</li>
 * </ul>
 * 所以只能在它被处理的那一刻把内容抄出来。
 * <p>
 * <b>注入时机</b>：{@code @At("HEAD")} 打在 {@code handle} 上，
 * 也就是"服务端刚发来、原版还没来得及更新 GUI 的时候"。
 * 必须在原逻辑<b>之前</b>抄，因为原逻辑会把这批候选清空或覆盖。
 * <p>
 * <b>线程</b>：{@code handle} 会被 Forge 调度到主线程执行
 * （它的 lambda 里用了 {@code ctx.get().enqueueWork}），
 * 所以这里读写 {@link CandidateSnapshot} 是安全的。
 */
@Mixin(value = MessageUpdateTransmutablesToDisplay.Handler.class, remap = false)
public abstract class MixinUpdateTransmutablesHandler {

    @Inject(method = "handle", at = @At("HEAD"), remap = false, cancellable = false)
    private static void ta$captureCandidates(
            MessageUpdateTransmutablesToDisplay message,
            Supplier<net.minecraftforge.network.NetworkEvent.Context> ctxSupplier,
            CallbackInfo ci) {
        // 当前打开的菜单 id —— MessageTransmuteFromMenu 只需要 choice，
        // 但保留 containerId 有助于判断"玩家是不是换了另一张嬗变台"
        int container = -1;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            // player.containerMenu 的静态类型已经是 AbstractContainerMenu，
            // 再写 instanceof 会被 javac 判为"模式类型是表达式类型的子类"而报错
            container = player.containerMenu.containerId;
        }
        int playerId = ((MixinUpdateTransmutablesAccessor) message).ta$getPlayerId();
        CandidateSnapshot.accept(
                playerId,
                message.stack1,
                message.stack2,
                message.stack3,
                container);
    }
}
