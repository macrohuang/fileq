package com.macrohuang.fileq.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.conf.Constants;
import com.macrohuang.fileq.concurrent.ConcurrencyStrategy;
import com.macrohuang.fileq.concurrent.ConcurrencyStrategyFactory;
import com.macrohuang.fileq.concurrent.LockStatistics;
import com.macrohuang.fileq.exception.CheckSumFailException;
import com.macrohuang.fileq.exception.FileQueueIOException;
import com.macrohuang.fileq.util.NumberBytesConvertUtil;
import com.macrohuang.fileq.conf.TimeConstants;
import com.macrohuang.fileq.util.SleepUtil;

/**
 * 增强的FileQueue实现
 * 集成了新的并发策略系统，支持多种并发访问模式
 * 
 * @author macro
 * @param <E> 队列元素类型
 */
public class EnhancedFileQueueImpl<E> extends AbstractFileQueueImpl<E> implements FileQueue<E> {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedFileQueueImpl.class);
    
    private final ConcurrencyStrategy concurrencyStrategy;
    
    public EnhancedFileQueueImpl(Config config) {
        super(config);
        
        // 根据配置创建并发策略
        if (config.getConcurrencyMode() != null) {
            this.concurrencyStrategy = ConcurrencyStrategyFactory.createStrategy(
                config.getConcurrencyMode(), config.isFairLock());
        } else {
            // 根据预期线程数推荐策略
            this.concurrencyStrategy = ConcurrencyStrategyFactory.createRecommendedStrategy(
                config.getExpectedReadThreads(), 
                config.getExpectedWriteThreads(), 
                config.isMultiProcessAccess(),
                config.isFairLock());
        }
        
        logger.info("EnhancedFileQueueImpl initialized with concurrency strategy: {}", 
                   concurrencyStrategy.getAccessMode());
    }
    
    @Override
    public void add(E e) {
        concurrencyStrategy.executeWrite(() -> {
            addInternal(e);
            return null;
        });
    }
    
    /**
     * Internal method to add an element to the queue.
     * Handles serialization, buffer management, and file rotation.
     */
    private void addInternal(E e) throws Exception {
        checkNotClosed();
        if (e == null) {
            throw new IllegalArgumentException("Cannot add null element to queue");
        }
        
        logger.debug("Adding element to queue");
        
        // Prepare data for writing
        QueueDataPacket dataPacket = prepareDataPacket(e);
        
        // Ensure buffer capacity and write data
        ensureWriteBufferCapacity(dataPacket.getTotalSize());
        writeDataPacket(dataPacket);
        
        // Update queue state
        updateQueueAfterWrite(dataPacket.getTotalSize());
        
        logger.debug("Successfully added element to queue, new size: {}", size());
    }
    
    /**
     * Prepares a complete data packet for writing to the queue.
     * Includes serialization, metadata creation, and checksum generation.
     */
    private QueueDataPacket prepareDataPacket(E element) throws Exception {
        // Serialize the object using configured codec
        byte[] objBytes = codec.encode(element);
        
        // Create metadata header (16 bytes: magic number + object length + padding)
        byte[] metaBytes = createMetadataHeader(objBytes.length);
        
        // Create checksum footer (16 bytes: total size + padding)
        byte[] checkSum = createChecksumFooter(objBytes.length);
        
        return new QueueDataPacket(metaBytes, objBytes, checkSum);
    }
    
    /**
     * Creates the metadata header for a queue entry.
     */
    private byte[] createMetadataHeader(int objectLength) {
        byte[] metaBytes = new byte[Constants.DATA_META_SIZE];
        Arrays.fill(metaBytes, Constants.PADDING);  // Initialize with padding
        System.arraycopy(Constants.LEADING_HEAD, 0, metaBytes, 0, 4);  // Magic number
        System.arraycopy(NumberBytesConvertUtil.int2ByteArr(objectLength), 0, metaBytes, 4, 4);  // Object length
        return metaBytes;
    }
    
    /**
     * Creates the checksum footer for integrity verification.
     */
    private byte[] createChecksumFooter(int objectLength) {
        byte[] checkSum = new byte[Constants.DATA_CHECKSUM_SIZE];
        Arrays.fill(checkSum, Constants.PADDING);
        // Store total data size (meta + object) for integrity verification
        byte[] sizeBytes = NumberBytesConvertUtil.int2ByteArr(Constants.DATA_META_SIZE + objectLength);
        System.arraycopy(sizeBytes, 0, checkSum, 0, sizeBytes.length);
        return checkSum;
    }
    
    /**
     * Ensures the write buffer has sufficient capacity for the data.
     */
    private void ensureWriteBufferCapacity(long requiredSize) throws IOException {
        if (writeMappedByteBuffer.position() + requiredSize > writeMappedByteBuffer.capacity()) {
            logger.debug("Expanding write buffer for size: {}", requiredSize);
            // Remap with current position as starting point and required size
            writeMappedByteBuffer = writeChannel.map(MapMode.READ_WRITE, writeMappedByteBuffer.position(), requiredSize);
        }
    }
    
    /**
     * Writes the complete data packet to the buffer.
     */
    private void writeDataPacket(QueueDataPacket dataPacket) {
        writeMappedByteBuffer.put(dataPacket.getMetaBytes());     // Write metadata header
        writeMappedByteBuffer.put(dataPacket.getObjectBytes());   // Write serialized object
        writeMappedByteBuffer.put(dataPacket.getChecksumBytes()); // Write checksum footer
    }
    
    /**
     * Updates queue metadata after a successful write operation.
     */
    private void updateQueueAfterWrite(long dataSize) throws IOException {
        // Update position and check if file rotation is needed
        if (writePosition.addAndGet(dataSize) >= getFileSize()) {
            increateWriteNumber();  // Rotate to new file when size limit reached
        }
        updateWriteMeta();  // Update queue metadata (count, position)
    }
    
    /**
     * Data structure to hold a complete queue entry ready for writing.
     */
    private static class QueueDataPacket {
        private final byte[] metaBytes;
        private final byte[] objectBytes;
        private final byte[] checksumBytes;
        private final long totalSize;
        
        public QueueDataPacket(byte[] metaBytes, byte[] objectBytes, byte[] checksumBytes) {
            this.metaBytes = metaBytes;
            this.objectBytes = objectBytes;
            this.checksumBytes = checksumBytes;
            this.totalSize = metaBytes.length + objectBytes.length + checksumBytes.length;
        }
        
        public byte[] getMetaBytes() { return metaBytes; }
        public byte[] getObjectBytes() { return objectBytes; }
        public byte[] getChecksumBytes() { return checksumBytes; }
        public long getTotalSize() { return totalSize; }
    }
    
    @Override
    protected E peekInner(boolean remove, long timeout) {
        return concurrencyStrategy.executeRead(() -> {
            return peekInternalImpl(remove, timeout);
        });
    }
    
    @SuppressWarnings("unchecked")
    private E peekInternalImpl(boolean remove, long timeout) throws Exception {
        logger.debug("Peeking element, remove: {}, timeout: {}", remove, timeout);
        
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
    }
    
    /**
     * Validates the metadata header format and content.
     * Checks magic number, object length, and padding bytes.
     */
    private boolean checkMeta(ByteBuffer meta) {
        // Ensure buffer is ready for reading from beginning
        if (meta.position() != 0)
            meta.flip();
        
        // Validate metadata structure:
        // - First 4 bytes: magic number for format validation
        // - Next 4 bytes: object length (must be non-negative)
        // - Remaining 8 bytes: padding (should all be PADDING value)
        boolean pass = (meta.getInt() == Constants.MAGIC_NUMBER && 
                       meta.getInt() > -1 && 
                       meta.get() == Constants.PADDING &&
                       meta.get() == Constants.PADDING && 
                       meta.get() == Constants.PADDING && 
                       meta.get() == Constants.PADDING);
        meta.flip();  // Reset for next read
        return pass;
    }

    /**
     * Retrieves a valid metadata buffer from the current read position.
     * Handles retry logic and error recovery for corrupted metadata.
     */
    private ByteBuffer getMetaBuffer(long timeout) throws IOException, InterruptedException {
        long position = readPosition.get();
        ByteBuffer metaBuffer = readMetaBufferAtPosition(position);
        
        // Try to read and validate metadata with retry logic
        if (retryMetaRead(metaBuffer, position)) {
            metaBuffer.getInt(); // Skip magic number
            return metaBuffer;
        }
        
        // Initial read failed, attempt recovery
        metaBuffer = recoverFromFailedMeta(timeout);
        if (metaBuffer != null) {
            metaBuffer.getInt(); // Skip magic number
        }
        return metaBuffer;
    }
    
    /**
     * Reads metadata buffer at the specified position.
     */
    private ByteBuffer readMetaBufferAtPosition(long position) throws IOException {
        ByteBuffer metaBuffer = ByteBuffer.allocate(Constants.DATA_META_SIZE);
        try {
            readChannel.read(metaBuffer, position);
            return metaBuffer;
        } catch (IOException e) {
            logger.error("Failed to read meta buffer at position: {}", position, e);
            throw e;
        }
    }
    
    /**
     * Attempts to read and validate metadata with retry logic.
     * @return true if successful, false if max retries exceeded
     */
    private boolean retryMetaRead(ByteBuffer metaBuffer, long position) throws InterruptedException {
        int retry = 0;
        
        // Handle potential race condition: data may not be flushed to disk yet
        while (!checkMeta(metaBuffer) && retry < Constants.MAX_RETRY) {
            SleepUtil.adaptiveWait(retry);  // Adaptive wait with exponential backoff
            retry++;
            metaBuffer.clear();
            
            try {
                readChannel.read(metaBuffer, position);
            } catch (IOException e) {
                logger.warn("Retry {} failed to read meta buffer at position: {}", retry, position, e);
            }
        }
        
        if (retry >= Constants.MAX_RETRY) {
            logger.warn("Meta validation failed after {} retries, will attempt recovery", Constants.MAX_RETRY);
            return false;
        }
        
        return true;
    }
    
    /**
     * Recovers from failed metadata read by advancing position and retrying.
     */
    private ByteBuffer recoverFromFailedMeta(long timeout) throws IOException, InterruptedException {
        long position = readPosition.incrementAndGet();
        logger.debug("Starting recovery from position: {}", position);
        
        while (true) {
            // Wait for data to become available if we've caught up to write position
            if (!waitForDataAvailable(position, timeout)) {
                return null; // Timeout occurred
            }
            
            // Check if we need to rotate to next file
            if (position >= readChannel.size()) {
                logger.debug("Reached end of read channel, increasing read number");
                increateReadNumber();
                position = readPosition.get(); // Reset to beginning of new file
            }
            
            // Try to read metadata at current position
            ByteBuffer metaBuffer = readMetaBufferAtPosition(position);
            
            // Attempt validation with retry
            if (retryMetaRead(metaBuffer, position)) {
                logger.debug("Successfully recovered metadata at position: {}", position);
                return metaBuffer;
            }
            
            // Recovery failed at this position, try next
            logger.warn("Recovery failed at position: {}, moving to next", position);
            position = readPosition.incrementAndGet();
        }
    }
    
    /**
     * Waits for data to become available at the specified position.
     * @return true if data became available, false if timeout occurred
     */
    private boolean waitForDataAvailable(long position, long timeout) throws InterruptedException {
        if (position < writePosition.get()) {
            return true; // Data already available
        }
        
        if (timeout > 0) {
            // Wait with timeout
            Thread.sleep(timeout);
            if (position >= writePosition.get()) {
                logger.debug("Timeout waiting for data at position: {}", position);
                return false;
            }
        } else {
            // Wait indefinitely
            while (position >= writePosition.get()) {
                SleepUtil.queueWait();
            }
        }
        
        return true;
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
                SleepUtil.adaptiveWait(i + 1);
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
                SleepUtil.adaptiveWait(i + 1);
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
    public void close() {
        // 先关闭并发策略
        if (concurrencyStrategy != null) {
            concurrencyStrategy.close();
        }
        
        // 然后关闭父类资源
        super.close();
    }
    
    /**
     * 获取并发策略
     */
    public ConcurrencyStrategy getConcurrencyStrategy() {
        return concurrencyStrategy;
    }
    
    /**
     * 获取锁统计信息
     */
    public LockStatistics getLockStatistics() {
        return concurrencyStrategy.getLockStatistics();
    }
    
    /**
     * 获取并发访问模式
     */
    public ConcurrencyStrategy.AccessMode getConcurrencyMode() {
        return concurrencyStrategy.getAccessMode();
    }
} 