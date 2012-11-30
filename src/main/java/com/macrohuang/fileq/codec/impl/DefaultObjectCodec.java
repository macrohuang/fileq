package com.macrohuang.fileq.codec.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.macrohuang.fileq.codec.Codec;

/**
 * 
 * @author Leo Liang
 * @author macro
 * 
 */
public class DefaultObjectCodec<T> implements Codec<T> {
	// private static final Logger log =
	// LoggerFactory.getLogger(DefaultObjectCodec.class);
	private Class<?> type;
    @Override
	public byte[] encode(T element) {
    	if (type ==null){
    		this.type = element.getClass();
    	}
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(element);
        } catch (IOException e) {
			// log.warn("Encode object({}) fail", element);
            return new byte[0];
        }
        return bos.toByteArray();
    }

    @Override
	@SuppressWarnings("unchecked")
	public T decode(byte[] bytes) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (T) ois.readObject();
        } catch (Exception e) {
			// log.warn("Decode object({}) fail", Arrays.toString(bytes));
            return null;
        }
    }
}
