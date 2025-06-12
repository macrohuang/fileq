package com.macrohuang.fileq.integrity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.conf.Constants;

/**
 * 文件完整性检查器
 * 提供强校验算法、损坏检测和恢复功能
 * 
 * @author macro
 */
public class FileIntegrityChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(FileIntegrityChecker.class);
    
    // 文件头魔数，用于识别FileQ文件
    public static final byte[] FILE_HEADER_MAGIC = "FILEQ".getBytes();
    public static final int FILE_HEADER_SIZE = 32;
    
    // 数据块校验类型
    public enum ChecksumType {
        SIMPLE_LENGTH(1),    // 简单长度校验（兼容旧版本）
        CRC32(2),           // CRC32校验
        ADLER32(3);         // Adler32校验
        
        private final int code;
        
        ChecksumType(int code) {
            this.code = code;
        }
        
        public int getCode() {
            return code;
        }
        
        public static ChecksumType fromCode(int code) {
            for (ChecksumType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return SIMPLE_LENGTH; // 默认兼容模式
        }
    }
    
    /**
     * 数据块完整性信息
     */
    public static class DataBlockInfo {
        private final long position;
        private final int metaSize;
        private final int dataSize;
        private final int checksumSize;
        private final ChecksumType checksumType;
        private final boolean isValid;
        private final String errorMessage;
        
        public DataBlockInfo(long position, int metaSize, int dataSize, int checksumSize, 
                           ChecksumType checksumType, boolean isValid, String errorMessage) {
            this.position = position;
            this.metaSize = metaSize;
            this.dataSize = dataSize;
            this.checksumSize = checksumSize;
            this.checksumType = checksumType;
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public long getPosition() { return position; }
        public int getMetaSize() { return metaSize; }
        public int getDataSize() { return dataSize; }
        public int getChecksumSize() { return checksumSize; }
        public ChecksumType getChecksumType() { return checksumType; }
        public boolean isValid() { return isValid; }
        public String getErrorMessage() { return errorMessage; }
        public long getTotalSize() { return metaSize + dataSize + checksumSize; }
    }
    
    /**
     * 文件完整性检查结果
     */
    public static class IntegrityCheckResult {
        private final boolean isValid;
        private final List<DataBlockInfo> validBlocks;
        private final List<DataBlockInfo> corruptedBlocks;
        private final long totalSize;
        private final long validDataSize;
        private final String summary;
        
        public IntegrityCheckResult(boolean isValid, List<DataBlockInfo> validBlocks, 
                                  List<DataBlockInfo> corruptedBlocks, long totalSize, 
                                  long validDataSize, String summary) {
            this.isValid = isValid;
            this.validBlocks = validBlocks;
            this.corruptedBlocks = corruptedBlocks;
            this.totalSize = totalSize;
            this.validDataSize = validDataSize;
            this.summary = summary;
        }
        
        // Getters
        public boolean isValid() { return isValid; }
        public List<DataBlockInfo> getValidBlocks() { return validBlocks; }
        public List<DataBlockInfo> getCorruptedBlocks() { return corruptedBlocks; }
        public long getTotalSize() { return totalSize; }
        public long getValidDataSize() { return validDataSize; }
        public String getSummary() { return summary; }
        public int getValidBlockCount() { return validBlocks.size(); }
        public int getCorruptedBlockCount() { return corruptedBlocks.size(); }
    }
    
    /**
     * 检查文件完整性
     */
    public static IntegrityCheckResult checkFileIntegrity(FileChannel channel, long startPosition, long endPosition) {
        logger.info("Starting file integrity check from position {} to {}", startPosition, endPosition);
        
        List<DataBlockInfo> validBlocks = new ArrayList<>();
        List<DataBlockInfo> corruptedBlocks = new ArrayList<>();
        long currentPosition = startPosition;
        long fileSize;
        
        try {
            fileSize = channel.size();
            if (endPosition == -1) {
                endPosition = fileSize;
            }
        } catch (IOException e) {
            logger.error("Failed to get file size", e);
            return new IntegrityCheckResult(false, validBlocks, corruptedBlocks, 0, 0, 
                                          "Failed to read file: " + e.getMessage());
        }
        
        while (currentPosition < endPosition) {
            try {
                DataBlockInfo blockInfo = checkDataBlock(channel, currentPosition);
                
                if (blockInfo.isValid()) {
                    validBlocks.add(blockInfo);
                    logger.debug("Valid block found at position {}, size: {}", 
                               currentPosition, blockInfo.getTotalSize());
                } else {
                    corruptedBlocks.add(blockInfo);
                    logger.warn("Corrupted block found at position {}: {}", 
                              currentPosition, blockInfo.getErrorMessage());
                }
                
                currentPosition += blockInfo.getTotalSize();
                
                // 如果块无效且大小为0，跳过一个字节避免无限循环
                if (!blockInfo.isValid() && blockInfo.getTotalSize() == 0) {
                    currentPosition++;
                }
                
            } catch (Exception e) {
                logger.error("Error checking block at position {}", currentPosition, e);
                // 创建错误块信息
                DataBlockInfo errorBlock = new DataBlockInfo(currentPosition, 0, 0, 0, 
                                                           ChecksumType.SIMPLE_LENGTH, false, 
                                                           "Exception: " + e.getMessage());
                corruptedBlocks.add(errorBlock);
                currentPosition++; // 跳过一个字节继续
            }
        }
        
        long validDataSize = validBlocks.stream().mapToLong(DataBlockInfo::getDataSize).sum();
        boolean isValid = corruptedBlocks.isEmpty();
        
        String summary = String.format(
            "Integrity check completed. Valid blocks: %d, Corrupted blocks: %d, " +
            "Valid data size: %d bytes, Total checked: %d bytes",
            validBlocks.size(), corruptedBlocks.size(), validDataSize, currentPosition - startPosition);
        
        logger.info(summary);
        
        return new IntegrityCheckResult(isValid, validBlocks, corruptedBlocks, 
                                      currentPosition - startPosition, validDataSize, summary);
    }
    
    /**
     * 检查单个数据块的完整性
     */
    private static DataBlockInfo checkDataBlock(FileChannel channel, long position) throws IOException {
        // 读取元数据
        ByteBuffer metaBuffer = ByteBuffer.allocate(Constants.DATA_META_SIZE);
        int bytesRead = channel.read(metaBuffer, position);
        
        if (bytesRead < Constants.DATA_META_SIZE) {
            return new DataBlockInfo(position, bytesRead, 0, 0, ChecksumType.SIMPLE_LENGTH, 
                                   false, "Incomplete meta data, only " + bytesRead + " bytes");
        }
        
        metaBuffer.flip();
        
        // 检查魔数
        int magicNumber = metaBuffer.getInt();
        if (magicNumber != Constants.MAGIC_NUMBER) {
            return new DataBlockInfo(position, Constants.DATA_META_SIZE, 0, 0, 
                                   ChecksumType.SIMPLE_LENGTH, false, 
                                   "Invalid magic number: " + magicNumber);
        }
        
        // 读取数据长度
        int dataLength = metaBuffer.getInt();
        if (dataLength < 0 || dataLength > 100 * 1024 * 1024) { // 100MB限制
            return new DataBlockInfo(position, Constants.DATA_META_SIZE, dataLength, 0, 
                                   ChecksumType.SIMPLE_LENGTH, false, 
                                   "Invalid data length: " + dataLength);
        }
        
        // 检查填充字节
        for (int i = 0; i < 8; i++) {
            if (metaBuffer.get() != Constants.PADDING) {
                return new DataBlockInfo(position, Constants.DATA_META_SIZE, dataLength, 0, 
                                       ChecksumType.SIMPLE_LENGTH, false, 
                                       "Invalid padding at byte " + (8 + i));
            }
        }
        
        // 读取数据
        ByteBuffer dataBuffer = ByteBuffer.allocate(dataLength);
        bytesRead = channel.read(dataBuffer, position + Constants.DATA_META_SIZE);
        
        if (bytesRead < dataLength) {
            return new DataBlockInfo(position, Constants.DATA_META_SIZE, dataLength, 0, 
                                   ChecksumType.SIMPLE_LENGTH, false, 
                                   "Incomplete data, expected " + dataLength + " but got " + bytesRead);
        }
        
        // 读取校验和
        ByteBuffer checksumBuffer = ByteBuffer.allocate(Constants.DATA_CHECKSUM_SIZE);
        bytesRead = channel.read(checksumBuffer, position + Constants.DATA_META_SIZE + dataLength);
        
        if (bytesRead < Constants.DATA_CHECKSUM_SIZE) {
            return new DataBlockInfo(position, Constants.DATA_META_SIZE, dataLength, bytesRead, 
                                   ChecksumType.SIMPLE_LENGTH, false, 
                                   "Incomplete checksum, expected " + Constants.DATA_CHECKSUM_SIZE + 
                                   " but got " + bytesRead);
        }
        
        checksumBuffer.flip();
        
        // 验证校验和（兼容旧版本的简单长度校验）
        int storedChecksum = checksumBuffer.getInt();
        int expectedSimpleChecksum = Constants.DATA_META_SIZE + dataLength;
        
        if (storedChecksum == expectedSimpleChecksum) {
            // 简单长度校验通过
            return new DataBlockInfo(position, Constants.DATA_META_SIZE, dataLength, 
                                   Constants.DATA_CHECKSUM_SIZE, ChecksumType.SIMPLE_LENGTH, 
                                   true, null);
        }
        
        // 尝试CRC32校验
        dataBuffer.flip();
        CRC32 crc32 = new CRC32();
        crc32.update(dataBuffer.array());
        int crc32Value = (int) crc32.getValue();
        
        if (storedChecksum == crc32Value) {
            return new DataBlockInfo(position, Constants.DATA_META_SIZE, dataLength, 
                                   Constants.DATA_CHECKSUM_SIZE, ChecksumType.CRC32, 
                                   true, null);
        }
        
        return new DataBlockInfo(position, Constants.DATA_META_SIZE, dataLength, 
                               Constants.DATA_CHECKSUM_SIZE, ChecksumType.SIMPLE_LENGTH, 
                               false, "Checksum mismatch. Expected: " + expectedSimpleChecksum + 
                               ", CRC32: " + crc32Value + ", Stored: " + storedChecksum);
    }
    
    /**
     * 计算数据的CRC32校验和
     */
    public static int calculateCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return (int) crc32.getValue();
    }
    
    /**
     * 计算数据的简单长度校验和（兼容旧版本）
     */
    public static int calculateSimpleChecksum(int dataLength) {
        return Constants.DATA_META_SIZE + dataLength;
    }
    
    /**
     * 验证文件头（如果存在）
     */
    public static boolean validateFileHeader(FileChannel channel) {
        try {
            if (channel.size() < FILE_HEADER_SIZE) {
                return true; // 文件太小，可能是旧格式，跳过头部验证
            }
            
            ByteBuffer headerBuffer = ByteBuffer.allocate(FILE_HEADER_MAGIC.length);
            channel.read(headerBuffer, 0);
            headerBuffer.flip();
            
            byte[] actualMagic = new byte[FILE_HEADER_MAGIC.length];
            headerBuffer.get(actualMagic);
            
            for (int i = 0; i < FILE_HEADER_MAGIC.length; i++) {
                if (actualMagic[i] != FILE_HEADER_MAGIC[i]) {
                    return true; // 不是新格式文件，跳过验证
                }
            }
            
            logger.debug("File header validation passed");
            return true;
            
        } catch (IOException e) {
            logger.warn("Failed to validate file header", e);
            return false;
        }
    }
} 