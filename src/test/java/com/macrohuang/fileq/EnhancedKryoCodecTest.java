package com.macrohuang.fileq;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.macrohuang.fileq.codec.impl.EnhancedKryoCodec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.impl.ThreadLockFileQueueImpl;

/**
 * 测试增强版KryoCodec的功能
 */
public class EnhancedKryoCodecTest {
    
    private Config config;
    private static int index = 0;
    
    @BeforeEach
    public void init() {
        config = new Config();
        config.setBasePath("/tmp/filequeue_enhanced_kryo_test_" + (index++));
        config.setInit(true);
        config.setFileSize(1024 * 1024);
    }
    
    /**
     * 比较原始KryoCodec和增强版KryoCodec的性能
     */
    @Test
    public void testCodecComparison() {
        KryoCodec originalCodec = new KryoCodec();
        EnhancedKryoCodec enhancedCodec = new EnhancedKryoCodec();
        
        // 测试数据
        List<String> arraysList = Arrays.asList("test1", "test2", "test3");
        TestObject testObj = new TestObject("comparison", arraysList, 42);
        
        System.out.println("=== Codec Comparison Test ===");
        System.out.println("Test object: " + testObj);
        System.out.println("Arrays.asList() class: " + arraysList.getClass().getName());
        
        // 测试原始编解码器
        testCodecPerformance(originalCodec, testObj, "Original KryoCodec");
        
        // 测试增强版编解码器
        testCodecPerformance(enhancedCodec, testObj, "Enhanced KryoCodec");
    }
    
    /**
     * 测试增强版编解码器在FileQueue中的使用
     */
    @Test
    public void testEnhancedCodecInFileQueue() {
        // 使用增强版编解码器
        config.setCodec(new EnhancedKryoCodec());
        FileQueue<TestObject> queue = new ThreadLockFileQueueImpl<>(config);
        
        // 创建包含Arrays.asList()的测试对象
        List<String> arraysList = Arrays.asList("queue1", "queue2", "queue3");
        TestObject original = new TestObject("enhanced-queue-test", arraysList, 123);
        
        System.out.println("=== Enhanced Codec in FileQueue Test ===");
        System.out.println("Adding to queue: " + original);
        
        try {
            // 添加到队列
            queue.add(original);
            System.out.println("Successfully added to queue, size: " + queue.size());
            
            // 从队列取出
            TestObject retrieved = queue.take();
            System.out.println("Retrieved from queue: " + retrieved);
            
            // 验证结果
            Assertions.assertEquals(original.getName(), retrieved.getName());
            Assertions.assertEquals(original.getItems(), retrieved.getItems());
            Assertions.assertEquals(original.getValue(), retrieved.getValue());
            
            System.out.println("✅ Enhanced codec in FileQueue test successful!");
            
        } catch (Exception e) {
            System.err.println("❌ Enhanced codec test failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail("Enhanced codec should work: " + e.getMessage());
        } finally {
            queue.delete();
        }
    }
    
    /**
     * 测试各种边界情况
     */
    @Test
    public void testEdgeCases() {
        EnhancedKryoCodec codec = new EnhancedKryoCodec();
        
        System.out.println("=== Edge Cases Test ===");
        
        // 测试空的Arrays.asList()
        List<String> emptyList = Arrays.asList();
        testSerializationRoundTrip(codec, emptyList, "Empty Arrays.asList()");
        
        // 测试单元素Arrays.asList()
        List<String> singleList = Arrays.asList("single");
        testSerializationRoundTrip(codec, singleList, "Single element Arrays.asList()");
        
        // 测试嵌套的Arrays.asList()
        List<List<String>> nestedList = Arrays.asList(
            Arrays.asList("a", "b"),
            Arrays.asList("c", "d", "e")
        );
        testSerializationRoundTrip(codec, nestedList, "Nested Arrays.asList()");
        
        // 测试包含null的Arrays.asList()
        List<String> nullList = Arrays.asList("item1", null, "item3");
        testSerializationRoundTrip(codec, nullList, "Arrays.asList() with null");
    }
    
    /**
     * 测试编解码器性能
     */
    private void testCodecPerformance(com.macrohuang.fileq.codec.Codec codec, TestObject testObj, String codecName) {
        System.out.println("\n--- Testing " + codecName + " ---");
        
        try {
            long startTime = System.nanoTime();
            
            // 序列化
            byte[] serialized = codec.encode(testObj);
            long serializeTime = System.nanoTime() - startTime;
            
            System.out.println("Serialization successful, size: " + serialized.length + " bytes");
            System.out.println("Serialization time: " + (serializeTime / 1000) + " microseconds");
            
            // 反序列化
            startTime = System.nanoTime();
            TestObject deserialized = (TestObject) codec.decode(serialized);
            long deserializeTime = System.nanoTime() - startTime;
            
            System.out.println("Deserialization time: " + (deserializeTime / 1000) + " microseconds");
            System.out.println("Deserialized: " + deserialized);
            System.out.println("Deserialized items class: " + deserialized.getItems().getClass().getName());
            
            // 验证结果
            Assertions.assertEquals(testObj.getName(), deserialized.getName());
            Assertions.assertEquals(testObj.getItems(), deserialized.getItems());
            Assertions.assertEquals(testObj.getValue(), deserialized.getValue());
            
            System.out.println("✅ " + codecName + " test successful!");
            
        } catch (Exception e) {
            System.err.println("❌ " + codecName + " test failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail(codecName + " should work: " + e.getMessage());
        }
    }
    
    /**
     * 通用的序列化往返测试方法
     */
    private <T> void testSerializationRoundTrip(EnhancedKryoCodec codec, T original, String testName) {
        System.out.println("\n--- Testing: " + testName + " ---");
        System.out.println("Original: " + original);
        System.out.println("Original class: " + original.getClass().getName());
        
        try {
            // 序列化
            byte[] serialized = codec.encode(original);
            System.out.println("Serialization successful, size: " + serialized.length + " bytes");
            
            // 反序列化
            @SuppressWarnings("unchecked")
            T deserialized = (T) codec.decode(serialized);
            System.out.println("Deserialized: " + deserialized);
            System.out.println("Deserialized class: " + deserialized.getClass().getName());
            
            // 验证
            Assertions.assertEquals(original, deserialized);
            System.out.println("✅ " + testName + " successful!");
            
        } catch (Exception e) {
            System.err.println("❌ " + testName + " failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail(testName + " should work: " + e.getMessage());
        }
    }
    
    /**
     * 测试用的对象类
     */
    public static class TestObject {
        private String name;
        private List<String> items;
        private Integer value;
        
        public TestObject() {}
        
        public TestObject(String name, List<String> items, Integer value) {
            this.name = name;
            this.items = items;
            this.value = value;
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
        
        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestObject that = (TestObject) obj;
            return java.util.Objects.equals(name, that.name) &&
                   java.util.Objects.equals(items, that.items) &&
                   java.util.Objects.equals(value, that.value);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, items, value);
        }
        
        @Override
        public String toString() {
            return "TestObject{name='" + name + "', items=" + items + ", value=" + value + "}";
        }
    }
} 