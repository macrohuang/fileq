package com.macrohuang.fileq.integrity;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.exception.FileQueueClosedException;
import com.macrohuang.fileq.integrity.FileIntegrityChecker.IntegrityCheckResult;
import com.macrohuang.fileq.integrity.FileRecoveryManager.RecoveryResult;
import com.macrohuang.fileq.integrity.FileRecoveryManager.RecoveryStrategy;

/**
 * 带有完整性检查功能的FileQueue包装器
 * 为现有的FileQueue实例添加完整性检查和恢复功能
 * 
 * @author macro
 * @param <E> 队列元素类型
 */
public class IntegrityAwareFileQueueWrapper<E> implements FileQueue<E> {
    
    private static final Logger logger = LoggerFactory.getLogger(IntegrityAwareFileQueueWrapper.class);
    
    private final FileQueue<E> delegate;
    private final FileQueueIntegrityManager integrityManager;
    private final int integrityCheckInterval;
    
    private int operationCount = 0;
    
    public IntegrityAwareFileQueueWrapper(FileQueue<E> delegate, FileQueueIntegrityManager integrityManager) {
        this(delegate, integrityManager, 1000); // 默认每1000次操作检查一次
    }
    
    public IntegrityAwareFileQueueWrapper(FileQueue<E> delegate, FileQueueIntegrityManager integrityManager, 
                                        int integrityCheckInterval) {
        this.delegate = delegate;
        this.integrityManager = integrityManager;
        this.integrityCheckInterval = integrityCheckInterval;
        
        logger.info("IntegrityAwareFileQueueWrapper initialized with check interval: {}", integrityCheckInterval);
        
        // 启动时进行完整性检查
        performStartupIntegrityCheck();
    }
    
    @Override
    public void add(E e) {
        delegate.add(e);
        
        // 定期进行完整性检查
        if (++operationCount % integrityCheckInterval == 0) {
            performPeriodicIntegrityCheck();
        }
    }
    
    @Override
    public E take() throws InterruptedException {
        E result = delegate.take();
        
        // 定期进行完整性检查
        if (++operationCount % integrityCheckInterval == 0) {
            performPeriodicIntegrityCheck();
        }
        
        return result;
    }
    
    @Override
    public E take(long timeout, TimeUnit unit) throws InterruptedException {
        E result = delegate.take(timeout, unit);
        
        // 定期进行完整性检查
        if (++operationCount % integrityCheckInterval == 0) {
            performPeriodicIntegrityCheck();
        }
        
        return result;
    }
    
    @Override
    public E peek() {
        E result = delegate.peek();
        
        // 定期进行完整性检查
        if (++operationCount % integrityCheckInterval == 0) {
            performPeriodicIntegrityCheck();
        }
        
        return result;
    }
    
    @Override
    public E peek(long timeout, TimeUnit unit) throws InterruptedException {
        E result = delegate.peek(timeout, unit);
        
        // 定期进行完整性检查
        if (++operationCount % integrityCheckInterval == 0) {
            performPeriodicIntegrityCheck();
        }
        
        return result;
    }
    
    @Override
    public int size() {
        return delegate.size();
    }
    
    @Override
    public boolean remain() {
        return delegate.remain();
    }
    
    @Override
    public void clear() {
        delegate.clear();
    }
    
    @Override
    public boolean delete() {
        return delegate.delete();
    }
    
    @Override
    public E remove() {
        E result = delegate.remove();
        
        // 定期进行完整性检查
        if (++operationCount % integrityCheckInterval == 0) {
            performPeriodicIntegrityCheck();
        }
        
        return result;
    }
    
    @Override
    public void close() {
        delegate.close();
    }
    
    /**
     * 手动触发完整性检查
     */
    public IntegrityCheckResult checkIntegrity() {
        return integrityManager.checkIntegrity(delegate);
    }
    
    /**
     * 手动触发文件恢复
     */
    public RecoveryResult recoverFile(String targetPath) {
        return integrityManager.recoverFile(targetPath);
    }
    
    /**
     * 手动触发文件恢复（指定策略）
     */
    public RecoveryResult recoverFile(String targetPath, RecoveryStrategy strategy) {
        return integrityManager.recoverFile(targetPath, strategy);
    }
    
    /**
     * 验证文件完整性
     */
    public boolean validateFile() {
        return integrityManager.validateFile();
    }
    
    /**
     * 清理旧备份
     */
    public void cleanupOldBackups(int maxBackups) {
        integrityManager.cleanupOldBackups(maxBackups);
    }
    
    /**
     * 启动时的完整性检查
     */
    private void performStartupIntegrityCheck() {
        try {
            logger.info("Performing startup integrity check");
            
            IntegrityCheckResult result = integrityManager.checkIntegrity(delegate);
            
            if (result != null && result.isValid()) {
                logger.info("Startup integrity check passed: {}", result.getSummary());
            } else if (result != null) {
                logger.warn("Startup integrity check found issues: {}", result.getSummary());
            } else {
                logger.error("Startup integrity check failed");
            }
            
        } catch (Exception e) {
            logger.error("Failed to perform startup integrity check", e);
        }
    }
    
    /**
     * 定期完整性检查
     */
    private void performPeriodicIntegrityCheck() {
        try {
            logger.debug("Performing periodic integrity check (operation count: {})", operationCount);
            
            IntegrityCheckResult result = integrityManager.checkIntegrity(delegate);
            
            if (result != null && !result.isValid()) {
                logger.warn("Periodic integrity check found issues: {}", result.getSummary());
            }
            
        } catch (Exception e) {
            logger.warn("Failed to perform periodic integrity check", e);
        }
    }
    
    // Getters
    public FileQueue<E> getDelegate() { return delegate; }
    public FileQueueIntegrityManager getIntegrityManager() { return integrityManager; }
    public int getIntegrityCheckInterval() { return integrityCheckInterval; }
    public int getOperationCount() { return operationCount; }
} 