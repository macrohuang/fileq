package com.macrohuang.fileq.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.util.FileUtil;
import com.macrohuang.fileq.util.NumberBytesConvertUtil;

public abstract class AbstractFileQueueImpl<E> implements FileQueue<E> {
	private Config config;
	private final AtomicInteger objectCount;
	protected Codec<E> codec;
	private final AtomicLong writeNumber;
	protected AtomicLong writePosition;
	private final AtomicLong readNumber;
	protected AtomicLong readPosition;
	protected MappedByteBuffer writeMappedByteBuffer;
	protected static final int META_SIZE = 16;
	protected static final int CHECKSUM_SIZE = 16;
	protected static final int magic = 1314520;
	protected static final byte[] LEADING_HEAD = NumberBytesConvertUtil.int2ByteArr(magic);
	private MappedByteBuffer queueMetaBuffer;
	private RandomAccessFile readFile;
	private RandomAccessFile writeFile;
	protected FileChannel readChannel;
	protected FileChannel writeChannel;
	private FileChannel metaChannel;
	private RandomAccessFile metaAccessFile;
	private static final int SIZE_OF_QUEUE_META = 36;

	public AbstractFileQueueImpl(Config config) {
		codec = new KryoCodec<E>();
		objectCount = new AtomicInteger(0);
		writeNumber = new AtomicLong(0);
		readNumber = new AtomicLong(0);
		writePosition = new AtomicLong(0);
		readPosition = new AtomicLong(0);
		this.config = config;
		init();
	}

	private void init() {
		try {
			if (config.isInit()) {
				File basePath = new File(config.getBasePath());
				delete(basePath);
			}
			boolean isNew = FileUtil.isMetaExists(config);
			metaAccessFile = new RandomAccessFile(FileUtil.getMetaFile(config), "rw");
			metaChannel = metaAccessFile.getChannel();
			queueMetaBuffer = metaChannel.map(MapMode.READ_WRITE, 0, SIZE_OF_QUEUE_META);
			if (!isNew && !config.isInit()) {
				writeNumber.set(queueMetaBuffer.getLong());
				writePosition.set(queueMetaBuffer.getLong());
				readNumber.set(queueMetaBuffer.getLong());
				readPosition.set(queueMetaBuffer.getLong());
				objectCount.set(queueMetaBuffer.getInt());
			} else {
				queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
				queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
				queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
				queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
				queueMetaBuffer.put(NumberBytesConvertUtil.int2ByteArr(0));
			}
			writeFile = new RandomAccessFile(FileUtil.getDataFile(config, writeNumber.get()), "rw");
			writeChannel = writeFile.getChannel();
			writeMappedByteBuffer = writeChannel.map(MapMode.READ_WRITE, 0, config.getFileSize());

			readFile = new RandomAccessFile(FileUtil.getDataFile(config, readNumber.get()), "r");
			readChannel = readFile.getChannel();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Config getConfig() {
		return config;
	}

	public void setConfig(Config config) {
		this.config = config;
		init();
	}

	@Override
	public boolean remain() {
		return readPosition == writePosition;
	}

	@Override
	public int size() {
		return objectCount.get();
	}

	@Override
	public abstract void add(E e);

	protected abstract E peekInner(boolean remove, long timeout);

	@Override
	public E remove() {
		if (readPosition.get() == writePosition.get())
			return null;
		return peekInner(true, 0L);
	}

	@Override
	public E peek() {
		if (readPosition.get() == writePosition.get())
			return null;
		return peekInner(false, 0L);
	}

	@Override
	public void clear() {
		objectCount.getAndSet(0);
		readPosition.getAndSet(writePosition.get());
	}

	@Override
	public E take() throws InterruptedException {
		while (readNumber.get() >= writeNumber.get() && readPosition.get() >= writePosition.get()) {
			Thread.sleep(100);
		}
		return peekInner(true, 0L);
	}

	@Override
	public E take(long timeout, TimeUnit unit) throws InterruptedException {
		if (readNumber.get() == writeNumber.get() && readPosition.get() == writePosition.get()) {
			Thread.sleep(TimeUnit.MILLISECONDS.convert(timeout, unit));
		}
		if (readPosition.get() == writePosition.get()) {
			return null;
		}
		return peekInner(true, TimeUnit.MILLISECONDS.convert(timeout, unit));
	}

	@Override
	public void close() {
		try {
			metaAccessFile.close();
			metaChannel.close();
			readChannel.close();
			readFile.close();
			writeChannel.close();
			writeFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean delete(File file) {
		boolean success = true;
		if (file.isDirectory()) {
			for (File file2 : file.listFiles()) {
				success &= delete(file2);
			}
		}
		success &= file.delete();
		return success;
	}

	@Override
	public boolean delete() {
		close();
		return delete(new File(config.getBasePath()));
	}

	protected void increateWriteNumber() throws IOException {
		queueMetaBuffer.putLong(0, writeNumber.incrementAndGet());
		writeChannel.close();
		writeFile.close();
		writeFile = new RandomAccessFile(FileUtil.getDataFile(config, writeNumber.get()), "rw");
		writeChannel = writeFile.getChannel();
		writePosition.set(0L);
		writeMappedByteBuffer = writeChannel.map(MapMode.READ_WRITE, 0, config.getFileSize());
	}

	private boolean backupDataFile() {
		if (config.isBackup()) {
			try {
				FileOutputStream backupStream = new FileOutputStream(FileUtil.getBakFile(config, readNumber.get()));
				FileChannel targetChannel = backupStream.getChannel();
				readChannel.transferTo(0, readChannel.size(), targetChannel);
				backupStream.close();
				targetChannel.close();
			} catch (Exception e) {
				return false;
			}
		}
		return true;
	}

	protected void increateReadNumber() throws IOException {
		boolean backup = backupDataFile();
		File toDelFile = FileUtil.getDataFile(config, readNumber.get());
		queueMetaBuffer.putLong(16, readNumber.incrementAndGet());
		readChannel.close();
		readFile.close();
		readFile = new RandomAccessFile(FileUtil.getDataFile(config, readNumber.get()), "r");
		readChannel = readFile.getChannel();
		readPosition.set(0L);
		if (backup)
			toDelFile.delete();
	}

	protected final void updateWriteMeta() {
		queueMetaBuffer.position(8);
		queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(writePosition.get()));
		queueMetaBuffer.position(32);
		queueMetaBuffer.put(NumberBytesConvertUtil.int2ByteArr(objectCount.incrementAndGet()));
	}

	protected final void updateReadMeta() {
		queueMetaBuffer.putLong(24, readPosition.get());
		queueMetaBuffer.putInt(32, objectCount.decrementAndGet());
	}

	protected final int getFileSize() {
		return config.getFileSize();
	}
}
