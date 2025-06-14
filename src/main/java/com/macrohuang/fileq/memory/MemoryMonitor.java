package com.macrohuang.fileq.memory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.conf.MemoryConstants;
import com.macrohuang.fileq.conf.TimeConstants;

/**
 * 内存监控器
 * 监控JVM内存使用情况和MappedByteBuffer内存使用，提供告警功能
 * 
 * @author macro
 */
public class MemoryMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitor.class);
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FileQ-MemoryMonitor");
        t.setDaemon(true);
        return t;
    });
    
    private final MappedBufferManager bufferManager;
    private final MemoryMXBean memoryBean;
    
    // 配置参数
    private volatile long mappedMemoryThresholdMB = MemoryConstants.DEFAULT_MAPPED_MEMORY_THRESHOLD_MB;
    private volatile double heapUsageThreshold = MemoryConstants.DEFAULT_HEAP_USAGE_THRESHOLD;
    private volatile long monitorIntervalSeconds = TimeConstants.DEFAULT_MONITOR_INTERVAL_SECONDS;
    
    // 状态
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private final AtomicLong alertCount = new AtomicLong(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);
    private final long alertCooldownMs = TimeConstants.ALERT_COOLDOWN_MS;
    
    // 统计信息
    private volatile MemorySnapshot lastSnapshot;
    
    public MemoryMonitor() {
        this(MappedBufferManager.getInstance());
    }
    
    public MemoryMonitor(MappedBufferManager bufferManager) {
        this.bufferManager = bufferManager;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        logger.info("MemoryMonitor initialized");
    }
    
    /**
     * 内存快照
     */
    public static class MemorySnapshot {
        private final long timestamp;
        private final long heapUsedMB;
        private final long heapMaxMB;
        private final double heapUsagePercent;
        private final long nonHeapUsedMB;
        private final long mappedBufferCount;
        private final long mappedMemoryMB;
        private final long successfulUnmaps;
        private final long failedUnmaps;
        
        public MemorySnapshot(MemoryUsage heapUsage, MemoryUsage nonHeapUsage, 
                            MappedBufferManager.MemoryStatistics bufferStats) {
            this.timestamp = System.currentTimeMillis();
            this.heapUsedMB = heapUsage.getUsed() / MemoryConstants.BYTES_PER_MB;
            this.heapMaxMB = heapUsage.getMax() / MemoryConstants.BYTES_PER_MB;
            this.heapUsagePercent = (double) heapUsage.getUsed() / heapUsage.getMax();
            this.nonHeapUsedMB = nonHeapUsage.getUsed() / MemoryConstants.BYTES_PER_MB;
            this.mappedBufferCount = bufferStats.getActiveBuffers();
            this.mappedMemoryMB = bufferStats.getTotalMappedMemory() / MemoryConstants.BYTES_PER_MB;
            this.successfulUnmaps = bufferStats.getSuccessfulUnmaps();
            this.failedUnmaps = bufferStats.getFailedUnmaps();
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public long getHeapUsedMB() { return heapUsedMB; }
        public long getHeapMaxMB() { return heapMaxMB; }
        public double getHeapUsagePercent() { return heapUsagePercent; }
        public long getNonHeapUsedMB() { return nonHeapUsedMB; }
        public long getMappedBufferCount() { return mappedBufferCount; }
        public long getMappedMemoryMB() { return mappedMemoryMB; }
        public long getSuccessfulUnmaps() { return successfulUnmaps; }
        public long getFailedUnmaps() { return failedUnmaps; }
        
        @Override
        public String toString() {
            return String.format(
                "MemorySnapshot{heap=%dMB/%.1f%%, nonHeap=%dMB, mapped=%dMB(%d buffers), unmaps=%d/%d}",
                heapUsedMB, heapUsagePercent * 100, nonHeapUsedMB, 
                mappedMemoryMB, mappedBufferCount, successfulUnmaps, failedUnmaps
            );
        }
    }
    
    /**
     * 开始内存监控
     */
    public void startMonitoring() {
        if (monitoring.compareAndSet(false, true)) {
            logger.info("Starting memory monitoring: interval={}s, mappedThreshold={}MB, heapThreshold={}%",
                       monitorIntervalSeconds, mappedMemoryThresholdMB, heapUsageThreshold * 100);
            
            scheduler.scheduleAtFixedRate(this::monitorMemory, 0, monitorIntervalSeconds, TimeUnit.SECONDS);
        } else {
            logger.warn("Memory monitoring already started");
        }
    }
    
    /**
     * 停止内存监控
     */
    public void stopMonitoring() {
        if (monitoring.compareAndSet(true, false)) {
            logger.info("Stopping memory monitoring");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 执行内存监控检查
     */
    private void monitorMemory() {
        try {
            MemorySnapshot snapshot = takeSnapshot();
            lastSnapshot = snapshot;
            
            // 检查内存阈值
            checkMemoryThresholds(snapshot);
            
            // 记录详细日志（DEBUG级别）
            logger.debug("Memory monitoring: {}", snapshot);
            
        } catch (Exception e) {
            logger.error("Error during memory monitoring", e);
        }
    }
    
    /**
     * 拍摄内存快照
     */
    public MemorySnapshot takeSnapshot() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        MappedBufferManager.MemoryStatistics bufferStats = bufferManager.getStatistics();
        
        return new MemorySnapshot(heapUsage, nonHeapUsage, bufferStats);
    }
    
    /**
     * 检查内存阈值并触发告警
     */
    private void checkMemoryThresholds(MemorySnapshot snapshot) {
        boolean shouldAlert = false;
        StringBuilder alertMessage = new StringBuilder("Memory threshold exceeded: ");
        
        // 检查映射内存阈值
        if (snapshot.getMappedMemoryMB() > mappedMemoryThresholdMB) {
            shouldAlert = true;
            alertMessage.append(String.format("Mapped memory %dMB > %dMB threshold; ",
                    snapshot.getMappedMemoryMB(), mappedMemoryThresholdMB));
        }
        
        // 检查堆内存阈值
        if (snapshot.getHeapUsagePercent() > heapUsageThreshold) {
            shouldAlert = true;
            alertMessage.append(String.format("Heap usage %.1f%% > %.1f%% threshold; ",
                    snapshot.getHeapUsagePercent() * 100, heapUsageThreshold * 100));
        }
        
        // 检查失败的unmap操作
        if (snapshot.getFailedUnmaps() > 0) {
            shouldAlert = true;
            alertMessage.append(String.format("Failed unmaps: %d; ", snapshot.getFailedUnmaps()));
        }
        
        // 触发告警（带冷却期）
        if (shouldAlert && canSendAlert()) {
            alertMessage.append(snapshot.toString());
            sendAlert(AlertLevel.WARNING, alertMessage.toString());
            lastAlertTime.set(System.currentTimeMillis());
            alertCount.incrementAndGet();
        }
    }
    
    /**
     * 检查是否可以发送告警（冷却期检查）
     */
    private boolean canSendAlert() {
        long now = System.currentTimeMillis();
        return now - lastAlertTime.get() >= alertCooldownMs;
    }
    
    /**
     * 告警级别
     */
    public enum AlertLevel {
        INFO, WARNING, ERROR, CRITICAL
    }
    
    /**
     * 发送告警
     */
    private void sendAlert(AlertLevel level, String message) {
        switch (level) {
            case INFO:
                logger.info("MEMORY ALERT [{}]: {}", level, message);
                break;
            case WARNING:
                logger.warn("MEMORY ALERT [{}]: {}", level, message);
                break;
            case ERROR:
            case CRITICAL:
                logger.error("MEMORY ALERT [{}]: {}", level, message);
                break;
        }
        
        // 在实际应用中，这里可以集成外部告警系统
        // 例如：发送邮件、短信、Slack通知等
    }
    
    /**
     * 强制进行内存清理
     */
    public void forceMemoryCleanup() {
        logger.info("Forcing memory cleanup");
        
        // 清理所有映射缓冲区
        bufferManager.releaseAllBuffers();
        
        // 建议JVM进行垃圾回收
        System.gc();
        
        // 等待一会儿让GC完成
        try {
            Thread.sleep(MemoryConstants.MEMORY_CLEANUP_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 拍摄清理后的快照
        MemorySnapshot afterCleanup = takeSnapshot();
        logger.info("Memory cleanup completed: {}", afterCleanup);
    }
    
    /**
     * 获取内存健康检查报告
     */
    public String getHealthReport() {
        MemorySnapshot snapshot = takeSnapshot();
        StringBuilder report = new StringBuilder();
        
        report.append("=== FileQ Memory Health Report ===\n");
        report.append(String.format("Report Time: %tc\n", snapshot.getTimestamp()));
        report.append(String.format("Monitoring Status: %s\n", monitoring.get() ? "ACTIVE" : "INACTIVE"));
        report.append(String.format("Alert Count: %d\n", alertCount.get()));
        report.append("\n");
        
        report.append("--- JVM Memory ---\n");
        report.append(String.format("Heap Usage: %d MB / %d MB (%.1f%%)\n",
                snapshot.getHeapUsedMB(), snapshot.getHeapMaxMB(), snapshot.getHeapUsagePercent() * 100));
        report.append(String.format("Non-Heap Usage: %d MB\n", snapshot.getNonHeapUsedMB()));
        report.append("\n");
        
        report.append("--- Mapped Memory ---\n");
        report.append(String.format("Active Buffers: %d\n", snapshot.getMappedBufferCount()));
        report.append(String.format("Mapped Memory: %d MB\n", snapshot.getMappedMemoryMB()));
        report.append(String.format("Successful Unmaps: %d\n", snapshot.getSuccessfulUnmaps()));
        report.append(String.format("Failed Unmaps: %d\n", snapshot.getFailedUnmaps()));
        report.append("\n");
        
        report.append("--- Thresholds ---\n");
        report.append(String.format("Mapped Memory Threshold: %d MB\n", mappedMemoryThresholdMB));
        report.append(String.format("Heap Usage Threshold: %.1f%%\n", heapUsageThreshold * 100));
        report.append(String.format("Monitor Interval: %d seconds\n", monitorIntervalSeconds));
        report.append("\n");
        
        report.append("--- Status ---\n");
        boolean mappedOk = snapshot.getMappedMemoryMB() <= mappedMemoryThresholdMB;
        boolean heapOk = snapshot.getHeapUsagePercent() <= heapUsageThreshold;
        boolean unmapOk = snapshot.getFailedUnmaps() == 0;
        
        report.append(String.format("Mapped Memory: %s\n", mappedOk ? "OK" : "EXCEEDED"));
        report.append(String.format("Heap Usage: %s\n", heapOk ? "OK" : "EXCEEDED"));
        report.append(String.format("Unmap Operations: %s\n", unmapOk ? "OK" : "FAILED"));
        report.append(String.format("Overall Status: %s\n", 
                (mappedOk && heapOk && unmapOk) ? "HEALTHY" : "NEEDS_ATTENTION"));
        
        if (!mappedOk || !heapOk || !unmapOk) {
            report.append("\n--- Detailed Buffer Info ---\n");
            report.append(bufferManager.getDetailedBufferInfo());
        }
        
        return report.toString();
    }
    
    // Configuration setters
    public void setMappedMemoryThresholdMB(long thresholdMB) {
        this.mappedMemoryThresholdMB = thresholdMB;
        logger.info("Updated mapped memory threshold to {}MB", thresholdMB);
    }
    
    public void setHeapUsageThreshold(double threshold) {
        this.heapUsageThreshold = threshold;
        logger.info("Updated heap usage threshold to {:.1f}%", threshold * 100);
    }
    
    public void setMonitorIntervalSeconds(long intervalSeconds) {
        this.monitorIntervalSeconds = intervalSeconds;
        logger.info("Updated monitor interval to {}s", intervalSeconds);
    }
    
    // Getters
    public boolean isMonitoring() { return monitoring.get(); }
    public long getAlertCount() { return alertCount.get(); }
    public MemorySnapshot getLastSnapshot() { return lastSnapshot; }
    public long getMappedMemoryThresholdMB() { return mappedMemoryThresholdMB; }
    public double getHeapUsageThreshold() { return heapUsageThreshold; }
    public long getMonitorIntervalSeconds() { return monitorIntervalSeconds; }
} 