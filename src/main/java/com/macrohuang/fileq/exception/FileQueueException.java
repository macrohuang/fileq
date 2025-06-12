package com.macrohuang.fileq.exception;

/**
 * FileQueue 操作异常的基类
 * 
 * @author macro
 */
public class FileQueueException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    public FileQueueException(String message) {
        super(message);
    }
    
    public FileQueueException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public FileQueueException(Throwable cause) {
        super(cause);
    }
} 