package com.macrohuang.fileq.concurrent;

import java.util.concurrent.atomic.AtomicLong;
import com.macrohuang.fileq.conf.TimeConstants;

/**
 * 锁竞争统计信息
 * 用于监控和分析并发性能
 * 
 * @author macro
 */
public class LockStatistics {
    
    private final AtomicLong readLockAcquisitions = new AtomicLong(0);
    private final AtomicLong writeLockAcquisitions = new AtomicLong(0);
    private final AtomicLong readLockWaitTime = new AtomicLong(0);
    private final AtomicLong writeLockWaitTime = new AtomicLong(0);
    private final AtomicLong readLockHoldTime = new AtomicLong(0);
    private final AtomicLong writeLockHoldTime = new AtomicLong(0);
    private final AtomicLong lockContentions = new AtomicLong(0);
    
    /**
     * 记录读锁获取
     */
    public void recordReadLockAcquisition() {
        readLockAcquisitions.incrementAndGet();
    }
    
    /**
     * 记录写锁获取
     */
    public void recordWriteLockAcquisition() {
        writeLockAcquisitions.incrementAndGet();
    }
    
    /**
     * 记录读锁等待时间
     */
    public void recordReadLockWaitTime(long waitTimeNanos) {
        readLockWaitTime.addAndGet(waitTimeNanos);
    }
    
    /**
     * 记录写锁等待时间
     */
    public void recordWriteLockWaitTime(long waitTimeNanos) {
        writeLockWaitTime.addAndGet(waitTimeNanos);
    }
    
    /**
     * 记录读锁持有时间
     */
    public void recordReadLockHoldTime(long holdTimeNanos) {
        readLockHoldTime.addAndGet(holdTimeNanos);
    }
    
    /**
     * 记录写锁持有时间
     */
    public void recordWriteLockHoldTime(long holdTimeNanos) {
        writeLockHoldTime.addAndGet(holdTimeNanos);
    }
    
    /**
     * 记录锁竞争
     */
    public void recordLockContention() {
        lockContentions.incrementAndGet();
    }
    
    // Getters
    public long getReadLockAcquisitions() {
        return readLockAcquisitions.get();
    }
    
    public long getWriteLockAcquisitions() {
        return writeLockAcquisitions.get();
    }
    
    public long getReadLockWaitTime() {
        return readLockWaitTime.get();
    }
    
    public long getWriteLockWaitTime() {
        return writeLockWaitTime.get();
    }
    
    public long getReadLockHoldTime() {
        return readLockHoldTime.get();
    }
    
    public long getWriteLockHoldTime() {
        return writeLockHoldTime.get();
    }
    
    public long getLockContentions() {
        return lockContentions.get();
    }
    
    /**
     * 获取平均读锁等待时间（纳秒）
     */
    public double getAverageReadLockWaitTime() {
        long acquisitions = readLockAcquisitions.get();
        return acquisitions > 0 ? (double) readLockWaitTime.get() / acquisitions : 0.0;
    }
    
    /**
     * 获取平均写锁等待时间（纳秒）
     */
    public double getAverageWriteLockWaitTime() {
        long acquisitions = writeLockAcquisitions.get();
        return acquisitions > 0 ? (double) writeLockWaitTime.get() / acquisitions : 0.0;
    }
    
    /**
     * 获取平均读锁持有时间（纳秒）
     */
    public double getAverageReadLockHoldTime() {
        long acquisitions = readLockAcquisitions.get();
        return acquisitions > 0 ? (double) readLockHoldTime.get() / acquisitions : 0.0;
    }
    
    /**
     * 获取平均写锁持有时间（纳秒）
     */
    public double getAverageWriteLockHoldTime() {
        long acquisitions = writeLockAcquisitions.get();
        return acquisitions > 0 ? (double) writeLockHoldTime.get() / acquisitions : 0.0;
    }
    
    /**
     * 获取锁竞争率
     */
    public double getLockContentionRate() {
        long totalAcquisitions = readLockAcquisitions.get() + writeLockAcquisitions.get();
        return totalAcquisitions > 0 ? (double) lockContentions.get() / totalAcquisitions : 0.0;
    }
    
    /**
     * 重置所有统计信息
     */
    public void reset() {
        readLockAcquisitions.set(0);
        writeLockAcquisitions.set(0);
        readLockWaitTime.set(0);
        writeLockWaitTime.set(0);
        readLockHoldTime.set(0);
        writeLockHoldTime.set(0);
        lockContentions.set(0);
    }
    
    @Override
    public String toString() {
        return String.format(
            "LockStatistics{" +
            "readLockAcquisitions=%d, " +
            "writeLockAcquisitions=%d, " +
            "avgReadWaitTime=%.2fμs, " +
            "avgWriteWaitTime=%.2fμs, " +
            "avgReadHoldTime=%.2fμs, " +
            "avgWriteHoldTime=%.2fμs, " +
            "contentionRate=%.2f%%}",
            readLockAcquisitions.get(),
            writeLockAcquisitions.get(),
            getAverageReadLockWaitTime() / TimeConstants.NANOSECONDS_PER_MICROSECOND,
            getAverageWriteLockWaitTime() / TimeConstants.NANOSECONDS_PER_MICROSECOND,
            getAverageReadLockHoldTime() / TimeConstants.NANOSECONDS_PER_MICROSECOND,
            getAverageWriteLockHoldTime() / TimeConstants.NANOSECONDS_PER_MICROSECOND,
            getLockContentionRate() * 100.0
        );
    }
} 