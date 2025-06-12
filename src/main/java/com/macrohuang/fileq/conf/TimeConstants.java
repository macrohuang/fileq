package com.macrohuang.fileq.conf;

/**
 * 时间相关常量
 * 
 * @author macro
 */
public class TimeConstants {
    
    /**
     * 默认监控间隔时间（秒）
     */
    public static final long DEFAULT_MONITOR_INTERVAL_SECONDS = 30;
    
    /**
     * 告警冷却期（毫秒）- 5分钟
     */
    public static final long ALERT_COOLDOWN_MS = 5 * 60 * 1000;
    
    /**
     * 队列等待间隔（毫秒）
     */
    public static final long QUEUE_WAIT_INTERVAL_MS = 100;
    
    /**
     * 重试间隔（毫秒）
     */
    public static final long RETRY_INTERVAL_MS = 10;
    
    /**
     * 每分钟的毫秒数
     */
    public static final long MS_PER_MINUTE = 60 * 1000;
    
    /**
     * 每秒的毫秒数
     */
    public static final long MS_PER_SECOND = 1000;
    
    private TimeConstants() {
        // 防止实例化
    }
}