package com.leyue.transmutator.mixin;

import com.github.alexthe666.alexsmobs.message.MessageUpdateTransmutablesToDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@code MessageUpdateTransmutablesToDisplay} 的 private 字段 {@code playerId}。
 * <p>
 * <b>为什么需要它</b>：三个 {@code stackN} 是 public，直接读即可；
 * 但 {@code playerId} 是 private。没有它也不是不行（可以不用），
 * 留着是为了日志与将来做"只处理自己的候选"这类判断。
 */
@Mixin(value = MessageUpdateTransmutablesToDisplay.class, remap = false)
public interface MixinUpdateTransmutablesAccessor {

    @Accessor("playerId")
    int ta$getPlayerId();
}
