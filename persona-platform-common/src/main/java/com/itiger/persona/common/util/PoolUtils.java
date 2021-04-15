package com.itiger.persona.common.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *
 * </p>
 *
 * @author Shik
 * @project clionelimacina
 * @package com.itiger.ssp.util
 * @since 2019-12-18
 */
public class PoolUtils {

    public static ExecutorService pool = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() / 2,
            Runtime.getRuntime().availableProcessors() * 2,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200));

}
