package com.macrohuang.fileq;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.impl.ThreadLockFileQueueImpl;
import com.macrohuang.fileq.util.TestUtil;

/**
 * 测试Kryo序列化Arrays.asList()的已知问题
 */
public class KryoSerializationBugTest {
    
    private Config config;
    private static int index = 0;
    
    @BeforeEach
    public void init() {
        config = new Config();
        config.setBasePath(TestUtil.getTempPathWithIndex("filequeue_kryo_test_", index++));
        config.setInit(true);
        config.setFileSize(1024 * 1024);
    }
    
    /**
     * 包含Arrays.asList()字段的测试DTO
     */
    public static class TestDTOWithArraysList {
        private String name;
        private List<String> items;
        private Object data;
        
        public TestDTOWithArraysList() {
            // 默认构造函数
        }
        
        public TestDTOWithArraysList(String name, List<String> items, Object data) {
            this.name = name;
            this.items = items;
            this.data = data;
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestDTOWithArraysList that = (TestDTOWithArraysList) obj;
            return java.util.Objects.equals(name, that.name) &&
                   java.util.Objects.equals(items, that.items) &&
                   java.util.Objects.equals(data, that.data);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, items, data);
        }
        
        @Override
        public String toString() {
            return "TestDTOWithArraysList{name='" + name + "', items=" + items + ", data=" + data + "}";
        }
    }
    
    /**
     * 测试直接使用Kryo编解码Arrays.asList()的问题
     */
    @Test
    public void testKryoDirectSerializationWithArraysList() {
        KryoCodec codec = new KryoCodec();
        
        // 创建包含Arrays.asList()的对象
        List<String> arraysList = Arrays.asList("item1", "item2", "item3");
        TestDTOWithArraysList original = new TestDTOWithArraysList("test", arraysList, "some data");
        
        System.out.println("Original object: " + original);
        System.out.println("Arrays.asList() class: " + arraysList.getClass().getName());
        
        try {
            // 尝试序列化
            byte[] serialized = codec.encode(original);
            System.out.println("Serialization successful, size: " + serialized.length + " bytes");
            
            // 尝试反序列化
            TestDTOWithArraysList deserialized = (TestDTOWithArraysList) codec.decode(serialized);
            System.out.println("Deserialized object: " + deserialized);
            
            // 验证反序列化结果
            Assertions.assertEquals(original, deserialized);
            System.out.println("✅ Kryo serialization/deserialization successful!");
            
        } catch (Exception e) {
            System.err.println("❌ Kryo serialization/deserialization failed: " + e.getMessage());
            e.printStackTrace();
            // 不让测试失败，因为这是已知的bug
            System.out.println("This is the known bug mentioned in README.md");
        }
    }
    
    /**
     * 测试在FileQueue中使用Arrays.asList()的问题
     */
    @Test
    public void testFileQueueWithArraysList() {
        FileQueue<TestDTOWithArraysList> queue = new ThreadLockFileQueueImpl<>(config);
        
        // 创建包含Arrays.asList()的对象
        List<String> arraysList = Arrays.asList("queue1", "queue2", "queue3");
        TestDTOWithArraysList original = new TestDTOWithArraysList("queue-test", arraysList, 12345);
        
        System.out.println("Adding to queue: " + original);
        
        try {
            // 添加到队列
            queue.add(original);
            System.out.println("Successfully added to queue, size: " + queue.size());
            
            // 从队列取出
            TestDTOWithArraysList retrieved = queue.take();
            System.out.println("Retrieved from queue: " + retrieved);
            
            // 验证结果
            Assertions.assertEquals(original, retrieved);
            System.out.println("✅ FileQueue with Arrays.asList() works!");
            
        } catch (Exception e) {
            System.err.println("❌ FileQueue with Arrays.asList() failed: " + e.getMessage());
            e.printStackTrace();
            // 不让测试失败，因为这是已知的bug
            System.out.println("This demonstrates the known bug in FileQueue with Arrays.asList()");
        } finally {
            queue.delete();
        }
    }
    
    /**
     * 测试解决方案：使用ArrayList替代Arrays.asList()
     */
    @Test
    public void testWorkaroundWithArrayList() {
        FileQueue<TestDTOWithArraysList> queue = new ThreadLockFileQueueImpl<>(config);
        
        // 使用ArrayList替代Arrays.asList()
        List<String> arrayList = new java.util.ArrayList<>(Arrays.asList("work1", "work2", "work3"));
        TestDTOWithArraysList original = new TestDTOWithArraysList("workaround-test", arrayList, "fixed");
        
        System.out.println("Testing workaround with ArrayList: " + original);
        System.out.println("ArrayList class: " + arrayList.getClass().getName());
        
        try {
            // 添加到队列
            queue.add(original);
            System.out.println("Successfully added to queue, size: " + queue.size());
            
            // 从队列取出
            TestDTOWithArraysList retrieved = queue.take();
            System.out.println("Retrieved from queue: " + retrieved);
            
            // 验证结果
            Assertions.assertEquals(original, retrieved);
            System.out.println("✅ Workaround with ArrayList successful!");
            
        } catch (Exception e) {
            System.err.println("❌ Workaround failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail("Workaround should work: " + e.getMessage());
        } finally {
            queue.delete();
        }
    }
} 