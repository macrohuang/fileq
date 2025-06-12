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
    
    private void addInternal(E e) throws Exception {
        checkNotClosed();
        if (e == null) {
            throw new IllegalArgumentException("Cannot add null element to queue");
        }
        
        logger.debug("Adding element to queue");
        
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
            Thread.sleep(10);
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
                            Thread.sleep(100);
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
                    Thread.sleep(10);
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
                Thread.sleep(10);
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
                Thread.sleep(10);
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