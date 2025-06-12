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
public class DefaultObjectCodec implements Codec {
	// private static final Logger log =
	// LoggerFactory.getLogger(DefaultObjectCodec.class);
	private final Class<?> type;

	public DefaultObjectCodec(Class<?> type) {
		this.type = type;
	}

	@Override
	public byte[] encode(Object element) {
		try (var bos = new ByteArrayOutputStream();
			 var oos = new ObjectOutputStream(bos)) {
			oos.writeObject(element);
			return bos.toByteArray();
		} catch (IOException e) {
			// log.warn("Encode object({}) fail", element);
			return new byte[0];
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T decode(byte[] bytes) {
		try (var bis = new ByteArrayInputStream(bytes);
			 var ois = new ObjectInputStream(bis)) {
			return (T) ois.readObject();
		} catch (Exception e) {
			// log.warn("Decode object({}) fail", Arrays.toString(bytes));
			return null;
		}
	}
}
