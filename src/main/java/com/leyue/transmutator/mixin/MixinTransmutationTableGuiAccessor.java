package com.leyue.transmutator.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取嬗变台 GUI 的边界坐标。
 * <p>
 * <b>为什么 mixin 的是父类而不是 {@code GUITransmutationTable}</b>：
 * {@code leftPos} / {@code topPos} 定义在 {@code AbstractContainerScreen} 上，
 * {@code @Accessor} 只在<b>被 mixin 的类自身</b>里找字段，
 * 指到子类会报 "Could not locate @Accessor target leftPos"。
 * <p>
 * <b>为什么需要它</b>：这两个字段是 protected，而本模组的类在不同的包里，
 * 跨包访问 protected 成员不被允许。有了它们才能把面板准确贴到嬗变台 GUI 旁边
 * （而不是猜一个绝对坐标），这样在任意分辨率与 GUI 缩放下都能对齐。
 */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface MixinTransmutationTableGuiAccessor {

    @Accessor("leftPos")
    int ta$getLeftPos();

    @Accessor("topPos")
    int ta$getTopPos();
}
