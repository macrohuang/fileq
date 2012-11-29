package com.macrohuang.fileq;

import java.util.concurrent.TimeUnit;

import com.macrohuang.fileq.exception.FileQueueClosedException;

public interface FileQueue<E extends Object> {
    
	/**
	 * Check if there is any object unread in the file queue.
	 * @return
	 */
    public boolean remain();

    
    /**
     * Add an object type E to the file queue.
     * @param e
     */
    public void add(E e) throws FileQueueClosedException;

    /**
     * Take an object from the file queue, block if there aren't any.
     * @return The object at the end of the queue.
     * @throws InterruptedException
     */
    public E take() throws InterruptedException,FileQueueClosedException;

    /**
     * Take an object from the file queue, after of blocking for timeout if there aren't still any, a null will be returned.
     * @param timeout
     * @param timeUnit
     * @return
     * @throws InterruptedException
     */
    public E take(long timeout, TimeUnit timeUnit) throws InterruptedException,FileQueueClosedException;

    /**
     * Remove and return the tail object of the file queue.
     * @return
     */
    public E remove() throws FileQueueClosedException;

    /**
     * Get the tail object of the queue, but not move the read position forward.
     * @return
     */
    public E peek() throws FileQueueClosedException;

    /**
     * Set the read position to the write position, skip any object unread if exists.
     */
    public void clear() throws FileQueueClosedException;

    /**
     * Get the unread object count.
     * @return
     */
    public int size();

    /**
     * Close the file queue, including the file and channel.  Nothing can be read from or write to the
     * file queue if its close() method has been called.
     */
    public void close();

	/**
	 * Delete the file queue, including data file, meat file, queue path.
	 * 
	 * @return <code>true</code> if successful deleted, otherwise
	 *         <code>false</code>
	 */
	public boolean delete();
}
