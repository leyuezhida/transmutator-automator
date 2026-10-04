package com.leyue.transmutator.core;

import com.github.alexthe666.alexsmobs.AlexsMobs;
import com.github.alexthe666.alexsmobs.message.MessageTransmuteFromMenu;
import net.minecraft.client.Minecraft;

/**
 * 嬗变请求的发送。
 * <p>
 * <b>为什么不自己建通道</b>：Alex's Mobs 已经把消息注册在
 * {@code AlexsMobs.NETWORK_WRAPPER}（{@code public static final SimpleChannel}）上，
 * 消息类 {@code MessageTransmuteFromMenu} 的字段是
 * {@code (playerId, choice)} 两个 int。自己新建通道的话服务端收不到，
 * 因为 Forge 的 SimpleChannel 是<b>按通道名握手</b>的，
 * 客户端发的包服务端没有对应通道就会直接丢弃（不报错，只是什么都没发生）——
 * 这种"静默失效"最难查，所以复用它的通道是最稳的做法。
 * <p>
 * <b>choice 是候选下标</b>（0/1/2），对应界面上的第一、二、三个按钮。
 */
public final class TransmutatorNetwork {

    private TransmutatorNetwork() {
    }

    /**
     * 请求嬗变。
     *
     * @param choice 候选下标，取值 0 / 1 / 2
     */
    public static void sendTransmute(int choice) {
        if (choice < 0 || choice > 2) {
            // 越界会在 Alex's Mobs 的按钮回调里直接 NPE，这里提前挡住
            TransmutatorLog.warn("候选下标越界：{}，已忽略", choice);
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int playerId = player.getId();
        AlexsMobs.NETWORK_WRAPPER.sendToServer(new MessageTransmuteFromMenu(playerId, choice));
    }
}
