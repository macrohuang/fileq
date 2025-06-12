package com.macrohuang.fileq.exception;

import java.io.IOException;

/**
 * FileQueue IO 操作异常
 * 
 * @author macro
 */
public class FileQueueIOException extends FileQueueException {
    
    private static final long serialVersionUID = 1L;
    
    public FileQueueIOException(String message) {
        super(message);
    }
    
    public FileQueueIOException(String message, IOException cause) {
        super(message, cause);
    }
    
    public FileQueueIOException(IOException cause) {
        super("FileQueue IO operation failed", cause);
    }
} 