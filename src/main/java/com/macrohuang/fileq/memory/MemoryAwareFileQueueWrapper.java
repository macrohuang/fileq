package com.macrohuang.fileq.memory;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.conf.MemoryConstants;

/**
 * 内存感知的FileQueue包装器
 * 通过包装器模式为现有FileQueue添加内存管理功能，避免继承访问限制
 * 
 * @author macro
 * @param <E> 队列元素类型
 */
public class MemoryAwareFileQueueWrapper<E> implements FileQueue<E> {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryAwareFileQueueWrapper.class);
    
    private final FileQueue<E> delegate;
    private final MappedBufferManager bufferManager;
    private final MemoryMonitor memoryMonitor;
    private final boolean enableMemoryMonitoring;
    private final int memoryCheckInterval;
    
    private int operationCount = 0;
    
    public MemoryAwareFileQueueWrapper(FileQueue<E> delegate, Config config) {
        this(delegate, config, true, MemoryConstants.DEFAULT_MEMORY_CHECK_INTERVAL);
    }
    
    public MemoryAwareFileQueueWrapper(FileQueue<E> delegate, Config config, 
                                     boolean enableMemoryMonitoring, int memoryCheckInterval) {
        this.delegate = delegate;
        this.bufferManager = MappedBufferManager.getInstance();
        this.memoryMonitor = new MemoryMonitor(bufferManager);
        this.enableMemoryMonitoring = enableMemoryMonitoring;
        this.memoryCheckInterval = memoryCheckInterval;
        
        setupMemoryMonitoring(config);
        
        logger.info("MemoryAwareFileQueueWrapper initialized: monitoring={}, interval={}", 
                   enableMemoryMonitoring, memoryCheckInterval);
    }
    
    private void setupMemoryMonitoring(Config config) {
        // 根据配置设置内存阈值
        long fileSizeMB = config.getFileSize() / MemoryConstants.BYTES_PER_MB;
        long memoryThreshold = Math.max(fileSizeMB * 2, MemoryConstants.MIN_MEMORY_THRESHOLD_MB);
        
        memoryMonitor.setMappedMemoryThresholdMB(memoryThreshold);
        memoryMonitor.setHeapUsageThreshold(0.8); // 80%堆内存阈值
        memoryMonitor.setMonitorIntervalSeconds(60); // 1分钟检查间隔
        
        if (enableMemoryMonitoring) {
            memoryMonitor.startMonitoring();
        }
    }
    
    private void checkMemoryIfNeeded() {
        if (enableMemoryMonitoring && ++operationCount % memoryCheckInterval == 0) {
            if (bufferManager.isMemoryThresholdExceeded(memoryMonitor.getMappedMemoryThresholdMB())) {
                logger.warn("Memory threshold exceeded after {} operations, forcing cleanup", operationCount);
                memoryMonitor.forceMemoryCleanup();
            }
        }
    }
    
    @Override
    public void add(E e) {
        checkMemoryIfNeeded();
        delegate.add(e);
    }
    
    @Override
    public E take() throws InterruptedException {
        checkMemoryIfNeeded();
        return delegate.take();
    }
    
    @Override
    public E take(long timeout, TimeUnit unit) throws InterruptedException {
        checkMemoryIfNeeded();
        return delegate.take(timeout, unit);
    }
    
    @Override
    public E peek() {
        checkMemoryIfNeeded();
        return delegate.peek();
    }
    
    @Override
    public E peek(long timeout, TimeUnit unit) throws InterruptedException {
        checkMemoryIfNeeded();
        return delegate.peek(timeout, unit);
    }
    
    @Override
    public int size() {
        return delegate.size();
    }
    
    @Override
    public boolean remain() {
        return delegate.remain();
    }
    
    @Override
    public void clear() {
        delegate.clear();
        // 清空后强制内存清理
        if (enableMemoryMonitoring) {
            memoryMonitor.forceMemoryCleanup();
        }
    }
    
    @Override
    public boolean delete() {
        boolean result = delegate.delete();
        // 删除后强制内存清理
        if (enableMemoryMonitoring) {
            memoryMonitor.forceMemoryCleanup();
        }
        return result;
    }
    
    @Override
    public E remove() {
        checkMemoryIfNeeded();
        return delegate.remove();
    }
    
    @Override
    public void close() {
        logger.info("Closing MemoryAwareFileQueueWrapper");
        
        try {
            // 停止内存监控
            if (enableMemoryMonitoring) {
                memoryMonitor.stopMonitoring();
            }
            
            // 关闭委托队列
            delegate.close();
            
            // 强制内存清理
            memoryMonitor.forceMemoryCleanup();
            
        } catch (Exception e) {
            logger.error("Error closing MemoryAwareFileQueueWrapper", e);
        }
    }
    
    // 内存管理相关的公共方法
    
    /**
     * 获取内存使用统计信息
     */
    public MappedBufferManager.MemoryStatistics getMemoryStatistics() {
        return bufferManager.getStatistics();
    }
    
    /**
     * 获取内存健康检查报告
     */
    public String getMemoryHealthReport() {
        return memoryMonitor.getHealthReport();
    }
    
    /**
     * 强制进行内存清理
     */
    public void forceMemoryCleanup() {
        memoryMonitor.forceMemoryCleanup();
    }
    
    /**
     * 拍摄内存快照
     */
    public MemoryMonitor.MemorySnapshot takeMemorySnapshot() {
        return memoryMonitor.takeSnapshot();
    }
    
    /**
     * 检查内存健康状态
     */
    public boolean isMemoryHealthy() {
        MemoryMonitor.MemorySnapshot snapshot = memoryMonitor.takeSnapshot();
        return snapshot.getMappedMemoryMB() <= memoryMonitor.getMappedMemoryThresholdMB() &&
               snapshot.getHeapUsagePercent() <= memoryMonitor.getHeapUsageThreshold() &&
               snapshot.getFailedUnmaps() == 0;
    }
    
    /**
     * 获取当前操作计数
     */
    public int getOperationCount() {
        return operationCount;
    }
    
    /**
     * 重置操作计数
     */
    public void resetOperationCount() {
        operationCount = 0;
    }
    
    // Getters
    public FileQueue<E> getDelegate() { return delegate; }
    public MappedBufferManager getBufferManager() { return bufferManager; }
    public MemoryMonitor getMemoryMonitor() { return memoryMonitor; }
    public boolean isMemoryMonitoringEnabled() { return enableMemoryMonitoring; }
    public int getMemoryCheckInterval() { return memoryCheckInterval; }
    
    /**
     * 创建内存感知的FileQueue包装器的工厂方法
     */
    public static <E> MemoryAwareFileQueueWrapper<E> wrap(FileQueue<E> queue, Config config) {
        return new MemoryAwareFileQueueWrapper<>(queue, config);
    }
    
    /**
     * 创建具有自定义配置的内存感知FileQueue包装器
     */
    public static <E> MemoryAwareFileQueueWrapper<E> wrap(FileQueue<E> queue, Config config, 
                                                          boolean enableMonitoring, int checkInterval) {
        return new MemoryAwareFileQueueWrapper<>(queue, config, enableMonitoring, checkInterval);
    }
} 