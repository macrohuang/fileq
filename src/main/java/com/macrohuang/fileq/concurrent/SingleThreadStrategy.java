package com.macrohuang.fileq.concurrent;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.exception.FileQueueIOException;

/**
 * 单线程并发策略实现
 * 无锁实现，提供最高性能，但不支持并发访问
 * 适用于单线程或明确知道不会有并发访问的场景
 * 
 * @author macro
 */
public class SingleThreadStrategy implements ConcurrencyStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(SingleThreadStrategy.class);
    
    private final LockStatistics statistics;
    private volatile boolean closed = false;
    
    public SingleThreadStrategy() {
        this.statistics = new LockStatistics();
        logger.debug("SingleThreadStrategy initialized - no locking overhead");
    }
    
    @Override
    public AccessMode getAccessMode() {
        return AccessMode.SINGLE_THREAD;
    }
    
    @Override
    public <T> T executeWrite(WriteOperation<T> writeOperation) {
        checkNotClosed();
        
        long startTime = System.nanoTime();
        
        try {
            // 记录统计信息（无等待时间）
            statistics.recordWriteLockAcquisition();
            statistics.recordWriteLockWaitTime(0);
            
            logger.debug("Executing write operation without locking");
            
            // 直接执行写操作，无锁开销
            T result = writeOperation.execute();
            
            long operationEndTime = System.nanoTime();
            statistics.recordWriteLockHoldTime(operationEndTime - startTime);
            
            logger.debug("Write operation completed in {}μs", 
                        (operationEndTime - startTime) / 1000.0);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Write operation failed", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new FileQueueIOException("Write operation failed", new IOException(e));
        }
    }
    
    @Override
    public <T> T executeRead(ReadOperation<T> readOperation) {
        checkNotClosed();
        
        long startTime = System.nanoTime();
        
        try {
            // 记录统计信息（无等待时间）
            statistics.recordReadLockAcquisition();
            statistics.recordReadLockWaitTime(0);
            
            logger.debug("Executing read operation without locking");
            
            // 直接执行读操作，无锁开销
            T result = readOperation.execute();
            
            long operationEndTime = System.nanoTime();
            statistics.recordReadLockHoldTime(operationEndTime - startTime);
            
            logger.debug("Read operation completed in {}μs", 
                        (operationEndTime - startTime) / 1000.0);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Read operation failed", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new FileQueueIOException("Read operation failed", new IOException(e));
        }
    }
    
    @Override
    public LockStatistics getLockStatistics() {
        return statistics;
    }
    
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        logger.info("Closing SingleThreadStrategy");
        closed = true;
        
        // 输出最终统计信息
        logger.info("Final operation statistics: {}", statistics);
    }
    
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("SingleThreadStrategy has been closed");
        }
    }
    
    /**
     * 获取性能统计摘要
     * 由于无锁实现，主要关注操作执行时间
     */
    public String getPerformanceSummary() {
        return String.format(
            "SingleThreadStrategy Performance: " +
            "totalOperations=%d, " +
            "avgWriteTime=%.2fμs, " +
            "avgReadTime=%.2fμs, " +
            "noLockingOverhead=true",
            statistics.getReadLockAcquisitions() + statistics.getWriteLockAcquisitions(),
            statistics.getAverageWriteLockHoldTime() / 1000.0,
            statistics.getAverageReadLockHoldTime() / 1000.0
        );
    }
} 