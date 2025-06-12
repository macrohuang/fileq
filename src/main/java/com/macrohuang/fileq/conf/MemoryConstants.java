package com.macrohuang.fileq.conf;

/**
 * 内存管理相关常量
 * 
 * @author macro
 */
public class MemoryConstants {
    
    /**
     * 默认映射内存阈值（MB）
     */
    public static final long DEFAULT_MAPPED_MEMORY_THRESHOLD_MB = 512;
    
    /**
     * 默认堆内存使用阈值（85%）
     */
    public static final double DEFAULT_HEAP_USAGE_THRESHOLD = 0.85;
    
    /**
     * 最小内存阈值（MB）
     */
    public static final long MIN_MEMORY_THRESHOLD_MB = 256;
    
    /**
     * 默认内存检查操作间隔
     */
    public static final int DEFAULT_MEMORY_CHECK_INTERVAL = 1000;
    
    /**
     * 内存清理后等待时间（毫秒）
     */
    public static final long MEMORY_CLEANUP_WAIT_MS = 1000;
    
    // 内存单位转换常量
    
    /**
     * 每KB的字节数
     */
    public static final int BYTES_PER_KB = 1024;
    
    /**
     * 每MB的字节数
     */
    public static final int BYTES_PER_MB = 1024 * 1024;
    
    /**
     * 每GB的字节数
     */
    public static final long BYTES_PER_GB = 1024L * 1024 * 1024;
    
    private MemoryConstants() {
        // 防止实例化
    }
}