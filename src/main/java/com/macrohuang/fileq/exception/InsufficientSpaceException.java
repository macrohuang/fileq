package com.macrohuang.fileq.exception;

import java.io.IOException;

/**
 * 磁盘空间不足异常
 * 
 * @author macro
 */
public class InsufficientSpaceException extends FileQueueIOException {
    
    private static final long serialVersionUID = 1L;
    
    private final long requiredSpace;
    private final long availableSpace;
    
    public InsufficientSpaceException(long requiredSpace, long availableSpace) {
        super(String.format("Insufficient disk space. Required: %d bytes, Available: %d bytes", 
              requiredSpace, availableSpace));
        this.requiredSpace = requiredSpace;
        this.availableSpace = availableSpace;
    }
    
    public InsufficientSpaceException(String message, long requiredSpace, long availableSpace, IOException cause) {
        super(message, cause);
        this.requiredSpace = requiredSpace;
        this.availableSpace = availableSpace;
    }
    
    public long getRequiredSpace() {
        return requiredSpace;
    }
    
    public long getAvailableSpace() {
        return availableSpace;
    }
} 