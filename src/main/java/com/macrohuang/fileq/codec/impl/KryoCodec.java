package com.macrohuang.fileq.codec.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.macrohuang.fileq.codec.Codec;

/**
 * 
 * @author macro
 * 
 * @param <E>
 */
public class KryoCodec<T> implements Codec<T> {
	private ThreadLocal<Kryo> serializer = new ThreadLocal<Kryo>();
	private ThreadLocal<Output> output = new ThreadLocal<Output>();
	private ThreadLocal<Input> input = new ThreadLocal<Input>();
	private Class<?> typeClass;
	public byte[] encode(T element) {
		if (typeClass==null){//guess the type class.
			typeClass = element.getClass();
		}
		Kryo kryo = serializer.get();
		Output output = this.output.get();
		if (kryo == null) {
			kryo = new Kryo();
			kryo.setRegistrationRequired(false);
			serializer.set(kryo);
		}
		if (output == null) {
			output = new Output(1024, -1);
			this.output.set(output);
		}
		output.clear();
//		kryo.writeClassAndObject(output, element);
		kryo.writeObject(output, element);
        return output.toBytes();
    }

	@SuppressWarnings("unchecked")
	public T decode(byte[] bytes) {
		Kryo kryo = serializer.get();
		Input input = this.input.get();
		if (kryo == null) {
			kryo = new Kryo();
			kryo.setRegistrationRequired(false);
			serializer.set(kryo);
		}
		if (input == null) {
			input = new Input();
			this.input.set(input);
		}
        input.setBuffer(bytes);
//		return (T) kryo.readClassAndObject(input);
		return (T) kryo.readObject(input, typeClass);
    }

	@Override
	public void registType(Class<?> typeClass) {
		this.typeClass = typeClass;
	}
}
