package com.leyue.transmutator;

import com.leyue.transmutator.command.TransmutatorCommand;
import com.leyue.transmutator.config.TransmutatorConfig;
import com.leyue.transmutator.core.TransmutatorLog;
import com.leyue.transmutator.core.TransmutatorLoop;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 嬗变台自动助手 —— 入口。
 * <p>
 * <b>这是个纯客户端模组</b>：嬗变台的规则、随机结果与经验扣除全部在服务端，
 * 本模组只做一件事——代替玩家发出合法的嬗变请求（等同按了界面上的按钮），
 * 因此不会凭空产出物品，也不会绕过任何服务端校验。
 */
@Mod(TransmutatorAutomator.MOD_ID)
public final class TransmutatorAutomator {

    public static final String MOD_ID = "transmutator_automator";

    @SuppressWarnings("removal") // ModLoadingContext.get() 在新版标记删除，1.20.1 上仍是标准注册方式
    public TransmutatorAutomator() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 配置必须在客户端侧注册
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TransmutatorConfig.SPEC);
        modBus.addListener(this::onClientSetup);

        // ⚠️ RegisterCommandsEvent 走**普通事件总线**（MinecraftForge.EVENT_BUS），
        // 不是 modBus。它不是 IModBusEvent 的子类，注册到 modBus 上会抛：
        //   "Listener for event class RegisterCommandsEvent takes an argument that is
        //    not a subtype of the base type interface IModBusEvent"
        MinecraftForge.EVENT_BUS.addListener(TransmutatorCommand::onRegisterCommands);

        MinecraftForge.EVENT_BUS.register(this);
        TransmutatorLog.info("嬗变台自动助手已加载（纯客户端）");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TransmutatorLoop.reloadTargets();
            TransmutatorLog.info("已载入标记物品：{}",
                    TransmutatorConfig.markerList());
        });
    }

    private void onRegisterCommands(
            net.minecraftforge.event.RegisterCommandsEvent event) {
        TransmutatorCommand.register(event.getDispatcher());
    }

    /**
     * 每客户端 tick 跑一次自动循环。
     * <p>
     * <b>为什么要判 Dist.CLIENT</b>：本模组标了纯客户端，
     * 但 Mixin 后的类加载在服务端也可能被拉起，加这层判断更保险。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        TransmutatorLoop.tick();
    }

    /** 断线重连时清状态，避免用着上一局的候选物品。 */
    @SubscribeEvent
    public void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TransmutatorLoop.onScreenClosed();
    }
}
