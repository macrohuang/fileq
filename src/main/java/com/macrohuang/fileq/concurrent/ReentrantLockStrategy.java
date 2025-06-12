package com.macrohuang.fileq.concurrent;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.exception.FileQueueIOException;

/**
 * 重入锁并发策略实现
 * 使用单一的ReentrantLock保证线程安全，平衡性能和安全性
 * 
 * @author macro
 */
public class ReentrantLockStrategy implements ConcurrencyStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ReentrantLockStrategy.class);
    
    private final ReentrantLock writeLock;
    private final ReentrantLock readLock;
    private final LockStatistics statistics;
    private volatile boolean closed = false;
    
    public ReentrantLockStrategy() {
        this(false);
    }
    
    public ReentrantLockStrategy(boolean fair) {
        this.writeLock = new ReentrantLock(fair);
        this.readLock = new ReentrantLock(fair);
        this.statistics = new LockStatistics();
        logger.debug("ReentrantLockStrategy initialized with fair={}", fair);
    }
    
    @Override
    public AccessMode getAccessMode() {
        return AccessMode.REENTRANT_LOCK;
    }
    
    @Override
    public <T> T executeWrite(WriteOperation<T> writeOperation) {
        checkNotClosed();
        
        long startTime = System.nanoTime();
        boolean lockAcquired = false;
        
        try {
            // 尝试获取写锁，如果需要等待则记录竞争
            if (!writeLock.tryLock()) {
                statistics.recordLockContention();
                writeLock.lock();
            }
            
            lockAcquired = true;
            long lockAcquiredTime = System.nanoTime();
            
            // 记录统计信息
            statistics.recordWriteLockAcquisition();
            statistics.recordWriteLockWaitTime(lockAcquiredTime - startTime);
            
            logger.debug("Write lock acquired, wait time: {}μs, queue length: {}", 
                        (lockAcquiredTime - startTime) / 1000.0, writeLock.getQueueLength());
            
            // 执行写操作
            T result = writeOperation.execute();
            
            long operationEndTime = System.nanoTime();
            statistics.recordWriteLockHoldTime(operationEndTime - lockAcquiredTime);
            
            logger.debug("Write operation completed, hold time: {}μs", 
                        (operationEndTime - lockAcquiredTime) / 1000.0);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Write operation failed", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new FileQueueIOException("Write operation failed", new IOException(e));
        } finally {
            if (lockAcquired) {
                writeLock.unlock();
                logger.debug("Write lock released");
            }
        }
    }
    
    @Override
    public <T> T executeRead(ReadOperation<T> readOperation) {
        checkNotClosed();
        
        long startTime = System.nanoTime();
        boolean lockAcquired = false;
        
        try {
            // 尝试获取读锁，如果需要等待则记录竞争
            if (!readLock.tryLock()) {
                statistics.recordLockContention();
                readLock.lock();
            }
            
            lockAcquired = true;
            long lockAcquiredTime = System.nanoTime();
            
            // 记录统计信息
            statistics.recordReadLockAcquisition();
            statistics.recordReadLockWaitTime(lockAcquiredTime - startTime);
            
            logger.debug("Read lock acquired, wait time: {}μs, queue length: {}", 
                        (lockAcquiredTime - startTime) / 1000.0, readLock.getQueueLength());
            
            // 执行读操作
            T result = readOperation.execute();
            
            long operationEndTime = System.nanoTime();
            statistics.recordReadLockHoldTime(operationEndTime - lockAcquiredTime);
            
            logger.debug("Read operation completed, hold time: {}μs", 
                        (operationEndTime - lockAcquiredTime) / 1000.0);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Read operation failed", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new FileQueueIOException("Read operation failed", new IOException(e));
        } finally {
            if (lockAcquired) {
                readLock.unlock();
                logger.debug("Read lock released");
            }
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
        
        logger.info("Closing ReentrantLockStrategy");
        closed = true;
        
        // 输出最终统计信息
        logger.info("Final lock statistics: {}", statistics);
    }
    
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("ReentrantLockStrategy has been closed");
        }
    }
    
    /**
     * 获取写锁等待队列长度
     */
    public int getWriteLockQueueLength() {
        return writeLock.getQueueLength();
    }
    
    /**
     * 获取读锁等待队列长度
     */
    public int getReadLockQueueLength() {
        return readLock.getQueueLength();
    }
    
    /**
     * 检查写锁是否被当前线程持有
     */
    public boolean isWriteLockedByCurrentThread() {
        return writeLock.isHeldByCurrentThread();
    }
    
    /**
     * 检查读锁是否被当前线程持有
     */
    public boolean isReadLockedByCurrentThread() {
        return readLock.isHeldByCurrentThread();
    }
    
    /**
     * 获取写锁持有计数
     */
    public int getWriteLockHoldCount() {
        return writeLock.getHoldCount();
    }
    
    /**
     * 获取读锁持有计数
     */
    public int getReadLockHoldCount() {
        return readLock.getHoldCount();
    }
    
    /**
     * 检查是否有线程在等待写锁
     */
    public boolean hasWriteQueuedThreads() {
        return writeLock.hasQueuedThreads();
    }
    
    /**
     * 检查是否有线程在等待读锁
     */
    public boolean hasReadQueuedThreads() {
        return readLock.hasQueuedThreads();
    }
} 