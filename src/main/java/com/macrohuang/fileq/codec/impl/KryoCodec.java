package com.macrohuang.fileq.codec.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.conf.FileConstants;

/**
 * Kryo序列化编解码器
 * @author macro
 */
public class KryoCodec implements Codec {
	private final ThreadLocal<Kryo> serializer = ThreadLocal.withInitial(() -> {
		var kryo = new Kryo();
		kryo.setRegistrationRequired(false);
		return kryo;
	});
	
	private final ThreadLocal<Output> output = ThreadLocal.withInitial(() -> 
		new Output(FileConstants.DEFAULT_KRYO_BUFFER_SIZE, FileConstants.UNLIMITED_BUFFER));
	
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
}
