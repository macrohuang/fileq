package com.macrohuang.fileq.integrity;

import java.nio.ByteBuffer;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

import com.macrohuang.fileq.conf.Constants;
import com.macrohuang.fileq.conf.FileConstants;
import com.macrohuang.fileq.integrity.FileIntegrityChecker.ChecksumType;

/**
 * 增强的校验和工具类
 * 支持多种校验算法，提供向后兼容性
 * 
 * @author macro
 */
public class EnhancedChecksumUtil {
    
    /**
     * 校验和结果
     */
    public static class ChecksumResult {
        private final ChecksumType type;
        private final int value;
        private final byte[] bytes;
        
        public ChecksumResult(ChecksumType type, int value) {
            this.type = type;
            this.value = value;
            this.bytes = createChecksumBytes(type, value);
        }
        
        public ChecksumType getType() { return type; }
        public int getValue() { return value; }
        public byte[] getBytes() { return bytes; }
        
        private byte[] createChecksumBytes(ChecksumType type, int value) {
            byte[] result = new byte[Constants.DATA_CHECKSUM_SIZE];
            
            // 第一个字节存储校验类型
            result[0] = (byte) type.getCode();
            
            // 接下来4个字节存储校验值
            ByteBuffer buffer = ByteBuffer.allocate(FileConstants.INTEGER_BYTE_SIZE);
            buffer.putInt(value);
            System.arraycopy(buffer.array(), 0, result, 1, 4);
            
            // 剩余字节填充
            for (int i = 5; i < Constants.DATA_CHECKSUM_SIZE; i++) {
                result[i] = Constants.PADDING;
            }
            
            return result;
        }
    }
    
    /**
     * 计算数据的校验和
     * 
     * @param data 数据
     * @param type 校验类型
     * @return 校验和结果
     */
    public static ChecksumResult calculateChecksum(byte[] data, ChecksumType type) {
        switch (type) {
            case CRC32:
                return new ChecksumResult(type, calculateCRC32(data));
                
            case ADLER32:
                return new ChecksumResult(type, calculateAdler32(data));
                
            case SIMPLE_LENGTH:
            default:
                return new ChecksumResult(type, calculateSimpleChecksum(data.length));
        }
    }
    
    /**
     * 验证校验和
     * 
     * @param data 原始数据
     * @param checksumBytes 存储的校验和字节
     * @return 是否验证通过
     */
    public static boolean verifyChecksum(byte[] data, byte[] checksumBytes) {
        if (checksumBytes.length < FileConstants.ENHANCED_CHECKSUM_MIN_SIZE) {
            // 兼容旧格式：直接比较简单校验和
            return verifyLegacyChecksum(data, checksumBytes);
        }
        
        // 解析校验类型
        ChecksumType type = ChecksumType.fromCode(checksumBytes[0]);
        
        // 解析存储的校验值
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put(checksumBytes, 1, 4);
        buffer.flip();
        int storedValue = buffer.getInt();
        
        // 计算实际校验值
        ChecksumResult actualResult = calculateChecksum(data, type);
        
        return actualResult.getValue() == storedValue;
    }
    
    /**
     * 验证旧格式的校验和（向后兼容）
     */
    private static boolean verifyLegacyChecksum(byte[] data, byte[] checksumBytes) {
        if (checksumBytes.length < 4) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(checksumBytes);
        int storedValue = buffer.getInt();
        
        // 尝试简单长度校验
        int simpleChecksum = calculateSimpleChecksum(data.length);
        if (storedValue == simpleChecksum) {
            return true;
        }
        
        // 尝试CRC32校验（可能是旧版本使用的）
        int crc32Value = calculateCRC32(data);
        return storedValue == crc32Value;
    }
    
    /**
     * 计算CRC32校验和
     */
    public static int calculateCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return (int) crc32.getValue();
    }
    
    /**
     * 计算Adler32校验和
     */
    public static int calculateAdler32(byte[] data) {
        Adler32 adler32 = new Adler32();
        adler32.update(data);
        return (int) adler32.getValue();
    }
    
    /**
     * 计算简单长度校验和（兼容旧版本）
     */
    public static int calculateSimpleChecksum(int dataLength) {
        return Constants.DATA_META_SIZE + dataLength;
    }
    
    /**
     * 从校验和字节中解析校验类型
     */
    public static ChecksumType parseChecksumType(byte[] checksumBytes) {
        if (checksumBytes.length < 1) {
            return ChecksumType.SIMPLE_LENGTH;
        }
        
        return ChecksumType.fromCode(checksumBytes[0]);
    }
    
    /**
     * 从校验和字节中解析校验值
     */
    public static int parseChecksumValue(byte[] checksumBytes) {
        if (checksumBytes.length < FileConstants.ENHANCED_CHECKSUM_MIN_SIZE) {
            // 兼容旧格式
            if (checksumBytes.length >= 4) {
                ByteBuffer buffer = ByteBuffer.wrap(checksumBytes);
                return buffer.getInt();
            }
            return 0;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put(checksumBytes, 1, 4);
        buffer.flip();
        return buffer.getInt();
    }
    
    /**
     * 获取推荐的校验类型
     * 根据数据大小和性能要求选择最适合的校验算法
     */
    public static ChecksumType getRecommendedChecksumType(int dataSize) {
        if (dataSize < FileConstants.SIZE_1KB) {
            // 小数据使用CRC32，精度高
            return ChecksumType.CRC32;
        } else if (dataSize < FileConstants.SIZE_1MB) {
            // 中等数据使用Adler32，速度快
            return ChecksumType.ADLER32;
        } else {
            // 大数据使用简单校验，性能最好
            return ChecksumType.SIMPLE_LENGTH;
        }
    }
    
    /**
     * 比较两个校验和结果
     */
    public static boolean compareChecksums(ChecksumResult result1, ChecksumResult result2) {
        if (result1.getType() != result2.getType()) {
            return false;
        }
        
        return result1.getValue() == result2.getValue();
    }
    
    /**
     * 创建兼容旧版本的校验和字节
     */
    public static byte[] createLegacyChecksumBytes(int value) {
        byte[] result = new byte[Constants.DATA_CHECKSUM_SIZE];
        
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        System.arraycopy(buffer.array(), 0, result, 0, 4);
        
        // 剩余字节填充
        for (int i = 4; i < Constants.DATA_CHECKSUM_SIZE; i++) {
            result[i] = Constants.PADDING;
        }
        
        return result;
    }
} 