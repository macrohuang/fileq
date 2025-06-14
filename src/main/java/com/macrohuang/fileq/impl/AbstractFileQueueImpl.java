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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.conf.Constants;
import com.macrohuang.fileq.exception.FileQueueIOException;
import com.macrohuang.fileq.exception.InsufficientSpaceException;
import com.macrohuang.fileq.util.FileUtil;
import com.macrohuang.fileq.util.NumberBytesConvertUtil;
import com.macrohuang.fileq.util.ResourceManager;
import com.macrohuang.fileq.conf.TimeConstants;
import com.macrohuang.fileq.util.SleepUtil;

public abstract class AbstractFileQueueImpl<E> implements FileQueue<E> {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractFileQueueImpl.class);
	
	private Config config;
	private final AtomicInteger objectCount;
	protected Codec codec;
	private final AtomicLong writeNumber;
	protected AtomicLong writePosition;
	private final AtomicLong readNumber;
	protected AtomicLong readPosition;
	protected MappedByteBuffer writeMappedByteBuffer;
	// protected static final int META_SIZE = 16;
	// protected static final int CHECKSUM_SIZE = 16;
	// protected static final int magic = 1314520;
	// protected static final byte[] LEADING_HEAD =
	// NumberBytesConvertUtil.int2ByteArr(magic);
	private MappedByteBuffer queueMetaBuffer;

	private RandomAccessFile readFile;
	private RandomAccessFile writeFile;
	protected FileChannel readChannel;
	protected FileChannel writeChannel;
	private FileChannel metaChannel;
	private RandomAccessFile metaAccessFile;

	// 资源关闭状态标记
	private volatile boolean closed = false;

	// private static final int SIZE_OF_QUEUE_META = 46;
	
	public enum MetaOffset {
		WriteNumberName(0), WriteNumber(2), WritePositionName(10), WritePosition(12), ReadNumberName(20), ReadNumber(22), ReadPositionName(30), ReadPosition(
				32), ObjectCountName(40), ObjectCount(42);
		private MetaOffset(int offset){
			this.offset = offset;
		}
		public int getOffset() {
			return offset;
		}
		public void setOffset(int offset) {
			this.offset = offset;
		}

		private int offset;
	}

	public AbstractFileQueueImpl(Config config) {
		codec = config.getCodec() == null ? new KryoCodec() : config.getCodec();
		objectCount = new AtomicInteger(0);
		writeNumber = new AtomicLong(0);
		readNumber = new AtomicLong(0);
		writePosition = new AtomicLong(0);
		readPosition = new AtomicLong(0);
		this.config = config;
		init();
	}

	/**
	 * Initializes the FileQueue with proper error handling and resource cleanup.
	 */
	private void init() {
		logger.info("Initializing FileQueue with base path: {}", config.getBasePath());
		
		try {
			performPreInitChecks();
			handleCleanupIfRequested();
			
			boolean isNew = FileUtil.isMetaExists(config);
			initializeQueueComponents(isNew);
			
			logger.info("FileQueue initialized successfully. New queue: {}", isNew);
			
		} catch (IOException e) {
			handleInitializationError("Failed to initialize FileQueue", e);
		} catch (Exception e) {
			handleInitializationError("Unexpected error during initialization", new IOException(e));
		}
	}
	
	/**
	 * Performs pre-initialization checks including disk space validation.
	 */
	private void performPreInitChecks() {
		// Disk space pre-check: estimate 2x file size for safety (data + backup)
		long requiredSpace = config.getFileSize() * 2L;
		long availableSpace = ResourceManager.getAvailableDiskSpace(config.getBasePath());
		if (availableSpace > 0 && availableSpace < requiredSpace) {
			// Only throw if we can reliably determine insufficient space
			throw new InsufficientSpaceException(requiredSpace, availableSpace);
		}
	}
	
	/**
	 * Handles cleanup of existing files if clean initialization is requested.
	 */
	private void handleCleanupIfRequested() {
		if (config.isInit()) {
			File basePath = new File(config.getBasePath());
			FileUtil.delete(basePath);  // Remove all existing queue files
			logger.info("Cleaned up existing queue files at: {}", config.getBasePath());
		}
	}
	
	/**
	 * Initializes the core queue components in proper order.
	 */
	private void initializeQueueComponents(boolean isNew) throws IOException {
		// Phase 1: Initialize metadata file and queue state
		initMetaFile(isNew);
		
		// Phase 2: Initialize data files and channels
		initDataFiles();
	}
	
	/**
	 * Handles initialization errors with proper cleanup and exception wrapping.
	 */
	private void handleInitializationError(String message, IOException cause) {
		logger.error("{} at path: {}", message, config.getBasePath(), cause);
		cleanup();  // Clean up any partially created resources
		throw new FileQueueIOException(message, cause);
	}
	
	private void initMetaFile(boolean isNew) throws IOException {
		try {
			// Create or open the metadata file with read/write access
			metaAccessFile = new RandomAccessFile(FileUtil.getMetaFile(config), "rw");
			metaChannel = metaAccessFile.getChannel();
			// Map the entire 46-byte metadata structure into memory for fast access
			queueMetaBuffer = metaChannel.map(MapMode.READ_WRITE, 0, Constants.QUEUE_META_SIZE);
			
			if (!isNew && !config.isInit()) {
				// Recovery path: restore queue state from existing metadata
				loadQueueState();
				logger.debug("Loaded existing queue state - writeNumber: {}, readNumber: {}, objectCount: {}", 
						   writeNumber.get(), readNumber.get(), objectCount.get());
			} else {
				// Fresh start: initialize new queue with default values
				initializeNewQueue();
				logger.debug("Initialized new queue state");
			}
		} catch (IOException e) {
			logger.error("Failed to initialize meta file", e);
			ResourceManager.safeClose(metaChannel, "metaChannel");
			ResourceManager.safeClose(metaAccessFile, "metaAccessFile");
			throw e;
		}
	}
	
	private void loadQueueState() {
		writeNumber.set(queueMetaBuffer.getLong(MetaOffset.WriteNumber.offset));
		writePosition.set(queueMetaBuffer.getLong(MetaOffset.WritePosition.offset));
		readNumber.set(queueMetaBuffer.getLong(MetaOffset.ReadNumber.offset));
		readPosition.set(queueMetaBuffer.getLong(MetaOffset.ReadPosition.offset));
		objectCount.set(queueMetaBuffer.getInt(MetaOffset.ObjectCount.offset));
	}
	
	private void initializeNewQueue() {
		queueMetaBuffer.put("WN".getBytes());
		queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
		queueMetaBuffer.put("WP".getBytes());
		queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
		queueMetaBuffer.put("RN".getBytes());
		queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
		queueMetaBuffer.put("RP".getBytes());
		queueMetaBuffer.put(NumberBytesConvertUtil.long2ByteArr(0L));
		queueMetaBuffer.put("OC".getBytes());
		queueMetaBuffer.put(NumberBytesConvertUtil.int2ByteArr(0));
	}
	
	private void initDataFiles() throws IOException {
		try {
			// Initialize write file and channel for queue additions
			writeFile = new RandomAccessFile(FileUtil.getDataFile(config, writeNumber.get()), "rw");
			writeChannel = writeFile.getChannel();
			// Memory-map the entire file for high-performance writes
			writeMappedByteBuffer = writeChannel.map(MapMode.READ_WRITE, 0, config.getFileSize());
			// Position buffer at last write position for continuous writing
			writeMappedByteBuffer.position(Long.valueOf(writePosition.get()).intValue());
			
			// Initialize read file and channel for queue consumption (read-only)
			readFile = new RandomAccessFile(FileUtil.getDataFile(config, readNumber.get()), "r");
			readChannel = readFile.getChannel();
			
		} catch (IOException e) {
			logger.error("Failed to initialize data files", e);
			// 清理已创建的文件资源
			ResourceManager.safeClose(writeChannel, "writeChannel");
			ResourceManager.safeClose(writeFile, "writeFile");
			ResourceManager.safeClose(readChannel, "readChannel");
			ResourceManager.safeClose(readFile, "readFile");
			throw e;
		}
	}
	
	private void cleanup() {
		logger.debug("Cleaning up FileQueue resources");
		ResourceManager.safeCloseAll(metaAccessFile, metaChannel, readChannel, readFile, writeChannel, writeFile);
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
		return readPosition.get() != writePosition.get();
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
		checkNotClosed();
		if (objectCount.get() == 0)
			return null;
		return peekInner(true, 0L);
	}

	@Override
	public E peek() {
		checkNotClosed();
		if (objectCount.get() == 0)
			return null;
		return peekInner(false, 0L);
	}

	@Override
	public E peek(long timeout, TimeUnit timeUnit) throws InterruptedException {
		checkNotClosed();
		if (objectCount.get() == 0) {
			if (timeout > 0) {
				Thread.sleep(TimeUnit.MILLISECONDS.convert(timeout, timeUnit));
			} else {
				while (objectCount.get() == 0 && !closed) {
					SleepUtil.queueWait();
				}
			}
		}
		if (objectCount.get() == 0 || closed) {
			return null;
		}
		return peekInner(false, TimeUnit.MILLISECONDS.convert(timeout, timeUnit));
	}

	@Override
	public void clear() {
		checkNotClosed();
		// 获取当前写位置，然后原子性地更新读位置和对象计数
		long currentWritePosition = writePosition.get();
		readPosition.set(currentWritePosition);
		objectCount.set(0);
		logger.debug("Queue cleared, reset to write position: {}", currentWritePosition);
	}

	@Override
	public E take() throws InterruptedException {
		checkNotClosed();
		while (objectCount.get() == 0 && !closed) {
			SleepUtil.queueWait();
		}
		if (closed) {
			return null;
		}
		return peekInner(true, 0L);
	}

	@Override
	public E take(long timeout, TimeUnit unit) throws InterruptedException {
		checkNotClosed();
		if (objectCount.get() == 0) {
			unit.sleep(timeout);
		}
		if (objectCount.get() == 0 || closed) {
			return null;
		}
		return peekInner(true, TimeUnit.MILLISECONDS.convert(timeout, unit));
	}

	@Override
	public void close() {
		if (closed) {
			logger.debug("FileQueue already closed");
			return;
		}
		
		logger.info("Closing FileQueue");
		closed = true;
		
		// 强制刷新元数据
		try {
			if (queueMetaBuffer != null) {
				queueMetaBuffer.force();
			}
		} catch (Exception e) {
			logger.warn("Failed to force meta buffer", e);
		}
		
		// 安全关闭所有资源
		ResourceManager.safeClose(metaAccessFile, "metaAccessFile");
		ResourceManager.safeClose(metaChannel, "metaChannel");
		ResourceManager.safeClose(readChannel, "readChannel");
		ResourceManager.safeClose(readFile, "readFile");
		ResourceManager.safeClose(writeChannel, "writeChannel");
		ResourceManager.safeClose(writeFile, "writeFile");
		
		logger.info("FileQueue closed successfully");
	}

	@Override
	public boolean delete() {
		close();
		boolean result = FileUtil.delete(new File(config.getBasePath()));
		if (result) {
			logger.info("FileQueue files deleted successfully");
		} else {
			logger.warn("Failed to delete some FileQueue files");
		}
		return result;
	}
	
	protected void checkNotClosed() {
		if (closed) {
			throw new IllegalStateException("FileQueue has been closed");
		}
	}

	protected void increateWriteNumber() throws IOException {
		logger.debug("Increasing write number from {} to {}", writeNumber.get(), writeNumber.get() + 1);
		
		try {
			// Step 1: Update metadata with new file number
			queueMetaBuffer.putLong(MetaOffset.WriteNumber.offset, writeNumber.incrementAndGet());
			
			// Step 2: Clean shutdown of current write file resources
			ResourceManager.safeClose(writeChannel, "writeChannel");
			ResourceManager.safeClose(writeFile, "writeFile");
			
			// Step 3: Create and initialize new write file
			writeFile = new RandomAccessFile(FileUtil.getDataFile(config, writeNumber.get()), "rw");
			writeChannel = writeFile.getChannel();
			// Reset position to beginning of new file
			writePosition.set(0L);
			queueMetaBuffer.putLong(MetaOffset.WritePosition.offset, writePosition.get());
			// Map new file into memory for fast access
			writeMappedByteBuffer = writeChannel.map(MapMode.READ_WRITE, 0, config.getFileSize());
			
			logger.debug("Created new write file for number: {}", writeNumber.get());
			
		} catch (IOException e) {
			logger.error("Failed to increase write number", e);
			throw new FileQueueIOException("Failed to create new write file", e);
		}
	}

	private boolean backupDataFile() {
		if (!config.isBackup()) {
			return true;
		}
		
		logger.debug("Backing up data file for read number: {}", readNumber.get());
		
		FileOutputStream backupStream = null;
		FileChannel targetChannel = null;
		
		try {
			backupStream = new FileOutputStream(FileUtil.getBakFile(config, readNumber.get()));
			targetChannel = backupStream.getChannel();
			readChannel.transferTo(0, readChannel.size(), targetChannel);
			
			logger.debug("Successfully backed up data file");
			return true;
			
		} catch (Exception e) {
			logger.error("Failed to backup data file for read number: {}", readNumber.get(), e);
			return false;
		} finally {
			ResourceManager.safeClose(targetChannel, "backupTargetChannel");
			ResourceManager.safeClose(backupStream, "backupStream");
		}
	}

	protected void increateReadNumber() throws IOException {
		logger.debug("Increasing read number from {} to {}", readNumber.get(), readNumber.get() + 1);
		
		// Step 1: Backup current file before deletion (if enabled)
		boolean backup = backupDataFile();
		File toDelFile = FileUtil.getDataFile(config, readNumber.get());
		
		try {
			// Step 2: Update metadata with new read file number
			queueMetaBuffer.putLong(MetaOffset.ReadNumber.offset, readNumber.incrementAndGet());
			
			// Step 3: Clean shutdown of current read file resources
			ResourceManager.safeClose(readChannel, "readChannel");
			ResourceManager.safeClose(readFile, "readFile");
			
			// Step 4: Open next data file for reading
			readFile = new RandomAccessFile(FileUtil.getDataFile(config, readNumber.get()), "r");
			readChannel = readFile.getChannel();
			// Reset read position to beginning of new file
			readPosition.set(0L);
			queueMetaBuffer.putLong(MetaOffset.ReadPosition.offset, readPosition.get());
			
			// Step 5: Cleanup old file if backup was successful
			if (backup && toDelFile.exists()) {
				if (toDelFile.delete()) {
					logger.debug("Deleted old data file: {}", toDelFile.getName());
				} else {
					logger.warn("Failed to delete old data file: {}", toDelFile.getName());
				}
			}
			
			logger.debug("Created new read file for number: {}", readNumber.get());
			
		} catch (IOException e) {
			logger.error("Failed to increase read number", e);
			throw new FileQueueIOException("Failed to create new read file", e);
		}
	}

	protected final void updateWriteMeta() {
		// 先更新计数器，然后更新元数据文件
		int newCount = objectCount.incrementAndGet();
		queueMetaBuffer.putLong(MetaOffset.WritePosition.offset, writePosition.get());
		queueMetaBuffer.putInt(MetaOffset.ObjectCount.offset, newCount);
	}

	protected final void updateReadMeta() {
		// 先更新计数器，然后更新元数据文件
		int newCount = objectCount.decrementAndGet();
		queueMetaBuffer.putLong(MetaOffset.ReadPosition.offset, readPosition.get());
		queueMetaBuffer.putInt(MetaOffset.ObjectCount.offset, newCount);
	}

	protected final int getFileSize() {
		return config.getFileSize();
	}
}
