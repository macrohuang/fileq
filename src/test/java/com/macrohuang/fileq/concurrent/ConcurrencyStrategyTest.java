package com.macrohuang.fileq.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 并发策略测试类
 * 测试不同并发策略的正确性和性能
 */
public class ConcurrencyStrategyTest {
    
    private ConcurrencyStrategy strategy;
    private ExecutorService executorService;
    
    @BeforeEach
    public void setUp() {
        executorService = Executors.newFixedThreadPool(20);
    }
    
    @AfterEach
    public void tearDown() {
        if (strategy != null) {
            strategy.close();
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Test
    public void testReadWriteLockStrategy() throws InterruptedException {
        strategy = new ReadWriteLockStrategy(false);
        testConcurrentOperations(strategy, "ReadWriteLockStrategy");
    }
    
    @Test
    public void testReentrantLockStrategy() throws InterruptedException {
        strategy = new ReentrantLockStrategy(false);
        testConcurrentOperations(strategy, "ReentrantLockStrategy");
    }
    
    @Test
    public void testSingleThreadStrategy() throws InterruptedException {
        strategy = new SingleThreadStrategy();
        testSingleThreadOperations(strategy, "SingleThreadStrategy");
    }
    
    @Test
    public void testFairReadWriteLockStrategy() throws InterruptedException {
        strategy = new ReadWriteLockStrategy(true);
        testConcurrentOperations(strategy, "FairReadWriteLockStrategy");
    }
    
    @Test
    public void testFairReentrantLockStrategy() throws InterruptedException {
        strategy = new ReentrantLockStrategy(true);
        testConcurrentOperations(strategy, "FairReentrantLockStrategy");
    }
    
    private void testConcurrentOperations(ConcurrencyStrategy strategy, String strategyName) throws InterruptedException {
        final int readThreads = 10;
        final int writeThreads = 5;
        final int operationsPerThread = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(readThreads + writeThreads);
        
        final AtomicInteger sharedCounter = new AtomicInteger(0);
        final AtomicLong totalReadTime = new AtomicLong(0);
        final AtomicLong totalWriteTime = new AtomicLong(0);
        final AtomicInteger readOperations = new AtomicInteger(0);
        final AtomicInteger writeOperations = new AtomicInteger(0);
        
        // 启动写线程
        for (int i = 0; i < writeThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        long startTime = System.nanoTime();
                        
                        Integer result = strategy.executeWrite(() -> {
                            // 模拟写操作
                            int currentValue = sharedCounter.get();
                            Thread.sleep(1); // 模拟一些处理时间
                            sharedCounter.set(currentValue + 1);
                            return currentValue + 1;
                        });
                        
                        long endTime = System.nanoTime();
                        totalWriteTime.addAndGet(endTime - startTime);
                        writeOperations.incrementAndGet();
                        
                        Assertions.assertNotNull(result, "Write operation should return a result");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("Write thread " + threadId + " failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // 启动读线程
        for (int i = 0; i < readThreads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        long startTime = System.nanoTime();
                        
                        Integer result = strategy.executeRead(() -> {
                            // 模拟读操作
                            Thread.sleep(1); // 模拟一些处理时间
                            return sharedCounter.get();
                        });
                        
                        long endTime = System.nanoTime();
                        totalReadTime.addAndGet(endTime - startTime);
                        readOperations.incrementAndGet();
                        
                        Assertions.assertNotNull(result, "Read operation should return a result");
                        Assertions.assertTrue(result >= 0, "Counter should be non-negative");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("Read thread " + threadId + " failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // 开始测试
        long testStartTime = System.nanoTime();
        startLatch.countDown();
        
        // 等待所有线程完成
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        long testEndTime = System.nanoTime();
        
        Assertions.assertTrue(completed, "All threads should complete within timeout");
        
        // 验证结果
        int finalCounter = sharedCounter.get();
        int expectedWrites = writeThreads * operationsPerThread;
        
        System.out.printf("\n=== %s Test Results ===\n", strategyName);
        System.out.printf("Final counter value: %d\n", finalCounter);
        System.out.printf("Expected writes: %d\n", expectedWrites);
        System.out.printf("Total test time: %.2f ms\n", (testEndTime - testStartTime) / 1_000_000.0);
        System.out.printf("Read operations: %d, avg time: %.2f μs\n", 
                         readOperations.get(), totalReadTime.get() / (double) readOperations.get() / 1000.0);
        System.out.printf("Write operations: %d, avg time: %.2f μs\n", 
                         writeOperations.get(), totalWriteTime.get() / (double) writeOperations.get() / 1000.0);
        
        // 输出锁统计信息
        LockStatistics stats = strategy.getLockStatistics();
        System.out.printf("Lock statistics: %s\n", stats);
        
        // 验证写操作的正确性（在并发环境下，最终计数器值应该等于写操作次数）
        Assertions.assertEquals(expectedWrites, finalCounter, 
                               "Final counter should equal the number of write operations");
        
        // 验证统计信息的合理性
        Assertions.assertTrue(stats.getReadLockAcquisitions() > 0, "Should have read lock acquisitions");
        Assertions.assertTrue(stats.getWriteLockAcquisitions() > 0, "Should have write lock acquisitions");
        
        System.out.println("=== Test Passed ===\n");
    }
    
    private void testSingleThreadOperations(ConcurrencyStrategy strategy, String strategyName) {
        final int operations = 1000;
        final AtomicInteger counter = new AtomicInteger(0);
        final AtomicLong totalReadTime = new AtomicLong(0);
        final AtomicLong totalWriteTime = new AtomicLong(0);
        
        long testStartTime = System.nanoTime();
        
        // 执行写操作
        for (int i = 0; i < operations; i++) {
            long startTime = System.nanoTime();
            
            Integer result = strategy.executeWrite(() -> {
                int currentValue = counter.get();
                counter.set(currentValue + 1);
                return currentValue + 1;
            });
            
            long endTime = System.nanoTime();
            totalWriteTime.addAndGet(endTime - startTime);
            
            Assertions.assertNotNull(result, "Write operation should return a result");
        }
        
        // 执行读操作
        for (int i = 0; i < operations; i++) {
            long startTime = System.nanoTime();
            
            Integer result = strategy.executeRead(() -> {
                return counter.get();
            });
            
            long endTime = System.nanoTime();
            totalReadTime.addAndGet(endTime - startTime);
            
            Assertions.assertNotNull(result, "Read operation should return a result");
            Assertions.assertEquals(operations, result.intValue(), "Counter should equal operations count");
        }
        
        long testEndTime = System.nanoTime();
        
        System.out.printf("\n=== %s Test Results ===\n", strategyName);
        System.out.printf("Final counter value: %d\n", counter.get());
        System.out.printf("Expected operations: %d\n", operations);
        System.out.printf("Total test time: %.2f ms\n", (testEndTime - testStartTime) / 1_000_000.0);
        System.out.printf("Read operations: %d, avg time: %.2f μs\n", 
                         operations, totalReadTime.get() / (double) operations / 1000.0);
        System.out.printf("Write operations: %d, avg time: %.2f μs\n", 
                         operations, totalWriteTime.get() / (double) operations / 1000.0);
        
        // 输出统计信息
        LockStatistics stats = strategy.getLockStatistics();
        System.out.printf("Operation statistics: %s\n", stats);
        
        // 验证结果
        Assertions.assertEquals(operations, counter.get(), 
                               "Final counter should equal the number of operations");
        
        // 验证统计信息
        Assertions.assertEquals(operations, stats.getReadLockAcquisitions());
        Assertions.assertEquals(operations, stats.getWriteLockAcquisitions());
        Assertions.assertEquals(0.0, stats.getAverageReadLockWaitTime(), 0.1, 
                               "Single thread should have no wait time");
        Assertions.assertEquals(0.0, stats.getAverageWriteLockWaitTime(), 0.1, 
                               "Single thread should have no wait time");
        
        System.out.println("=== Test Passed ===\n");
    }
    
    @Test
    public void testStrategyFactory() {
        // 测试工厂方法
        ConcurrencyStrategy rwStrategy = ConcurrencyStrategyFactory.createStrategy(
            ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK, false);
        Assertions.assertEquals(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK, rwStrategy.getAccessMode());
        rwStrategy.close();
        
        ConcurrencyStrategy reentrantStrategy = ConcurrencyStrategyFactory.createStrategy(
            ConcurrencyStrategy.AccessMode.REENTRANT_LOCK, true);
        Assertions.assertEquals(ConcurrencyStrategy.AccessMode.REENTRANT_LOCK, reentrantStrategy.getAccessMode());
        reentrantStrategy.close();
        
        ConcurrencyStrategy singleStrategy = ConcurrencyStrategyFactory.createStrategy(
            ConcurrencyStrategy.AccessMode.SINGLE_THREAD);
        Assertions.assertEquals(ConcurrencyStrategy.AccessMode.SINGLE_THREAD, singleStrategy.getAccessMode());
        singleStrategy.close();
    }
    
    @Test
    public void testStrategyRecommendation() {
        // 测试策略推荐
        ConcurrencyStrategy.AccessMode mode1 = ConcurrencyStrategyFactory.recommendStrategy(1, 1, false);
        Assertions.assertEquals(ConcurrencyStrategy.AccessMode.SINGLE_THREAD, mode1);
        
        ConcurrencyStrategy.AccessMode mode2 = ConcurrencyStrategyFactory.recommendStrategy(10, 2, false);
        Assertions.assertEquals(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK, mode2);
        
        ConcurrencyStrategy.AccessMode mode3 = ConcurrencyStrategyFactory.recommendStrategy(5, 5, false);
        Assertions.assertEquals(ConcurrencyStrategy.AccessMode.REENTRANT_LOCK, mode3);
        
        ConcurrencyStrategy.AccessMode mode4 = ConcurrencyStrategyFactory.recommendStrategy(1, 1, true);
        Assertions.assertEquals(ConcurrencyStrategy.AccessMode.FILE_LOCK, mode4);
    }
    
    @Test
    public void testLockStatistics() {
        strategy = new ReadWriteLockStrategy(false);
        
        // 执行一些操作
        strategy.executeWrite(() -> {
            Thread.sleep(10);
            return "write result";
        });
        
        strategy.executeRead(() -> {
            Thread.sleep(5);
            return "read result";
        });
        
        LockStatistics stats = strategy.getLockStatistics();
        
        Assertions.assertEquals(1, stats.getWriteLockAcquisitions());
        Assertions.assertEquals(1, stats.getReadLockAcquisitions());
        Assertions.assertTrue(stats.getAverageWriteLockHoldTime() > 0);
        Assertions.assertTrue(stats.getAverageReadLockHoldTime() > 0);
        
        // 测试统计信息重置
        stats.reset();
        Assertions.assertEquals(0, stats.getWriteLockAcquisitions());
        Assertions.assertEquals(0, stats.getReadLockAcquisitions());
    }
} 