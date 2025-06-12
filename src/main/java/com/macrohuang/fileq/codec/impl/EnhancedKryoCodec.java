package com.macrohuang.fileq.codec.impl;

import java.util.Arrays;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.macrohuang.fileq.codec.Codec;

/**
 * 增强版Kryo序列化编解码器
 * 专门解决Arrays.asList()序列化问题
 * 
 * @author macro
 */
public class EnhancedKryoCodec implements Codec {
    
    private final ThreadLocal<Kryo> serializer = ThreadLocal.withInitial(() -> {
        var kryo = new Kryo();
        
        // 方案1: 允许未注册的类（当前KryoCodec的方案）
        kryo.setRegistrationRequired(false);
        
        // 方案2: 显式注册Arrays$ArrayList类
        try {
            Class<?> arraysListClass = Class.forName("java.util.Arrays$ArrayList");
            kryo.register(arraysListClass, new ArraysListSerializer());
        } catch (ClassNotFoundException e) {
            // 如果找不到类，使用默认处理
            System.err.println("Warning: Could not register Arrays$ArrayList class: " + e.getMessage());
        }
        
        return kryo;
    });
    
    private final ThreadLocal<Output> output = ThreadLocal.withInitial(() -> 
        new Output(1024, -1));
    
    private final ThreadLocal<Input> input = ThreadLocal.withInitial(Input::new);
    
    private Class<?> typeClass;

    @Override
    public byte[] encode(Object element) {
        if (typeClass == null) {
            typeClass = element.getClass();
        }
        var kryo = serializer.get();
        var output = this.output.get();
        
        output.reset();
        kryo.writeClassAndObject(output, element);
        return output.toBytes();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(byte[] bytes) {
        var kryo = serializer.get();
        var input = this.input.get();
        
        input.setBuffer(bytes);
        return (T) kryo.readClassAndObject(input);
    }
    
    /**
     * Arrays.asList()的自定义序列化器
     * 将Arrays$ArrayList转换为普通的ArrayList进行序列化
     */
    private static class ArraysListSerializer extends Serializer<List<?>> {
        
        @Override
        public void write(Kryo kryo, Output output, List<?> list) {
            // 将Arrays$ArrayList转换为ArrayList
            java.util.ArrayList<?> arrayList = new java.util.ArrayList<>(list);
            kryo.writeObject(output, arrayList);
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public List<?> read(Kryo kryo, Input input, Class<? extends List<?>> type) {
            // 读取为ArrayList，然后转换为Arrays.asList()的形式
            java.util.ArrayList<?> arrayList = kryo.readObject(input, java.util.ArrayList.class);
            // 注意：这里返回ArrayList而不是Arrays$ArrayList，但功能相同
            return arrayList;
        }
    }
} 