package com.macrohuang.fileq.integrity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.integrity.FileIntegrityChecker.DataBlockInfo;
import com.macrohuang.fileq.integrity.FileIntegrityChecker.IntegrityCheckResult;

/**
 * 文件恢复管理器
 * 提供损坏文件的恢复和修复功能
 * 
 * @author macro
 */
public class FileRecoveryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(FileRecoveryManager.class);
    
    /**
     * 恢复策略
     */
    public enum RecoveryStrategy {
        /**
         * 跳过损坏的数据块，保留有效数据
         */
        SKIP_CORRUPTED,
        
        /**
         * 截断文件到最后一个有效数据块
         */
        TRUNCATE_TO_LAST_VALID,
        
        /**
         * 尝试修复损坏的数据块
         */
        ATTEMPT_REPAIR,
        
        /**
         * 创建备份后进行恢复
         */
        BACKUP_AND_RECOVER
    }
    
    /**
     * 恢复结果
     */
    public static class RecoveryResult {
        private final boolean success;
        private final int recoveredBlocks;
        private final int skippedBlocks;
        private final long recoveredDataSize;
        private final String backupPath;
        private final String message;
        
        public RecoveryResult(boolean success, int recoveredBlocks, int skippedBlocks, 
                            long recoveredDataSize, String backupPath, String message) {
            this.success = success;
            this.recoveredBlocks = recoveredBlocks;
            this.skippedBlocks = skippedBlocks;
            this.recoveredDataSize = recoveredDataSize;
            this.backupPath = backupPath;
            this.message = message;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public int getRecoveredBlocks() { return recoveredBlocks; }
        public int getSkippedBlocks() { return skippedBlocks; }
        public long getRecoveredDataSize() { return recoveredDataSize; }
        public String getBackupPath() { return backupPath; }
        public String getMessage() { return message; }
    }
    
    /**
     * 恢复损坏的文件
     * 
     * @param sourceChannel 源文件通道
     * @param targetPath 目标文件路径
     * @param checkResult 完整性检查结果
     * @param strategy 恢复策略
     * @return 恢复结果
     */
    public static RecoveryResult recoverFile(FileChannel sourceChannel, String targetPath, 
                                           IntegrityCheckResult checkResult, RecoveryStrategy strategy) {
        logger.info("Starting file recovery with strategy: {}", strategy);
        
        try {
            String backupPath = null;
            
            // 如果需要备份
            if (strategy == RecoveryStrategy.BACKUP_AND_RECOVER) {
                backupPath = createBackup(targetPath);
                logger.info("Created backup at: {}", backupPath);
            }
            
            switch (strategy) {
                case SKIP_CORRUPTED:
                case BACKUP_AND_RECOVER:
                    return recoverBySkippingCorrupted(sourceChannel, targetPath, checkResult, backupPath);
                    
                case TRUNCATE_TO_LAST_VALID:
                    return recoverByTruncating(sourceChannel, targetPath, checkResult, backupPath);
                    
                case ATTEMPT_REPAIR:
                    return recoverByRepairing(sourceChannel, targetPath, checkResult, backupPath);
                    
                default:
                    return new RecoveryResult(false, 0, 0, 0, backupPath, 
                                            "Unknown recovery strategy: " + strategy);
            }
            
        } catch (Exception e) {
            logger.error("File recovery failed", e);
            return new RecoveryResult(false, 0, 0, 0, null, 
                                    "Recovery failed: " + e.getMessage());
        }
    }
    
    /**
     * 通过跳过损坏数据块来恢复文件
     */
    private static RecoveryResult recoverBySkippingCorrupted(FileChannel sourceChannel, String targetPath, 
                                                           IntegrityCheckResult checkResult, String backupPath) 
            throws IOException {
        
        List<DataBlockInfo> validBlocks = checkResult.getValidBlocks();
        if (validBlocks.isEmpty()) {
            return new RecoveryResult(false, 0, checkResult.getCorruptedBlockCount(), 0, backupPath,
                                    "No valid blocks found to recover");
        }
        
        Path target = Paths.get(targetPath);
        
        try (FileChannel targetChannel = FileChannel.open(target, 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.WRITE, 
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            
            long totalRecoveredSize = 0;
            int recoveredBlocks = 0;
            
            for (DataBlockInfo block : validBlocks) {
                // 复制有效的数据块
                long blockSize = block.getTotalSize();
                ByteBuffer buffer = ByteBuffer.allocate((int) blockSize);
                
                sourceChannel.read(buffer, block.getPosition());
                buffer.flip();
                
                targetChannel.write(buffer);
                totalRecoveredSize += blockSize;
                recoveredBlocks++;
                
                logger.debug("Recovered block at position {}, size: {}", 
                           block.getPosition(), blockSize);
            }
            
            targetChannel.force(true);
            
            String message = String.format(
                "Successfully recovered %d blocks (%d bytes), skipped %d corrupted blocks",
                recoveredBlocks, totalRecoveredSize, checkResult.getCorruptedBlockCount());
            
            logger.info(message);
            
            return new RecoveryResult(true, recoveredBlocks, checkResult.getCorruptedBlockCount(), 
                                    totalRecoveredSize, backupPath, message);
        }
    }
    
    /**
     * 通过截断到最后一个有效数据块来恢复文件
     */
    private static RecoveryResult recoverByTruncating(FileChannel sourceChannel, String targetPath, 
                                                    IntegrityCheckResult checkResult, String backupPath) 
            throws IOException {
        
        List<DataBlockInfo> validBlocks = checkResult.getValidBlocks();
        if (validBlocks.isEmpty()) {
            return new RecoveryResult(false, 0, checkResult.getCorruptedBlockCount(), 0, backupPath,
                                    "No valid blocks found to recover");
        }
        
        // 找到最后一个有效块
        DataBlockInfo lastValidBlock = validBlocks.get(validBlocks.size() - 1);
        long truncatePosition = lastValidBlock.getPosition() + lastValidBlock.getTotalSize();
        
        Path target = Paths.get(targetPath);
        
        try (FileChannel targetChannel = FileChannel.open(target, 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.WRITE, 
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            
            // 复制从开始到最后一个有效块的所有数据
            ByteBuffer buffer = ByteBuffer.allocate((int) truncatePosition);
            sourceChannel.read(buffer, 0);
            buffer.flip();
            
            targetChannel.write(buffer);
            targetChannel.force(true);
            
            String message = String.format(
                "Successfully truncated file to position %d, recovered %d blocks (%d bytes)",
                truncatePosition, validBlocks.size(), truncatePosition);
            
            logger.info(message);
            
            return new RecoveryResult(true, validBlocks.size(), checkResult.getCorruptedBlockCount(), 
                                    truncatePosition, backupPath, message);
        }
    }
    
    /**
     * 尝试修复损坏的数据块
     */
    private static RecoveryResult recoverByRepairing(FileChannel sourceChannel, String targetPath, 
                                                   IntegrityCheckResult checkResult, String backupPath) 
            throws IOException {
        
        // 目前实现简单的修复策略：跳过损坏块，保留有效块
        // 未来可以实现更复杂的修复算法，如错误纠正码等
        logger.info("Attempting to repair corrupted blocks (currently using skip strategy)");
        
        return recoverBySkippingCorrupted(sourceChannel, targetPath, checkResult, backupPath);
    }
    
    /**
     * 创建文件备份
     */
    private static String createBackup(String originalPath) throws IOException {
        Path original = Paths.get(originalPath);
        
        if (!Files.exists(original)) {
            throw new IOException("Original file does not exist: " + originalPath);
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupPath = originalPath + ".backup_" + timestamp;
        Path backup = Paths.get(backupPath);
        
        Files.copy(original, backup, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("Created backup: {} -> {}", originalPath, backupPath);
        return backupPath;
    }
    
    /**
     * 验证恢复后的文件
     */
    public static boolean validateRecoveredFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.error("Recovered file does not exist: {}", filePath);
                return false;
            }
            
            try (FileChannel channel = FileChannel.open(path, java.nio.file.StandardOpenOption.READ)) {
                IntegrityCheckResult result = FileIntegrityChecker.checkFileIntegrity(channel, 0, -1);
                
                if (result.isValid()) {
                    logger.info("Recovered file validation passed: {}", filePath);
                    return true;
                } else {
                    logger.warn("Recovered file still has {} corrupted blocks: {}", 
                              result.getCorruptedBlockCount(), filePath);
                    return false;
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to validate recovered file: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 清理旧的备份文件
     */
    public static void cleanupOldBackups(String basePath, int maxBackups) {
        try {
            Path baseDir = Paths.get(basePath).getParent();
            if (baseDir == null || !Files.exists(baseDir)) {
                return;
            }
            
            String fileName = Paths.get(basePath).getFileName().toString();
            
            Files.list(baseDir)
                .filter(path -> path.getFileName().toString().startsWith(fileName + ".backup_"))
                .sorted((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .skip(maxBackups)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        logger.debug("Deleted old backup: {}", path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete old backup: {}", path, e);
                    }
                });
                
        } catch (Exception e) {
            logger.warn("Failed to cleanup old backups for: {}", basePath, e);
        }
    }
} 