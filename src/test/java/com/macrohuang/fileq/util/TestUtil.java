package com.macrohuang.fileq.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 测试工具类
 * 提供跨平台的测试路径生成和其他测试辅助功能
 * 
 * @author macro
 */
public class TestUtil {
    
    /**
     * 获取跨平台的临时目录路径
     * 
     * @param subPath 子目录名称
     * @return 完整的临时目录路径
     */
    public static String getTempPath(String subPath) {
        return System.getProperty("java.io.tmpdir") + File.separator + subPath;
    }
    
    /**
     * 获取带索引的跨平台临时目录路径
     * 
     * @param prefix 前缀
     * @param index 索引
     * @return 完整的临时目录路径
     */
    public static String getTempPathWithIndex(String prefix, int index) {
        return getTempPath(prefix + index);
    }
    
    /**
     * 获取带时间戳的跨平台临时目录路径
     * 
     * @param prefix 前缀
     * @return 完整的临时目录路径
     */
    public static String getTempPathWithTimestamp(String prefix) {
        return getTempPath(prefix + System.currentTimeMillis());
    }
    
    /**
     * 使用Paths API构建跨平台路径
     * 
     * @param first 第一个路径段
     * @param more 其他路径段
     * @return 构建的路径字符串
     */
    public static String buildPath(String first, String... more) {
        Path path = Paths.get(first, more);
        return path.toString();
    }
    
    /**
     * 在临时目录下构建跨平台路径
     * 
     * @param more 路径段
     * @return 构建的路径字符串
     */
    public static String buildTempPath(String... more) {
        String tempDir = System.getProperty("java.io.tmpdir");
        if (more.length == 0) {
            return tempDir;
        }
        return buildPath(tempDir, more);
    }
    
    /**
     * 验证路径是否为跨平台兼容格式
     * 
     * @param path 要检查的路径
     * @return true 如果路径使用了正确的分隔符
     */
    public static boolean isCrossPlatformPath(String path) {
        // 检查是否包含硬编码的分隔符
        if (path.contains("/") && !"/".equals(File.separator)) {
            return false;  // 在Windows上使用了Unix分隔符
        }
        if (path.contains("\\") && !"\\".equals(File.separator)) {
            return false;  // 在Unix上使用了Windows分隔符
        }
        return true;
    }
}