package com.macrohuang.fileq.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.conf.Constants;
import com.macrohuang.fileq.exception.CheckSumFailException;
import com.macrohuang.fileq.exception.FileQueueIOException;
import com.macrohuang.fileq.util.NumberBytesConvertUtil;
import com.macrohuang.fileq.conf.TimeConstants;

/**
 * Thread-safe FileQueue implementation using ReentrantLock
 * 
 * You should always keep this class Singleton in your application, or there will be something damage! 
 * To use multiple instances, use {@link com.macrohuang.fileq.MultionFileQueueImpl} instead.
 * 
 * @author macro
 * @param <E> the type of elements held in this queue
 */
public class ThreadLockFileQueueImpl<E> extends AbstractFileQueueImpl<E>
		implements FileQueue<E> {
	
	private static final Logger logger = LoggerFactory.getLogger(ThreadLockFileQueueImpl.class);
	
	private final ReentrantLock writeLock = new ReentrantLock();
	private final ReentrantLock readLock = new ReentrantLock();

	public ThreadLockFileQueueImpl(Config config) {
		super(config);
	}

	@Override
	public void add(E e) {
		checkNotClosed();
		if (e == null) {
			throw new IllegalArgumentException("Cannot add null element to queue");
		}
		
		logger.debug("Adding element to queue");
		
		byte[] objBytes;
		try {
			objBytes = codec.encode(e);
		} catch (Exception ex) {
			logger.error("Failed to encode object", ex);
			throw new FileQueueIOException("Failed to encode object for queue", new IOException(ex));
		}
		
		byte[] metaBytes = new byte[Constants.DATA_META_SIZE];
		Arrays.fill(metaBytes, Constants.PADDING);
		System.arraycopy(Constants.LEADING_HEAD, 0, metaBytes, 0, 4);
		System.arraycopy(NumberBytesConvertUtil.int2ByteArr(objBytes.length), 0, metaBytes, 4, 4);
		
		byte[] checkSum = new byte[Constants.DATA_CHECKSUM_SIZE];
		Arrays.fill(checkSum, Constants.PADDING);
		System.arraycopy(NumberBytesConvertUtil.int2ByteArr(Constants.DATA_META_SIZE + objBytes.length), 0, checkSum, 0,
				NumberBytesConvertUtil.int2ByteArr(Constants.DATA_META_SIZE + objBytes.length).length);
		
		long size = metaBytes.length + objBytes.length + checkSum.length;
		
		writeLock.lock();
		try {
			// Check if current object exceeds the file size, expand it first
			if (writeMappedByteBuffer.position() + size > writeMappedByteBuffer.capacity()) {
				logger.debug("Expanding write buffer for size: {}", size);
				writeMappedByteBuffer = writeChannel.map(MapMode.READ_WRITE, writeMappedByteBuffer.position(), size);
			}
			
			writeMappedByteBuffer.put(metaBytes);
			writeMappedByteBuffer.put(objBytes);
			writeMappedByteBuffer.put(checkSum);
			
			if (writePosition.addAndGet(size) >= getFileSize()) {
				increateWriteNumber();
			}
			updateWriteMeta();
			
			logger.debug("Successfully added element to queue, new size: {}", size());
			
		} catch (IOException e1) {
			logger.error("Failed to add element to queue", e1);
			throw new FileQueueIOException("Failed to write element to queue", e1);
		} catch (Exception e1) {
			logger.error("Unexpected error while adding element to queue", e1);
			throw new FileQueueIOException("Unexpected error during queue write", new IOException(e1));
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
		
		try {
			readChannel.read(metaBuffer, position);
		} catch (IOException e) {
			logger.error("Failed to read meta buffer at position: {}", position, e);
			throw e;
		}
		
		int retry = 0;
		// Data error, maybe the data hasn't flushed to the disk, try some time, if still error, then skip
		while (!checkMeta(metaBuffer) && retry < Constants.MAX_RETRY) {
			Thread.sleep(TimeConstants.RETRY_INTERVAL_MS);
			retry++;
			metaBuffer.clear();
			try {
				readChannel.read(metaBuffer, position);
			} catch (IOException e) {
				logger.warn("Retry {} failed to read meta buffer at position: {}", retry, position, e);
			}
		}
		
		if (retry >= Constants.MAX_RETRY) {
			logger.warn("Meta validation failed after {} retries, skipping to next position", Constants.MAX_RETRY);
			
			if (!readLock.isHeldByCurrentThread())
				readLock.lock();
			position = readPosition.incrementAndGet();
			boolean success = false;
			
			while (!success) {
				if (position >= writePosition.get()) {
					if (timeout > 0) {
						Thread.sleep(timeout);
						if (position >= writePosition.get()) {
							logger.debug("Timeout waiting for data at position: {}", position);
							return null;
						}
					} else {
						while (position >= writePosition.get()) {
							Thread.sleep(TimeConstants.QUEUE_WAIT_INTERVAL_MS);
						}
					}
				}
				
				if (position >= readChannel.size()) {
					logger.debug("Reached end of read channel, increasing read number");
					increateReadNumber();
				}
				
				metaBuffer.clear();
				try {
					readChannel.read(metaBuffer, position);
				} catch (IOException e) {
					logger.error("Failed to read meta buffer during recovery at position: {}", position, e);
					throw e;
				}
				
				retry = 0;
				// Data error, maybe the data hasn't flushed to the disk, try some time, if still error, then skip
				while (!checkMeta(metaBuffer) && retry < Constants.MAX_RETRY) {
					Thread.sleep(TimeConstants.RETRY_INTERVAL_MS);
					retry++;
					metaBuffer.clear();
					try {
						readChannel.read(metaBuffer, position);
					} catch (IOException e) {
						logger.warn("Recovery retry {} failed at position: {}", retry, position, e);
					}
				}
				
				if (retry < Constants.MAX_RETRY) {
					success = true;
				} else {
					logger.warn("Recovery failed at position: {}, moving to next", position);
					position = readPosition.incrementAndGet();
				}
			}
		}
		
		metaBuffer.getInt(); // Skip magic number
		return metaBuffer;
	}

	@SuppressWarnings("unchecked")
	private E readObject(int objLen) throws IOException, InterruptedException {
		if (objLen <= 0) {
			logger.warn("Invalid object length: {}", objLen);
			return null;
		}
		
		ByteBuffer objBuffer = ByteBuffer.allocate(objLen);
		
		for (int i = 0; i < Constants.MAX_RETRY; i++) {
			try {
				readChannel.read(objBuffer, readPosition.get() + Constants.DATA_META_SIZE);
				return (E) codec.decode(objBuffer.array());
			} catch (Exception e) {
				logger.warn("Failed to read/decode object, retry {}/{}: {}", i + 1, Constants.MAX_RETRY, e.getMessage());
				if (i == Constants.MAX_RETRY - 1) {
					logger.error("Failed to read object after {} retries", Constants.MAX_RETRY, e);
				}
				Thread.sleep(TimeConstants.RETRY_INTERVAL_MS);
			}
			objBuffer.clear();
		}
		
		logger.error("Failed to read object after all retries");
		return null;
	}

	private void checksum(int objLength) throws IOException, CheckSumFailException, InterruptedException {
		ByteBuffer checksumBuffer = ByteBuffer.allocate(Constants.DATA_CHECKSUM_SIZE);
		
		for (int i = 0; i < Constants.MAX_RETRY; i++) {
			try {
				readChannel.read(checksumBuffer, readPosition.get() + Constants.DATA_META_SIZE + objLength);
				checksumBuffer.flip();
				if (checksumBuffer.getInt() == Constants.DATA_META_SIZE + objLength) {
					logger.debug("Checksum validation passed");
					return;
				}
				logger.warn("Checksum mismatch, retry {}/{}", i + 1, Constants.MAX_RETRY);
			} catch (Exception e) {
				logger.warn("Failed to read checksum, retry {}/{}: {}", i + 1, Constants.MAX_RETRY, e.getMessage());
				Thread.sleep(TimeConstants.RETRY_INTERVAL_MS);
			}
			checksumBuffer.clear();
		}
		
		logger.error("Checksum validation failed after {} retries", Constants.MAX_RETRY);
		throw new CheckSumFailException();
	}

	private void updateReadInfo(boolean update, int objLength) throws IOException {
		if (update) {
			readPosition.addAndGet(Constants.DATA_META_SIZE + objLength + Constants.DATA_CHECKSUM_SIZE);
			if (readPosition.get() >= readChannel.size()) {
				logger.debug("Read position reached channel size, increasing read number");
				increateReadNumber();
			}
			updateReadMeta();
		}
	}
	
	@Override
	protected E peekInner(boolean remove, long timeout) {
		logger.debug("Peeking element, remove: {}, timeout: {}", remove, timeout);
		
		try {
			if (remove) {
				readLock.lock();
			}
			
			ByteBuffer metaBuffer = getMetaBuffer(timeout);
			if (metaBuffer == null) {
				logger.debug("No meta buffer available, returning null");
				return null; // 超时或没有数据可读
			}
			
			int objLength = metaBuffer.getInt();
			if (objLength <= 0) {
				logger.warn("Invalid object length in meta buffer: {}", objLength);
				return null;
			}
			
			E obj = readObject(objLength);
			if (obj == null) {
				logger.warn("Failed to read object with length: {}", objLength);
				return null;
			}
			
			checksum(objLength);
			updateReadInfo(remove, objLength);
			
			logger.debug("Successfully {} element", remove ? "removed" : "peeked");
			return obj;
			
		} catch (CheckSumFailException e) {
			logger.error("Checksum validation failed", e);
			return null;
		} catch (IOException e) {
			logger.error("IO error during peek operation", e);
			throw new FileQueueIOException("IO error during queue read", e);
		} catch (InterruptedException e) {
			logger.warn("Peek operation interrupted", e);
			Thread.currentThread().interrupt(); // Restore interrupted status
			return null;
		} catch (Exception e) {
			logger.error("Unexpected error during peek operation", e);
			throw new FileQueueIOException("Unexpected error during queue read", new IOException(e));
		} finally {
			if (readLock.isHeldByCurrentThread()) {
				readLock.unlock();
			}
		}
	}
}
