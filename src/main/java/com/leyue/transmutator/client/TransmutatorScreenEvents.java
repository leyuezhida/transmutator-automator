package com.leyue.transmutator.client;

import com.github.alexthe666.alexsmobs.inventory.MenuTransmutationTable;
import com.leyue.transmutator.client.panel.TransmutatorPanel;
import com.leyue.transmutator.core.CandidateSnapshot;
import com.leyue.transmutator.core.TransmutatorLoop;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 监听嬗变台界面的开与关。
 * <p>
 * <b>打开时</b>：重置计数与"已处理批次"。这一步很关键——
 * 上一批的 {@link CandidateSnapshot} 若不清，玩家重新打开嬗变台后
 * 模组会对着<b>旧的候选</b>立刻发一次嬗变请求，点到的东西跟眼前看到的对不上。
 * <p>
 * <b>关闭时</b>：清快照。候选物品只在那张嬗变台上有效，
 * 留着只会在下次打开时造成误判。
 * <p>
 * <b>为什么用 getMenu() 而不是 getContainer</b>：要判断的是"打开的是嬗变台这个界面"，
 * 而 {@code getMenu()} 返回的是 {@link AbstractContainerScreen} 才有的方法；
 * 父类 {@code Screen} 没有它，所以先做类型判断再取。
 */
@Mod.EventBusSubscriber(modid = "transmutator_automator", value = Dist.CLIENT)
public final class TransmutatorScreenEvents {

    private TransmutatorScreenEvents() {
    }

    /** 界面是否是嬗变台。 */
    private static boolean isTransmutationTable(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getMenu() instanceof MenuTransmutationTable;
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (isTransmutationTable(event.getNewScreen())) {
            TransmutatorLoop.onScreenOpened();
            // 面板的目标列表与循环共享同一份数据，打开时同步一次
            TransmutatorPanel.syncTargets();
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        // Closing 继承 ScreenEvent，方法名是 getScreen()（不是 getOldScreen）
        if (isTransmutationTable(event.getScreen())) {
            TransmutatorLoop.onScreenClosed();
            CandidateSnapshot.clear();
            // 关闭界面时才落盘：点击事件里同步写文件会导致卡死
            TransmutatorPanel.flush();
        }
    }
}
