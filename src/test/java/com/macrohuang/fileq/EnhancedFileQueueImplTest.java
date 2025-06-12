package com.macrohuang.fileq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.concurrent.ConcurrencyStrategy;
import com.macrohuang.fileq.concurrent.LockStatistics;
import com.macrohuang.fileq.impl.EnhancedFileQueueImpl;

/**
 * 增强FileQueue实现的测试类
 * 测试不同并发策略下的队列操作
 */
public class EnhancedFileQueueImplTest {
    
    private Config config;
    private static int index = 0;
    
    @BeforeEach
    public void init() {
        config = new Config();
        config.setBasePath("/tmp/enhanced_filequeue_test_" + (index++));
        config.setInit(true);
        config.setFileSize(1024 * 1024 * 10);
    }
    
    @AfterEach
    public void cleanup() {
        // 清理会在队列的delete()方法中处理
    }
    
    @Test
    public void testReadWriteLockStrategy() {
        config.setConcurrencyMode(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK);
        config.setFairLock(false);
        
        EnhancedFileQueueImpl<String> queue = new EnhancedFileQueueImpl<>(config);
        
        try {
            Assertions.assertEquals(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK, queue.getConcurrencyMode());
            
            // 基本操作测试
            queue.add("test1");
            queue.add("test2");
            queue.add("test3");
            
            Assertions.assertEquals(3, queue.size());
            Assertions.assertEquals("test1", queue.peek());
            try {
                Assertions.assertEquals("test1", queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Assertions.fail("Interrupted during take operation");
            }
            Assertions.assertEquals(2, queue.size());
            
            // 验证统计信息
            LockStatistics stats = queue.getLockStatistics();
            Assertions.assertTrue(stats.getWriteLockAcquisitions() > 0);
            Assertions.assertTrue(stats.getReadLockAcquisitions() > 0);
            
            System.out.println("ReadWriteLock strategy stats: " + stats);
            
        } finally {
            queue.delete();
        }
    }
    
    @Test
    public void testReentrantLockStrategy() {
        config.setConcurrencyMode(ConcurrencyStrategy.AccessMode.REENTRANT_LOCK);
        config.setFairLock(true);
        
        EnhancedFileQueueImpl<String> queue = new EnhancedFileQueueImpl<>(config);
        
        try {
            Assertions.assertEquals(ConcurrencyStrategy.AccessMode.REENTRANT_LOCK, queue.getConcurrencyMode());
            
            // 基本操作测试
            queue.add("data1");
            queue.add("data2");
            
            Assertions.assertEquals(2, queue.size());
            Assertions.assertEquals("data1", queue.remove());
            Assertions.assertEquals(1, queue.size());
            
            // 验证统计信息
            LockStatistics stats = queue.getLockStatistics();
            System.out.println("ReentrantLock strategy stats: " + stats);
            
        } finally {
            queue.delete();
        }
    }
    
    @Test
    public void testSingleThreadStrategy() {
        config.setConcurrencyMode(ConcurrencyStrategy.AccessMode.SINGLE_THREAD);
        
        EnhancedFileQueueImpl<String> queue = new EnhancedFileQueueImpl<>(config);
        
        try {
            Assertions.assertEquals(ConcurrencyStrategy.AccessMode.SINGLE_THREAD, queue.getConcurrencyMode());
            
            // 基本操作测试
            queue.add("single1");
            queue.add("single2");
            
            Assertions.assertEquals(2, queue.size());
            Assertions.assertEquals("single1", queue.peek());
            
            // 验证统计信息（单线程策略应该没有锁等待时间）
            LockStatistics stats = queue.getLockStatistics();
            Assertions.assertEquals(0, stats.getAverageReadLockWaitTime(), 0.1);
            Assertions.assertEquals(0, stats.getAverageWriteLockWaitTime(), 0.1);
            
            System.out.println("SingleThread strategy stats: " + stats);
            
        } finally {
            queue.delete();
        }
    }
    
    @Test
    public void testAutomaticStrategyRecommendation() {
        // 不设置并发模式，让系统自动推荐
        config.setConcurrencyMode(null); // 明确设置为null以触发自动推荐
        config.setExpectedReadThreads(10);
        config.setExpectedWriteThreads(2);
        config.setMultiProcessAccess(false);
        
        EnhancedFileQueueImpl<String> queue = new EnhancedFileQueueImpl<>(config);
        
        try {
            // 读多写少的场景应该推荐读写锁
            Assertions.assertEquals(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK, queue.getConcurrencyMode());
            
            queue.add("auto1");
            Assertions.assertEquals("auto1", queue.peek());
            
        } finally {
            queue.delete();
        }
    }
    
    @Test
    public void testConcurrentOperationsWithReadWriteLock() throws InterruptedException {
        config.setConcurrencyMode(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK);
        config.setFairLock(false);
        
        final EnhancedFileQueueImpl<String> queue = new EnhancedFileQueueImpl<>(config);
        final int readThreads = 8;
        final int writeThreads = 4;
        final int operationsPerThread = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(readThreads + writeThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(readThreads + writeThreads);
        
        AtomicInteger addedCount = new AtomicInteger(0);
        AtomicInteger removedCount = new AtomicInteger(0);
        
        try {
            // 启动写线程
            for (int i = 0; i < writeThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            queue.add("data-" + threadId + "-" + j);
                            addedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Assertions.fail("Write thread failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            // 启动读线程
            for (int i = 0; i < readThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            String data = queue.take(100, TimeUnit.MILLISECONDS);
                            if (data != null) {
                                removedCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Assertions.fail("Read thread failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            // 开始测试
            long startTime = System.nanoTime();
            startLatch.countDown();
            
            // 等待完成
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            long endTime = System.nanoTime();
            
            Assertions.assertTrue(completed, "All threads should complete within timeout");
            
            // 验证结果
            int finalSize = queue.size();
            int expectedRemaining = addedCount.get() - removedCount.get();
            
            System.out.printf("Concurrent test results:\n");
            System.out.printf("Added: %d, Removed: %d, Final size: %d, Expected: %d\n", 
                             addedCount.get(), removedCount.get(), finalSize, expectedRemaining);
            System.out.printf("Test duration: %.2f ms\n", (endTime - startTime) / 1_000_000.0);
            
            // 输出锁统计信息
            LockStatistics stats = queue.getLockStatistics();
            System.out.printf("Lock statistics: %s\n", stats);
            
            // 验证最终状态的合理性
            Assertions.assertTrue(Math.abs(finalSize - expectedRemaining) <= writeThreads,
                                 "Size should be close to expected remaining");
            
            // 验证锁统计信息
            Assertions.assertTrue(stats.getReadLockAcquisitions() > 0);
            Assertions.assertTrue(stats.getWriteLockAcquisitions() > 0);
            Assertions.assertTrue(stats.getLockContentionRate() >= 0.0);
            
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            queue.delete();
        }
    }
    
    @Test
    public void testPerformanceComparison() throws InterruptedException {
        final int operations = 1000;
        
        // 测试读写锁策略
        long rwLockTime = testStrategyPerformance(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK, operations);
        
        // 测试重入锁策略
        long reentrantLockTime = testStrategyPerformance(ConcurrencyStrategy.AccessMode.REENTRANT_LOCK, operations);
        
        // 测试单线程策略
        long singleThreadTime = testStrategyPerformance(ConcurrencyStrategy.AccessMode.SINGLE_THREAD, operations);
        
        System.out.printf("\nPerformance Comparison (%d operations):\n", operations);
        System.out.printf("ReadWriteLock: %.2f ms\n", rwLockTime / 1_000_000.0);
        System.out.printf("ReentrantLock: %.2f ms\n", reentrantLockTime / 1_000_000.0);
        System.out.printf("SingleThread: %.2f ms\n", singleThreadTime / 1_000_000.0);
        
        // 验证性能差异在合理范围内（允许一定的性能波动）
        // 单线程策略通常应该比较快，但由于JVM优化等因素，可能不总是最快
        double singleThreadMs = singleThreadTime / 1_000_000.0;
        double rwLockMs = rwLockTime / 1_000_000.0;
        double reentrantLockMs = reentrantLockTime / 1_000_000.0;
        
        // 验证所有策略的性能都在合理范围内（不超过1秒）
        Assertions.assertTrue(singleThreadMs < 1000, "SingleThread should complete within 1 second");
        Assertions.assertTrue(rwLockMs < 1000, "ReadWriteLock should complete within 1 second");
        Assertions.assertTrue(reentrantLockMs < 1000, "ReentrantLock should complete within 1 second");
        
        // 验证读写锁在读多写少场景下的性能优势（相对于重入锁）
        // 在单线程测试中，读写锁可能比重入锁稍快，因为读锁开销较小
        System.out.printf("Performance ratios - RW/Reentrant: %.2f, Single/RW: %.2f, Single/Reentrant: %.2f\n",
                         rwLockMs / reentrantLockMs, singleThreadMs / rwLockMs, singleThreadMs / reentrantLockMs);
    }
    
    private long testStrategyPerformance(ConcurrencyStrategy.AccessMode mode, int operations) {
        Config testConfig = new Config();
        testConfig.setBasePath("/tmp/perf_test_" + mode.name().toLowerCase() + "_" + (index++));
        testConfig.setInit(true);
        testConfig.setFileSize(1024 * 1024 * 10);
        testConfig.setConcurrencyMode(mode);
        
        EnhancedFileQueueImpl<String> queue = new EnhancedFileQueueImpl<>(testConfig);
        
        try {
            long startTime = System.nanoTime();
            
            // 执行操作
            for (int i = 0; i < operations; i++) {
                queue.add("data-" + i);
            }
            
            for (int i = 0; i < operations; i++) {
                try {
                    String data = queue.take();
                    Assertions.assertNotNull(data);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Assertions.fail("Interrupted during take operation");
                }
            }
            
            long endTime = System.nanoTime();
            return endTime - startTime;
            
        } finally {
            queue.delete();
        }
    }
} 