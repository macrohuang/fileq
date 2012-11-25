package com.macrohuang.fileq.exception;

public class FileQueueClosedException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4170271231183366721L;

	public FileQueueClosedException() {
		super();
	}

	public FileQueueClosedException(String message, Throwable cause) {
		super(message, cause);
	}

	public FileQueueClosedException(String message) {
		super(message);
	}

	public FileQueueClosedException(Throwable cause) {
		super(cause);
	}
}
