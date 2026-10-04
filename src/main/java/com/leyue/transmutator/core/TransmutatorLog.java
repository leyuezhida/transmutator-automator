package com.leyue.transmutator.core;

import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.language.I18n;
import org.slf4j.Logger;

/**
 * 统一日志出口。
 * <p>
 * <b>为什么不直接用 LogUtils.getLogger()</b>：那要求在类的静态初始化里
 * 固定 LOG 字段名。包一层便于以后统一加前缀或做级别过滤。
 */
public final class TransmutatorLog {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[TransmutatorAutomator] ";

    private TransmutatorLog() {
    }

    public static void info(String format, Object... args) {
        LOGGER.info(PREFIX + format, args);
    }

    public static void warn(String format, Object... args) {
        LOGGER.warn(PREFIX + format, args);
    }

    public static void error(String format, Object... args) {
        LOGGER.error(PREFIX + format, args);
    }

    /**
     * 记一条<b>本地化</b>的日志。
     * <p>
     * 为什么日志也要走翻译：英文环境下打开 latest.log 会看到一整片中文，
     * 排查问题时反而看不清哪行是哪个意思。消息以 "log." 前缀的翻译键给出，
     * 语言文件里各有一份。
     *
     * @param key  翻译键，如 {@code log.exp_paused}
     * @param args 占位符参数
     */
    public static void infoT(String key, Object... args) {
        LOGGER.info(PREFIX + I18n.get(key, args));
    }

    public static void warnT(String key, Object... args) {
        LOGGER.warn(PREFIX + I18n.get(key, args));
    }
}
