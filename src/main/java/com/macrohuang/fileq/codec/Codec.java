package com.macrohuang.fileq.codec;

/**
 * @author Leo Liang
 * 
 */
public interface Codec {

    byte[] encode(Object element);

	<T> T decode(byte[] bytes);
}
