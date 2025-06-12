package com.macrohuang.fileq.concurrent;

/**
 * 并发访问策略接口
 * 定义FileQueue支持的不同并发访问模式
 * 
 * @author macro
 */
public interface ConcurrencyStrategy {
    
    /**
     * 并发访问模式枚举
     */
    enum AccessMode {
        /**
         * 单线程模式 - 无锁，最高性能，但不支持并发访问
         */
        SINGLE_THREAD,
        
        /**
         * 读写锁模式 - 支持多读单写，适合读多写少的场景
         */
        READ_WRITE_LOCK,
        
        /**
         * 重入锁模式 - 使用ReentrantLock，平衡性能和安全性
         */
        REENTRANT_LOCK,
        
        /**
         * 文件锁模式 - 支持多进程访问，但性能较低
         */
        FILE_LOCK,
        
        /**
         * 无锁模式 - 使用CAS操作，高性能但实现复杂
         */
        LOCK_FREE
    }
    
    /**
     * 获取当前并发访问模式
     */
    AccessMode getAccessMode();
    
    /**
     * 执行写操作
     * @param writeOperation 写操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    <T> T executeWrite(WriteOperation<T> writeOperation);
    
    /**
     * 执行读操作
     * @param readOperation 读操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    <T> T executeRead(ReadOperation<T> readOperation);
    
    /**
     * 获取锁竞争统计信息
     */
    LockStatistics getLockStatistics();
    
    /**
     * 关闭并发策略，释放资源
     */
    void close();
    
    /**
     * 写操作接口
     */
    @FunctionalInterface
    interface WriteOperation<T> {
        T execute() throws Exception;
    }
    
    /**
     * 读操作接口
     */
    @FunctionalInterface
    interface ReadOperation<T> {
        T execute() throws Exception;
    }
} 