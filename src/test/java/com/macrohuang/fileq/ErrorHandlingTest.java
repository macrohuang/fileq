package com.macrohuang.fileq;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.exception.FileQueueIOException;
import com.macrohuang.fileq.exception.InsufficientSpaceException;
import com.macrohuang.fileq.impl.ThreadLockFileQueueImpl;
import com.macrohuang.fileq.util.ResourceManager;

/**
 * 测试错误处理和资源管理的改进
 */
public class ErrorHandlingTest {
    
    private Config config;
    private static int index = 0;
    
    @BeforeEach
    public void init() {
        config = new Config();
        config.setBasePath("/tmp/filequeue_error_test_" + (index++));
        config.setInit(true);
        config.setFileSize(1024 * 1024);
    }
    
    /**
     * 测试空元素添加的错误处理
     */
    @Test
    public void testAddNullElement() {
        FileQueue<String> queue = new ThreadLockFileQueueImpl<>(config);
        
        try {
            // 尝试添加null元素
            IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class, 
                () -> queue.add(null)
            );
            
            Assertions.assertEquals("Cannot add null element to queue", exception.getMessage());
            System.out.println("✅ Null element rejection test passed");
            
        } finally {
            queue.delete();
        }
    }
    
    /**
     * 测试队列关闭后的操作
     */
    @Test
    public void testOperationsAfterClose() {
        FileQueue<String> queue = new ThreadLockFileQueueImpl<>(config);
        
        // 添加一些数据
        queue.add("test1");
        queue.add("test2");
        
        // 关闭队列
        queue.close();
        
        // 尝试在关闭后进行操作
        Assertions.assertThrows(IllegalStateException.class, () -> queue.add("test3"));
        Assertions.assertThrows(IllegalStateException.class, () -> queue.peek());
        Assertions.assertThrows(IllegalStateException.class, () -> queue.remove());
        Assertions.assertThrows(IllegalStateException.class, () -> queue.clear());
        
        System.out.println("✅ Operations after close test passed");
        
        queue.delete();
    }
    
    /**
     * 测试重复关闭队列
     */
    @Test
    public void testMultipleClose() {
        FileQueue<String> queue = new ThreadLockFileQueueImpl<>(config);
        
        queue.add("test");
        
        // 多次关闭应该是安全的
        queue.close();
        queue.close();
        queue.close();
        
        System.out.println("✅ Multiple close test passed");
        
        queue.delete();
    }
    
    /**
     * 测试磁盘空间检查
     */
    @Test
    public void testDiskSpaceCheck() {
        String testPath = "/tmp/disk_space_test";
        
        // 测试正常情况
        boolean hasSpace = ResourceManager.checkDiskSpace(testPath, 1024);
        Assertions.assertTrue(hasSpace, "Should have enough space for 1KB");
        
        // 测试获取可用空间
        long availableSpace = ResourceManager.getAvailableDiskSpace(testPath);
        Assertions.assertTrue(availableSpace > 0, "Available space should be positive");
        
        System.out.println("Available disk space: " + availableSpace + " bytes");
        System.out.println("✅ Disk space check test passed");
    }
    
    /**
     * 测试无效路径的处理
     */
    @Test
    public void testInvalidPath() {
        // 使用无效路径
        String invalidPath = "/invalid/path/that/does/not/exist";
        
        long availableSpace = ResourceManager.getAvailableDiskSpace(invalidPath);
        Assertions.assertTrue(availableSpace <= 0, "Invalid path should return -1 or 0");
        
        boolean hasSpace = ResourceManager.checkDiskSpace(invalidPath, 1024);
        Assertions.assertFalse(hasSpace, "Invalid path should return false");
        
        System.out.println("✅ Invalid path test passed");
    }
    
    /**
     * 测试资源安全关闭
     */
    @Test
    public void testSafeResourceClose() throws IOException {
        // 创建临时文件用于测试
        Path tempFile = Files.createTempFile("test", ".tmp");
        
        try {
            // 测试正常关闭
            java.io.FileInputStream fis = new java.io.FileInputStream(tempFile.toFile());
            ResourceManager.safeClose(fis, "test file input stream");
            
            // 测试关闭null资源
            ResourceManager.safeClose(null, "null resource");
            
            // 测试批量关闭
            java.io.FileInputStream fis1 = new java.io.FileInputStream(tempFile.toFile());
            java.io.FileInputStream fis2 = new java.io.FileInputStream(tempFile.toFile());
            ResourceManager.safeCloseAll(fis1, fis2, null);
            
            System.out.println("✅ Safe resource close test passed");
            
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    /**
     * 测试文件权限问题的处理
     */
    @Test
    public void testFilePermissionHandling() {
        // 尝试在只读目录创建队列（如果可能的话）
        String readOnlyPath = "/tmp/readonly_test_" + System.currentTimeMillis();
        File readOnlyDir = new File(readOnlyPath);
        
        try {
            if (readOnlyDir.mkdirs()) {
                readOnlyDir.setReadOnly();
                
                Config readOnlyConfig = new Config();
                readOnlyConfig.setBasePath(readOnlyPath + "/queue");
                readOnlyConfig.setInit(true);
                readOnlyConfig.setFileSize(1024);
                
                // 在某些系统上，这可能会抛出异常
                try {
                    FileQueue<String> queue = new ThreadLockFileQueueImpl<>(readOnlyConfig);
                    queue.delete();
                    System.out.println("✅ Read-only directory test completed (no exception thrown)");
                } catch (FileQueueIOException e) {
                    System.out.println("✅ Read-only directory test passed (expected exception: " + e.getMessage() + ")");
                }
            } else {
                System.out.println("⚠️ Could not create read-only directory for testing");
            }
        } finally {
            // 清理
            if (readOnlyDir.exists()) {
                readOnlyDir.setWritable(true);
                readOnlyDir.delete();
            }
        }
    }
    
    /**
     * 测试大文件处理
     */
    @Test
    public void testLargeFileHandling() {
        Config largeConfig = new Config();
        largeConfig.setBasePath("/tmp/large_file_test_" + System.currentTimeMillis());
        largeConfig.setInit(true);
        largeConfig.setFileSize(1024 * 1024 * 10); // 10MB
        
        FileQueue<byte[]> queue = new ThreadLockFileQueueImpl<>(largeConfig);
        
        try {
            // 创建大对象
            byte[] largeData = new byte[1024 * 1024]; // 1MB
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            
            // 添加多个大对象
            for (int i = 0; i < 5; i++) {
                queue.add(largeData);
            }
            
            Assertions.assertEquals(5, queue.size());
            
            // 读取并验证
            for (int i = 0; i < 5; i++) {
                try {
                    byte[] retrieved = queue.take();
                    Assertions.assertNotNull(retrieved);
                    Assertions.assertEquals(largeData.length, retrieved.length);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Assertions.fail("Unexpected interruption: " + e.getMessage());
                }
            }
            
            Assertions.assertEquals(0, queue.size());
            System.out.println("✅ Large file handling test passed");
            
        } finally {
            queue.delete();
        }
    }
} 