package com.macrohuang.fileq.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 并发策略工厂
 * 用于创建不同类型的并发访问策略
 * 
 * @author macro
 */
public class ConcurrencyStrategyFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyStrategyFactory.class);
    
    /**
     * 创建并发策略
     * 
     * @param mode 并发访问模式
     * @param fair 是否使用公平锁
     * @return 并发策略实例
     */
    public static ConcurrencyStrategy createStrategy(ConcurrencyStrategy.AccessMode mode, boolean fair) {
        logger.debug("Creating concurrency strategy: mode={}, fair={}", mode, fair);
        
        switch (mode) {
            case READ_WRITE_LOCK:
                return new ReadWriteLockStrategy(fair);
                
            case REENTRANT_LOCK:
                return new ReentrantLockStrategy(fair);
                
            case SINGLE_THREAD:
                return new SingleThreadStrategy();
                
            case FILE_LOCK:
                // TODO: 实现文件锁策略
                throw new UnsupportedOperationException("FILE_LOCK strategy not implemented yet");
                
            case LOCK_FREE:
                // TODO: 实现无锁策略
                throw new UnsupportedOperationException("LOCK_FREE strategy not implemented yet");
                
            default:
                logger.warn("Unknown concurrency mode: {}, falling back to REENTRANT_LOCK", mode);
                return new ReentrantLockStrategy(fair);
        }
    }
    
    /**
     * 创建并发策略（使用默认的非公平锁）
     * 
     * @param mode 并发访问模式
     * @return 并发策略实例
     */
    public static ConcurrencyStrategy createStrategy(ConcurrencyStrategy.AccessMode mode) {
        return createStrategy(mode, false);
    }
    
    /**
     * 根据使用场景推荐最佳并发策略
     * 
     * @param readThreads 预期的读线程数
     * @param writeThreads 预期的写线程数
     * @param multiProcess 是否需要支持多进程访问
     * @return 推荐的并发访问模式
     */
    public static ConcurrencyStrategy.AccessMode recommendStrategy(int readThreads, int writeThreads, boolean multiProcess) {
        logger.debug("Recommending strategy for readThreads={}, writeThreads={}, multiProcess={}", 
                    readThreads, writeThreads, multiProcess);
        
        if (multiProcess) {
            logger.debug("Multi-process access required, recommending FILE_LOCK");
            return ConcurrencyStrategy.AccessMode.FILE_LOCK;
        }
        
        if (readThreads == 1 && writeThreads == 1) {
            logger.debug("Single thread access, recommending SINGLE_THREAD");
            return ConcurrencyStrategy.AccessMode.SINGLE_THREAD;
        }
        
        // 读多写少的场景，推荐读写锁
        if (readThreads > writeThreads * 3) {
            logger.info("Read-heavy workload detected: readThreads={} > writeThreads*3={}, recommending READ_WRITE_LOCK", 
                       readThreads, writeThreads * 3);
            return ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK;
        }
        
        // 默认推荐重入锁
        logger.debug("Balanced workload, recommending REENTRANT_LOCK");
        return ConcurrencyStrategy.AccessMode.REENTRANT_LOCK;
    }
    
    /**
     * 创建推荐的并发策略
     * 
     * @param readThreads 预期的读线程数
     * @param writeThreads 预期的写线程数
     * @param multiProcess 是否需要支持多进程访问
     * @param fair 是否使用公平锁
     * @return 推荐的并发策略实例
     */
    public static ConcurrencyStrategy createRecommendedStrategy(int readThreads, int writeThreads, 
                                                               boolean multiProcess, boolean fair) {
        ConcurrencyStrategy.AccessMode mode = recommendStrategy(readThreads, writeThreads, multiProcess);
        return createStrategy(mode, fair);
    }
    
    /**
     * 创建推荐的并发策略（使用默认的非公平锁）
     * 
     * @param readThreads 预期的读线程数
     * @param writeThreads 预期的写线程数
     * @param multiProcess 是否需要支持多进程访问
     * @return 推荐的并发策略实例
     */
    public static ConcurrencyStrategy createRecommendedStrategy(int readThreads, int writeThreads, boolean multiProcess) {
        return createRecommendedStrategy(readThreads, writeThreads, multiProcess, false);
    }
} 