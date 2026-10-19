package com.spothelper.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единственная точка вывода ошибок мода в лог. Каждая ошибка пишется одной строкой без стектрейса,
 * и не чаще одного раза на место вызова (иначе ошибка в тике забила бы лог за секунды).
 */
public final class Log {
    private static final Logger LOG = LoggerFactory.getLogger("SpotHelper");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private Log() {}

    public static void error(String where, Throwable t) {
        if (REPORTED.add(where)) {
            LOG.warn("{}: {}", where, String.valueOf(t));
        }
    }
}
