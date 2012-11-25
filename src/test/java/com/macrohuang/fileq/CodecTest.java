package com.macrohuang.fileq;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.codec.impl.DefaultJavaObjectCodec;

public class CodecTest {
    private int MAX_OBJS = 10000;
	private int MAX_THREADS = 20;
	private int OBJS_PER_THREAD = MAX_OBJS / MAX_THREADS;
	private byte[][] objectBytes;
	private ExecutorService executors = Executors.newFixedThreadPool(MAX_THREADS);

	@Before
	public void init() {
		objectBytes = new byte[MAX_OBJS][];
	}

	private void encode(Codec<MyObject> codec) {
		for (int i = 0; i < MAX_OBJS; i++) {
			objectBytes[i] = codec.encode(new MyObject());
		}
	}

	private void decode(Codec<MyObject> codec) {
		for (int i = 0; i < MAX_OBJS; i++) {
			codec.decode(objectBytes[i]);
		}
	}

	private void encodeMultThread(final Codec<MyObject> codec) throws InterruptedException {
		for (int i = 0; i < MAX_THREADS; i++) {
			final int k = i;
			executors.submit(new Runnable() {
				public void run() {
					for (int j = 0; j < OBJS_PER_THREAD; j++) {
						objectBytes[k * OBJS_PER_THREAD + j] = codec.encode(new MyObject());
					}
				}
			});
		}
		executors.shutdown();
		executors.awaitTermination(100, TimeUnit.SECONDS);
	}

	private void decodeMultThread(final Codec<MyObject> codec) throws InterruptedException {
		for (int i = 0; i < MAX_THREADS; i++) {
			final int k = i;
			executors.submit(new Runnable() {
				public void run() {
					for (int j = 0; j < OBJS_PER_THREAD; j++) {
						codec.decode(objectBytes[k * OBJS_PER_THREAD + j]);
					}
				}
			});
		}
		executors.shutdown();
		executors.awaitTermination(100, TimeUnit.SECONDS);
	}

	@Test
	public void testDefaultSerializeEncode() {
		encode(new DefaultJavaObjectCodec<MyObject>());
	}

	@Test
	public void testDefaultSerializeDecode() {
		Codec<MyObject> codec = new DefaultJavaObjectCodec<MyObject>();
		encode(codec);
		decode(codec);
	}

	@Test
	public void testDefaultSerializeEncodeMultThreads() throws InterruptedException {
		encodeMultThread(new DefaultJavaObjectCodec<MyObject>());
	}

	@Test
	public void testDefaultSerializeDecodeMultThreads() throws InterruptedException {
		final Codec<MyObject> codec = new DefaultJavaObjectCodec<MyObject>();
		encode(codec);
		decodeMultThread(codec);
	}

	@Test
	public void testKryoSerializeEncode() {
		encode(new KryoCodec<MyObject>());
	}

	@Test
	public void testKryoSerializeDecode() {
		Codec<MyObject> codec = new KryoCodec<MyObject>();
		encode(codec);
		decode(codec);
	}

	@Test
	public void testKryoSerializeEncodeMultThreads() throws InterruptedException {
		encodeMultThread(new KryoCodec<MyObject>());
	}

	@Test
	public void testKryoSerializeDecodeMultThreads() throws InterruptedException {
		final Codec<MyObject> codec = new KryoCodec<MyObject>();
		encode(codec);
		decodeMultThread(codec);
	}

	public void clean() {
		objectBytes = null;
	}
}
