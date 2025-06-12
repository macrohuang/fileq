package com.macrohuang.fileq.integrity;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.integrity.FileIntegrityChecker.IntegrityCheckResult;
import com.macrohuang.fileq.integrity.FileRecoveryManager.RecoveryResult;
import com.macrohuang.fileq.integrity.FileRecoveryManager.RecoveryStrategy;

/**
 * FileQueue完整性管理器
 * 为现有的FileQueue实例提供完整性检查和恢复功能
 * 
 * @author macro
 */
public class FileQueueIntegrityManager {
    
    private static final Logger logger = LoggerFactory.getLogger(FileQueueIntegrityManager.class);
    
    private final Config config;
    private final boolean autoRecovery;
    private final RecoveryStrategy recoveryStrategy;
    
    public FileQueueIntegrityManager(Config config) {
        this(config, true, RecoveryStrategy.BACKUP_AND_RECOVER);
    }
    
    public FileQueueIntegrityManager(Config config, boolean autoRecovery, RecoveryStrategy recoveryStrategy) {
        this.config = config;
        this.autoRecovery = autoRecovery;
        this.recoveryStrategy = recoveryStrategy;
        
        logger.info("FileQueueIntegrityManager initialized with auto recovery: {}, strategy: {}", 
                   autoRecovery, recoveryStrategy);
    }
    
    /**
     * 检查FileQueue的完整性
     * 
     * @param queue FileQueue实例
     * @return 完整性检查结果
     */
    public IntegrityCheckResult checkIntegrity(FileQueue<?> queue) {
        try {
            String dataFilePath = getDataFilePath();
            
            // 检查文件是否存在
            if (!java.nio.file.Files.exists(Paths.get(dataFilePath))) {
                logger.warn("Data file does not exist: {}", dataFilePath);
                return new IntegrityCheckResult(true, java.util.Collections.emptyList(), 
                                              java.util.Collections.emptyList(), 0, 0, 
                                              "Data file does not exist, assuming empty queue");
            }
            
            try (FileChannel channel = FileChannel.open(Paths.get(dataFilePath), StandardOpenOption.READ)) {
                IntegrityCheckResult result = FileIntegrityChecker.checkFileIntegrity(channel, 0, -1);
                
                if (result.isValid()) {
                    logger.info("FileQueue integrity check passed: {}", result.getSummary());
                } else {
                    logger.warn("FileQueue integrity check found issues: {}", result.getSummary());
                    
                    if (autoRecovery) {
                        logger.info("Attempting automatic recovery");
                        RecoveryResult recoveryResult = recoverFile(dataFilePath + ".recovered");
                        
                        if (recoveryResult.isSuccess()) {
                            logger.info("Automatic recovery successful: {}", recoveryResult.getMessage());
                        } else {
                            logger.error("Automatic recovery failed: {}", recoveryResult.getMessage());
                        }
                    }
                }
                
                return result;
            }
            
        } catch (Exception e) {
            logger.error("Failed to check FileQueue integrity", e);
            return null;
        }
    }
    
    /**
     * 恢复FileQueue文件
     * 
     * @param targetPath 恢复目标路径
     * @return 恢复结果
     */
    public RecoveryResult recoverFile(String targetPath) {
        return recoverFile(targetPath, recoveryStrategy);
    }
    
    /**
     * 恢复FileQueue文件
     * 
     * @param targetPath 恢复目标路径
     * @param strategy 恢复策略
     * @return 恢复结果
     */
    public RecoveryResult recoverFile(String targetPath, RecoveryStrategy strategy) {
        try {
            String dataFilePath = getDataFilePath();
            
            // 首先检查完整性
            IntegrityCheckResult checkResult;
            try (FileChannel sourceChannel = FileChannel.open(Paths.get(dataFilePath), StandardOpenOption.READ)) {
                checkResult = FileIntegrityChecker.checkFileIntegrity(sourceChannel, 0, -1);
            }
            
            if (checkResult == null) {
                return new RecoveryResult(false, 0, 0, 0, null, "Integrity check failed");
            }
            
            if (checkResult.isValid()) {
                return new RecoveryResult(true, checkResult.getValidBlockCount(), 0, 
                                        checkResult.getValidDataSize(), null, 
                                        "File is already valid, no recovery needed");
            }
            
            // 执行恢复
            try (FileChannel sourceChannel = FileChannel.open(Paths.get(dataFilePath), StandardOpenOption.READ)) {
                RecoveryResult result = FileRecoveryManager.recoverFile(sourceChannel, targetPath, checkResult, strategy);
                
                if (result.isSuccess()) {
                    logger.info("FileQueue recovery successful: {}", result.getMessage());
                    
                    // 验证恢复后的文件
                    if (FileRecoveryManager.validateRecoveredFile(targetPath)) {
                        logger.info("Recovered file validation passed");
                    } else {
                        logger.warn("Recovered file validation failed");
                    }
                } else {
                    logger.error("FileQueue recovery failed: {}", result.getMessage());
                }
                
                return result;
            }
            
        } catch (Exception e) {
            logger.error("FileQueue recovery operation failed", e);
            return new RecoveryResult(false, 0, 0, 0, null, "Recovery failed: " + e.getMessage());
        }
    }
    
    /**
     * 验证FileQueue文件的完整性
     * 
     * @return 是否完整
     */
    public boolean validateFile() {
        try {
            String dataFilePath = getDataFilePath();
            return FileRecoveryManager.validateRecoveredFile(dataFilePath);
        } catch (Exception e) {
            logger.error("Failed to validate FileQueue file", e);
            return false;
        }
    }
    
    /**
     * 清理旧的备份文件
     * 
     * @param maxBackups 保留的最大备份数量
     */
    public void cleanupOldBackups(int maxBackups) {
        try {
            String dataFilePath = getDataFilePath();
            FileRecoveryManager.cleanupOldBackups(dataFilePath, maxBackups);
            logger.info("Cleaned up old backups, keeping {} most recent", maxBackups);
        } catch (Exception e) {
            logger.warn("Failed to cleanup old backups", e);
        }
    }
    
    /**
     * 获取数据文件路径
     */
    private String getDataFilePath() {
        // 使用与FileUtil相同的路径生成逻辑
        return config.getBasePath() + File.separator + "data" + File.separator + config.getFilePrefix() + "0" + config.getFileSuffix();
    }
    
    /**
     * 创建带有完整性检查的FileQueue包装器
     */
    public static <E> IntegrityAwareFileQueueWrapper<E> wrapWithIntegrityCheck(FileQueue<E> queue, Config config) {
        return new IntegrityAwareFileQueueWrapper<>(queue, new FileQueueIntegrityManager(config));
    }
    
    /**
     * 创建带有完整性检查的FileQueue包装器（自定义配置）
     */
    public static <E> IntegrityAwareFileQueueWrapper<E> wrapWithIntegrityCheck(FileQueue<E> queue, Config config, 
                                                                              boolean autoRecovery, RecoveryStrategy strategy) {
        return new IntegrityAwareFileQueueWrapper<>(queue, new FileQueueIntegrityManager(config, autoRecovery, strategy));
    }
    
    // Getters
    public Config getConfig() { return config; }
    public boolean isAutoRecoveryEnabled() { return autoRecovery; }
    public RecoveryStrategy getRecoveryStrategy() { return recoveryStrategy; }
} 