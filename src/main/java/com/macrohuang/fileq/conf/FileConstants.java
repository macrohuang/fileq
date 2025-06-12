package com.macrohuang.fileq.conf;

/**
 * 文件相关常量
 * 
 * @author macro
 */
public class FileConstants {
    
    /**
     * 默认文件大小（字节）- 100MB
     */
    public static final int DEFAULT_FILE_SIZE_BYTES = 100 * 1024 * 1024;
    
    /**
     * 最大数据块大小（字节）- 100MB
     */
    public static final int MAX_DATA_BLOCK_SIZE_BYTES = 100 * 1024 * 1024;
    
    /**
     * 默认Kryo缓冲区大小（字节）
     */
    public static final int DEFAULT_KRYO_BUFFER_SIZE = 1024;
    
    /**
     * 默认序列化输出缓冲区无限制标志
     */
    public static final int UNLIMITED_BUFFER = -1;
    
    // 常用文件大小常量
    
    /**
     * 1KB 大小
     */
    public static final int SIZE_1KB = 1024;
    
    /**
     * 1MB 大小
     */
    public static final int SIZE_1MB = 1024 * 1024;
    
    /**
     * 512KB 大小
     */
    public static final int SIZE_512KB = 512 * 1024;
    
    /**
     * 10MB 大小
     */
    public static final int SIZE_10MB = 10 * 1024 * 1024;
    
    private FileConstants() {
        // 防止实例化
    }
}