package com.macrohuang.fileq.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 资源管理工具类
 * 提供安全的资源关闭和异常处理方法
 * 
 * @author macro
 */
public class ResourceManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    
    /**
     * 安全关闭可关闭资源
     * 
     * @param closeable 要关闭的资源
     * @param resourceName 资源名称，用于日志记录
     */
    public static void safeClose(Closeable closeable, String resourceName) {
        if (closeable != null) {
            try {
                closeable.close();
                logger.debug("Successfully closed {}", resourceName);
            } catch (IOException e) {
                logger.warn("Failed to close {}: {}", resourceName, e.getMessage(), e);
            }
        }
    }
    
    /**
     * 安全关闭可关闭资源（不记录调试日志）
     * 
     * @param closeable 要关闭的资源
     */
    public static void safeClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                logger.warn("Failed to close resource: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 安全关闭通道
     * 
     * @param channel 要关闭的通道
     * @param channelName 通道名称，用于日志记录
     */
    public static void safeClose(Channel channel, String channelName) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
                logger.debug("Successfully closed channel {}", channelName);
            } catch (IOException e) {
                logger.warn("Failed to close channel {}: {}", channelName, e.getMessage(), e);
            }
        }
    }
    
    /**
     * 批量安全关闭资源
     * 
     * @param closeables 要关闭的资源数组
     */
    public static void safeCloseAll(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            safeClose(closeable);
        }
    }
    
    /**
     * 检查磁盘空间是否足够
     * 
     * @param path 文件路径
     * @param requiredSpace 需要的空间大小（字节）
     * @return true 如果空间足够，false 否则
     */
    public static boolean checkDiskSpace(String path, long requiredSpace) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parentDir = file.getParentFile();
            if (parentDir == null) {
                parentDir = file;
            }
            
            long availableSpace = parentDir.getUsableSpace();
            boolean sufficient = availableSpace >= requiredSpace;
            
            if (!sufficient) {
                logger.warn("Insufficient disk space. Required: {} bytes, Available: {} bytes", 
                           requiredSpace, availableSpace);
            }
            
            return sufficient;
        } catch (Exception e) {
            logger.error("Failed to check disk space for path: {}", path, e);
            return false;
        }
    }
    
    /**
     * 获取可用磁盘空间
     * 
     * @param path 文件路径
     * @return 可用空间大小（字节），如果检查失败返回-1
     */
    public static long getAvailableDiskSpace(String path) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parentDir = file.getParentFile();
            if (parentDir == null) {
                parentDir = file;
            }
            return parentDir.getUsableSpace();
        } catch (Exception e) {
            logger.error("Failed to get available disk space for path: {}", path, e);
            return -1;
        }
    }
} 