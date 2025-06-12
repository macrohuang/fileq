package com.macrohuang.fileq.concurrent;

import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.exception.FileQueueIOException;

/**
 * 读写锁并发策略实现
 * 适合读多写少的场景，允许多个读操作并发执行
 * 
 * @author macro
 */
public class ReadWriteLockStrategy implements ConcurrencyStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ReadWriteLockStrategy.class);
    
    private final ReadWriteLock readWriteLock;
    private final LockStatistics statistics;
    private volatile boolean closed = false;
    
    public ReadWriteLockStrategy() {
        this(false);
    }
    
    public ReadWriteLockStrategy(boolean fair) {
        this.readWriteLock = new ReentrantReadWriteLock(fair);
        this.statistics = new LockStatistics();
        logger.debug("ReadWriteLockStrategy initialized with fair={}", fair);
    }
    
    @Override
    public AccessMode getAccessMode() {
        return AccessMode.READ_WRITE_LOCK;
    }
    
    @Override
    public <T> T executeWrite(WriteOperation<T> writeOperation) {
        checkNotClosed();
        
        long startTime = System.nanoTime();
        boolean lockAcquired = false;
        
        try {
            // 尝试获取写锁，如果需要等待则记录竞争
            if (!readWriteLock.writeLock().tryLock()) {
                statistics.recordLockContention();
                readWriteLock.writeLock().lock();
            }
            
            lockAcquired = true;
            long lockAcquiredTime = System.nanoTime();
            
            // 记录统计信息
            statistics.recordWriteLockAcquisition();
            statistics.recordWriteLockWaitTime(lockAcquiredTime - startTime);
            
            logger.debug("Write lock acquired, wait time: {}μs", 
                        (lockAcquiredTime - startTime) / 1000.0);
            
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
                readWriteLock.writeLock().unlock();
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
            if (!readWriteLock.readLock().tryLock()) {
                statistics.recordLockContention();
                readWriteLock.readLock().lock();
            }
            
            lockAcquired = true;
            long lockAcquiredTime = System.nanoTime();
            
            // 记录统计信息
            statistics.recordReadLockAcquisition();
            statistics.recordReadLockWaitTime(lockAcquiredTime - startTime);
            
            logger.debug("Read lock acquired, wait time: {}μs", 
                        (lockAcquiredTime - startTime) / 1000.0);
            
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
                readWriteLock.readLock().unlock();
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
        
        logger.info("Closing ReadWriteLockStrategy");
        closed = true;
        
        // 输出最终统计信息
        logger.info("Final lock statistics: {}", statistics);
    }
    
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("ReadWriteLockStrategy has been closed");
        }
    }
    
    /**
     * 获取当前等待写锁的线程数
     */
    public int getQueuedWriters() {
        if (readWriteLock instanceof ReentrantReadWriteLock) {
            return ((ReentrantReadWriteLock) readWriteLock).getQueueLength();
        }
        return -1; // 不支持
    }
    
    /**
     * 获取当前等待读锁的线程数
     */
    public int getQueuedReaders() {
        if (readWriteLock instanceof ReentrantReadWriteLock) {
            return ((ReentrantReadWriteLock) readWriteLock).getQueueLength();
        }
        return -1; // 不支持
    }
    
    /**
     * 获取当前读锁持有数量
     */
    public int getReadLockCount() {
        if (readWriteLock instanceof ReentrantReadWriteLock) {
            return ((ReentrantReadWriteLock) readWriteLock).getReadLockCount();
        }
        return -1; // 不支持
    }
    
    /**
     * 检查写锁是否被持有
     */
    public boolean isWriteLocked() {
        if (readWriteLock instanceof ReentrantReadWriteLock) {
            return ((ReentrantReadWriteLock) readWriteLock).isWriteLocked();
        }
        return false;
    }
    
    /**
     * 检查当前线程是否持有写锁
     */
    public boolean isWriteLockedByCurrentThread() {
        if (readWriteLock instanceof ReentrantReadWriteLock) {
            return ((ReentrantReadWriteLock) readWriteLock).isWriteLockedByCurrentThread();
        }
        return false;
    }
} 