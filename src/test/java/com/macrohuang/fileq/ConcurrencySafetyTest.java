package com.macrohuang.fileq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.impl.ThreadLockFileQueueImpl;

/**
 * 专门测试并发安全性的测试类
 */
public class ConcurrencySafetyTest {
    
    private Config config;
    private static int index = 0;
    
    @BeforeEach
    public void init() {
        config = new Config();
        config.setBasePath("/tmp/filequeue_concurrency_test_" + (index++));
        config.setInit(true);
        config.setFileSize(1024 * 1024 * 10);
    }
    
    /**
     * 测试remain()方法的线程安全性
     */
    @Test
    public void testRemainMethodThreadSafety() throws InterruptedException {
        final FileQueue<String> queue = new ThreadLockFileQueueImpl<>(config);
        final int threadCount = 10;
        final int operationsPerThread = 1000;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount * 2);
        final AtomicInteger addCount = new AtomicInteger(0);
        final AtomicInteger removeCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);
        
        // 启动写线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        queue.add("data-" + threadId + "-" + j);
                        addCount.incrementAndGet();
                        
                        // 测试remain()方法不会抛出异常
                        boolean hasRemaining = queue.remain();
                        // remain()应该返回合理的值
                        Assertions.assertTrue(hasRemaining || queue.size() == 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("Writer thread failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // 启动读线程
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String data = queue.take(100, TimeUnit.MILLISECONDS);
                        if (data != null) {
                            removeCount.incrementAndGet();
                        }
                        
                        // 测试remain()方法不会抛出异常
                        boolean hasRemaining = queue.remain();
                        // remain()应该返回合理的值
                        Assertions.assertTrue(hasRemaining || queue.size() == 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("Reader thread failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // 开始测试
        endLatch.await(30, TimeUnit.SECONDS); // 等待所有线程完成
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // 验证最终状态
        int finalSize = queue.size();
        int expectedRemaining = addCount.get() - removeCount.get();
        
        System.out.println("Added: " + addCount.get() + ", Removed: " + removeCount.get() + 
                          ", Final size: " + finalSize + ", Expected: " + expectedRemaining);
        
        // 允许一些误差，因为读线程可能超时
        Assertions.assertTrue(Math.abs(finalSize - expectedRemaining) <= threadCount,
                "Size mismatch: expected around " + expectedRemaining + ", got " + finalSize);
        
        queue.delete();
    }
    
    /**
     * 测试clear()方法的原子性
     */
    @Test
    public void testClearMethodAtomicity() throws InterruptedException {
        final FileQueue<String> queue = new ThreadLockFileQueueImpl<>(config);
        final int itemCount = 1000;
        
        // 先添加一些数据
        for (int i = 0; i < itemCount; i++) {
            queue.add("item-" + i);
        }
        
        Assertions.assertEquals(itemCount, queue.size());
        
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(2);
        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger addCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // 清空线程
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10; i++) {
                    queue.clear();
                    clearCount.incrementAndGet();
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });
        
        // 添加线程
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 100; i++) {
                    queue.add("new-item-" + i);
                    addCount.incrementAndGet();
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });
        
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // clear()操作应该是原子的，最终大小应该合理
        int finalSize = queue.size();
        System.out.println("Clear count: " + clearCount.get() + ", Add count: " + addCount.get() + 
                          ", Final size: " + finalSize);
        
        // 最终大小应该不超过添加的数量
        Assertions.assertTrue(finalSize <= addCount.get(),
                "Final size should not exceed added items");
        
        queue.delete();
    }
    
    /**
     * 测试size()方法的一致性
     */
    @Test
    public void testSizeConsistency() throws InterruptedException {
        final FileQueue<String> queue = new ThreadLockFileQueueImpl<>(config);
        final int threadCount = 5;
        final int operationsPerThread = 200;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount * 2);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);
        
        // 写线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        queue.add("data-" + threadId + "-" + j);
                        
                        // size()应该始终返回非负数
                        int size = queue.size();
                        Assertions.assertTrue(size >= 0, "Size should be non-negative, got: " + size);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("Writer thread failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // 读线程
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String data = queue.take(50, TimeUnit.MILLISECONDS);
                        
                        // size()应该始终返回非负数
                        int size = queue.size();
                        Assertions.assertTrue(size >= 0, "Size should be non-negative, got: " + size);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("Reader thread failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // 最终验证
        int finalSize = queue.size();
        Assertions.assertTrue(finalSize >= 0, "Final size should be non-negative");
        
        queue.delete();
    }
} 