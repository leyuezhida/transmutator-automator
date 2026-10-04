package com.leyue.transmutator.core;

import com.mojang.logging.LogUtils;
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
}
