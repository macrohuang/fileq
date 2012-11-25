package com.macrohuang.fileq.codec;

/**
 * @author Leo Liang
 * 
 */
public interface Codec<T> {

    byte[] encode(T element);

    T decode(byte[] bytes);
    
    void registType(Class<?> typeClass);
}
