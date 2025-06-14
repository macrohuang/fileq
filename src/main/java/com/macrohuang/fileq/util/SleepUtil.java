package com.macrohuang.fileq.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.macrohuang.fileq.conf.TimeConstants;

/**
 * 高效的等待工具类
 * 提供比Thread.sleep更高效的等待机制
 * 
 * @author macro
 */
public class SleepUtil {
    
    /**
     * 高效的短时间等待（适用于重试间隔）
     * 使用LockSupport.parkNanos提供更精确的等待时间
     * 
     * @throws InterruptedException 如果线程被中断
     */
    public static void retryWait() throws InterruptedException {
        parkInterruptibly(TimeConstants.RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 高效的队列等待（适用于轮询等待）
     * 
     * @throws InterruptedException 如果线程被中断
     */
    public static void queueWait() throws InterruptedException {
        parkInterruptibly(TimeConstants.QUEUE_WAIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 可中断的精确等待
     * 
     * @param time 等待时间
     * @param unit 时间单位
     * @throws InterruptedException 如果线程被中断
     */
    public static void parkInterruptibly(long time, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        
        long nanos = unit.toNanos(time);
        LockSupport.parkNanos(nanos);
        
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }
    
    /**
     * 自适应等待 - 根据等待次数调整等待时间
     * 初始等待时间较短，逐渐增加到最大值
     * 
     * @param retryCount 重试次数
     * @throws InterruptedException 如果线程被中断
     */
    public static void adaptiveWait(int retryCount) throws InterruptedException {
        if (retryCount <= 0) {
            return;
        }
        
        // 使用指数退避算法，但限制最大等待时间
        long baseWaitMs = TimeConstants.RETRY_INTERVAL_MS;
        long waitTimeMs = Math.min(baseWaitMs * (1L << Math.min(retryCount - 1, 6)), 
                                   TimeConstants.QUEUE_WAIT_INTERVAL_MS);
        
        parkInterruptibly(waitTimeMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 兼容Thread.sleep的等待方法，但提供更好的性能
     * 
     * @param millis 等待的毫秒数
     * @throws InterruptedException 如果线程被中断
     */
    public static void sleep(long millis) throws InterruptedException {
        if (millis <= 0) {
            return;
        }
        
        parkInterruptibly(millis, TimeUnit.MILLISECONDS);
    }
    
    private SleepUtil() {
        // 防止实例化
    }
}