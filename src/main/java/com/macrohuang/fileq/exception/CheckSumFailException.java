package com.macrohuang.fileq.exception;

public class CheckSumFailException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -6545775144545096519L;

    public CheckSumFailException() {
        super();
    }

    public CheckSumFailException(String message, Throwable cause) {
        super(message, cause);
    }

    public CheckSumFailException(String message) {
        super(message);
    }

    public CheckSumFailException(Throwable cause) {
        super(cause);
    }
}
