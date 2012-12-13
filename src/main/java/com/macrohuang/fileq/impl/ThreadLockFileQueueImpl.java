package com.macrohuang.fileq.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.conf.Constants;
import com.macrohuang.fileq.exception.CheckSumFailException;
import com.macrohuang.fileq.util.NumberBytesConvertUtil;

/**
 * You should always keep this class Singleton in your application, or there will be something damage! To use multion, use {@link com.macrohuang.fileq.MultionFileQueueImpl} instead.
 * @author macro
 *
 * @param <E>
 */
public class ThreadLockFileQueueImpl<E> extends AbstractFileQueueImpl<E>
		implements FileQueue<E> {
	private final ReentrantLock writeLock = new ReentrantLock();
	private final ReentrantLock readLock = new ReentrantLock();


	public ThreadLockFileQueueImpl(Config config) {
		super(config);
	}

	 @Override
	public void add(E e) {
		byte[] objBytes = codec.encode(e);
		byte[] metaBytes = new byte[Constants.DATA_META_SIZE];
		Arrays.fill(metaBytes, Constants.PADDING);
		System.arraycopy(Constants.LEADING_HEAD, 0, metaBytes, 0, 4);
		System.arraycopy(NumberBytesConvertUtil.int2ByteArr(objBytes.length), 0, metaBytes, 4, 4);
		byte[] checkSum = new byte[Constants.DATA_CHECKSUM_SIZE];
		Arrays.fill(checkSum, Constants.PADDING);
		System.arraycopy(NumberBytesConvertUtil.int2ByteArr(Constants.DATA_META_SIZE + objBytes.length), 0, checkSum, 0,
				NumberBytesConvertUtil.int2ByteArr(Constants.DATA_META_SIZE + objBytes.length).length);
		long size = metaBytes.length + objBytes.length + checkSum.length;
		try {
			writeLock.lock();
			// Current object exceed the file size, expand it first.
			if (writeMappedByteBuffer.position() + size > writeMappedByteBuffer.capacity()) {
				writeMappedByteBuffer = writeChannel.map(MapMode.READ_WRITE, writeMappedByteBuffer.position(), size);
			}
			writeMappedByteBuffer.put(metaBytes);
			writeMappedByteBuffer.put(objBytes);
			writeMappedByteBuffer.put(checkSum);
			if (writePosition.addAndGet(size) >= getFileSize()) {
				increateWriteNumber();
			}
			updateWriteMeta();
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			writeLock.unlock();
		}
	}

	private boolean checkMeta(ByteBuffer meta) {
		if (meta.position() != 0)
			meta.flip();
		boolean pass = (meta.getInt() == Constants.MAGIC_NUMBER && meta.getInt() > -1 && meta.get() == Constants.PADDING
				&& meta.get() == Constants.PADDING && meta.get() == Constants.PADDING && meta.get() == Constants.PADDING);
		meta.flip();
		return pass;
	}

	private ByteBuffer getMetaBuffer(long timeout) throws IOException, InterruptedException {
		long position = readPosition.get();
		ByteBuffer metaBuffer = ByteBuffer.allocate(Constants.DATA_META_SIZE);
		readChannel.read(metaBuffer, position);
		int retry = 0;
		// data error,maybe the data havn't flush to the disk, try some time, if
		// still error, then skip
		while (!checkMeta(metaBuffer) && retry < Constants.MAX_RETRY) {
			Thread.sleep(10);
			retry++;
		}
		if (retry >= Constants.MAX_RETRY) {
			if (!readLock.isHeldByCurrentThread())
				readLock.lock();
			position = readPosition.incrementAndGet();
			boolean success = false;
			while (!success) {
				if (position >= writePosition.get()) {
					if (timeout > 0) {
						Thread.sleep(timeout);
						if (position >= writePosition.get()) {
							return null;
						}
					} else {
						while (position >= writePosition.get()) {
							Thread.sleep(100);
						}
					}
				}
				if (position >= readChannel.size()) {
					increateReadNumber();
				}
				metaBuffer.clear();
				readChannel.read(metaBuffer, position);
				retry = 0;
				// data error,maybe the data havn't flush to the disk, try some
				// time, if
				// still error, then skip
				while (!checkMeta(metaBuffer) && retry < Constants.MAX_RETRY) {
					Thread.sleep(10);
					retry++;
				}
				if (retry < Constants.MAX_RETRY) {
					success = true;
				} else {
					position = readPosition.incrementAndGet();
				}
			}
		}
		metaBuffer.getInt();
		return metaBuffer;
	}

	@SuppressWarnings("unchecked")
	private E readObject(int objLen) throws IOException {
		ByteBuffer objBuffer = ByteBuffer.allocate(objLen);
		for (int i = 0; i < Constants.MAX_RETRY; i++) {
			try {
				readChannel.read(objBuffer, readPosition.get() + Constants.DATA_META_SIZE);
				return (E) codec.decode(objBuffer.array());
			} catch (Exception e) {
			}
			objBuffer.clear();
		}
		return null;
	}

	private void checksum(int objLength) throws IOException, CheckSumFailException {
		ByteBuffer checksumBuffer = ByteBuffer.allocate(Constants.DATA_CHECKSUM_SIZE);
		for (int i = 0; i < Constants.MAX_RETRY; i++) {
			try {
				readChannel.read(checksumBuffer, readPosition.get() + Constants.DATA_META_SIZE + objLength);
				checksumBuffer.flip();
				if (checksumBuffer.getInt() == Constants.DATA_META_SIZE + objLength)
					return;
			} catch (Exception e) {
			}
			checksumBuffer.clear();
		}
		throw new CheckSumFailException();
	}

	private void updateReadInfo(boolean update, int objLength) throws IOException {
		if (update) {
			readPosition.addAndGet(Constants.DATA_META_SIZE + objLength + Constants.DATA_CHECKSUM_SIZE);
			if (readPosition.get() >= readChannel.size()) {
				increateReadNumber();
			}
			updateReadMeta();
		}
	}
	@Override
	protected E peekInner(boolean remove, long timeout) {
        try {
        	if (remove){
        		readLock.lock();
        	}
			ByteBuffer metaBuffer = getMetaBuffer(timeout);
			int objLength = metaBuffer.getInt();
			E obj = readObject(objLength);
			checksum(objLength);
			updateReadInfo(remove, objLength);
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        	if (readLock.isHeldByCurrentThread())
        		readLock.unlock();
        }
        return null;
    }
}
