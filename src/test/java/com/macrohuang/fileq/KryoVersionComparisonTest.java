package com.macrohuang.fileq;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.macrohuang.fileq.codec.impl.KryoCodec;

/**
 * 详细测试Kryo版本升级对Arrays.asList()序列化问题的修复
 */
public class KryoVersionComparisonTest {
    
    /**
     * 测试各种Arrays.asList()的使用场景
     */
    @Test
    public void testVariousArraysAsListScenarios() {
        KryoCodec codec = new KryoCodec();
        
        // 场景1: 直接序列化Arrays.asList()
        List<String> stringList = Arrays.asList("a", "b", "c");
        testSerializationRoundTrip(codec, stringList, "String Arrays.asList()");
        
        // 场景2: 序列化包含Arrays.asList()的复杂对象
        ComplexObject complex = new ComplexObject();
        complex.setName("test");
        complex.setItems(Arrays.asList("item1", "item2", "item3"));
        complex.setNumbers(Arrays.asList(1, 2, 3, 4, 5));
        testSerializationRoundTrip(codec, complex, "Complex object with Arrays.asList()");
        
        // 场景3: 嵌套的Arrays.asList()
        List<List<String>> nestedList = Arrays.asList(
            Arrays.asList("a1", "a2"),
            Arrays.asList("b1", "b2", "b3"),
            Arrays.asList("c1")
        );
        testSerializationRoundTrip(codec, nestedList, "Nested Arrays.asList()");
        
        // 场景4: 空的Arrays.asList()
        List<String> emptyList = Arrays.asList();
        testSerializationRoundTrip(codec, emptyList, "Empty Arrays.asList()");
        
        // 场景5: 单元素Arrays.asList()
        List<String> singleList = Arrays.asList("single");
        testSerializationRoundTrip(codec, singleList, "Single element Arrays.asList()");
    }
    
    /**
     * 测试Kryo直接API的使用
     */
    @Test
    public void testKryoDirectAPI() {
        Kryo kryo = new Kryo();
        // 关键：设置不要求注册，这是KryoCodec的解决方案
        kryo.setRegistrationRequired(false);
        
        // 测试Arrays.asList()的直接序列化
        List<String> original = Arrays.asList("direct1", "direct2", "direct3");
        
        System.out.println("Testing Kryo direct API:");
        System.out.println("Original list: " + original);
        System.out.println("Original class: " + original.getClass().getName());
        
        try {
            // 序列化
            Output output = new Output(1024);
            kryo.writeObject(output, original);
            byte[] serialized = output.toBytes();
            output.close();
            
            System.out.println("Serialization successful, size: " + serialized.length + " bytes");
            
            // 反序列化
            Input input = new Input(serialized);
            @SuppressWarnings("unchecked")
            List<String> deserialized = kryo.readObject(input, original.getClass());
            input.close();
            
            System.out.println("Deserialized list: " + deserialized);
            System.out.println("Deserialized class: " + deserialized.getClass().getName());
            
            // 验证内容相等
            Assertions.assertEquals(original, deserialized);
            System.out.println("✅ Kryo direct API test successful!");
            
        } catch (Exception e) {
            System.err.println("❌ Kryo direct API test failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail("Kryo direct API should work with setRegistrationRequired(false): " + e.getMessage());
        }
    }
    
    /**
     * 检查Kryo版本信息
     */
    @Test
    public void testKryoVersionInfo() {
        System.out.println("=== Kryo Version Information ===");
        
        // 尝试获取Kryo版本信息
        try {
            Package kryoPackage = Kryo.class.getPackage();
            if (kryoPackage != null) {
                System.out.println("Kryo Package: " + kryoPackage.getName());
                System.out.println("Implementation Title: " + kryoPackage.getImplementationTitle());
                System.out.println("Implementation Version: " + kryoPackage.getImplementationVersion());
                System.out.println("Specification Title: " + kryoPackage.getSpecificationTitle());
                System.out.println("Specification Version: " + kryoPackage.getSpecificationVersion());
            }
        } catch (Exception e) {
            System.out.println("Could not get package info: " + e.getMessage());
        }
        
        // 测试Kryo的基本功能
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // 关键设置
        System.out.println("Kryo instance created successfully");
        System.out.println("Kryo class: " + kryo.getClass().getName());
        
        // 检查是否支持Arrays$ArrayList
        List<String> arraysList = Arrays.asList("version", "test");
        System.out.println("Arrays.asList() class: " + arraysList.getClass().getName());
        
        // 测试序列化能力而不是注册状态
        try {
            Output output = new Output(1024);
            kryo.writeClassAndObject(output, arraysList);
            byte[] serialized = output.toBytes();
            output.close();
            
            Input input = new Input(serialized);
            @SuppressWarnings("unchecked")
            List<String> deserialized = (List<String>) kryo.readClassAndObject(input);
            input.close();
            
            System.out.println("✅ Arrays$ArrayList serialization test successful");
            System.out.println("Original: " + arraysList);
            System.out.println("Deserialized: " + deserialized);
            Assertions.assertEquals(arraysList, deserialized);
            
        } catch (Exception e) {
            System.err.println("❌ Arrays$ArrayList serialization test failed: " + e.getMessage());
            Assertions.fail("Should be able to serialize Arrays$ArrayList with setRegistrationRequired(false)");
        }
    }
    
    /**
     * 通用的序列化往返测试方法
     */
    private <T> void testSerializationRoundTrip(KryoCodec codec, T original, String testName) {
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
     * 用于测试的复杂对象
     */
    public static class ComplexObject {
        private String name;
        private List<String> items;
        private List<Integer> numbers;
        
        public ComplexObject() {}
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
        
        public List<Integer> getNumbers() { return numbers; }
        public void setNumbers(List<Integer> numbers) { this.numbers = numbers; }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ComplexObject that = (ComplexObject) obj;
            return java.util.Objects.equals(name, that.name) &&
                   java.util.Objects.equals(items, that.items) &&
                   java.util.Objects.equals(numbers, that.numbers);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, items, numbers);
        }
        
        @Override
        public String toString() {
            return "ComplexObject{name='" + name + "', items=" + items + ", numbers=" + numbers + "}";
        }
    }
} 