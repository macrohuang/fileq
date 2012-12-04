package com.macrohuang.fileq;

/**
 * A file queue using java serialize to store binary data in file. 
 * The file queue has three types of file, backup file, data file and meta file. The data and meta files are in the basePath/data directory, the backup files are in the basePath/bak directory.<br>
 * <ul>
 * <li> The meta file is a file of 46 bytes binary file<br>
 * <table>
 * <th><td>bytes</td><td>data</td><td>describe</td></th>
 * <tr><td> 1 -  2</td><td>WN</td><td>short for write number</td></tr>
 * <tr><td> 3 - 10</td><td>an 8 bytes long integer</td><td>current sequence of writing file.</td></tr>
 * <tr><td>11 - 12</td><td>WP</td><td>short for write position</td></tr>
 * <tr><td>13 - 20</td><td>an 8 bytes long integer</td><td>Current write file offset.</td></tr>
 * <tr><td>21 - 22</td><td>RN</td><td>short for read number</td></tr>
 * <tr><td>23 - 30</td><td>an 8 bytes long integer</td><td>current sequence of reading file.</td></tr>
 * <tr><td>31 - 32</td><td>RP</td><td>short for read position</td></tr>
 * <tr><td>33 - 40</td><td>an 8 bytes long integer</td><td>Current read file offset.</td></tr>
 * <tr><td>41 - 42</td><td>OC</td><td>short for object count</td></tr>
 * <tr><td>43 - 46</td><td>an 4 bytes integer</td><td>Unread object counts in the queue.</td></tr>
 * </table>
 * </li>
 * <li>
 * 	The data file is padding of object bytes. Each object has a 16 bytes meta, including a magic number and an integer L describe the body length.<br>
 * After the meta are L bytes of object data. Following is an 16 bytes checksum.
 * </li>
 */ 

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
	 * If the queue is empty, a null will be returned.
	 * 
	 * @return
	 */
    public E peek() throws FileQueueClosedException;

	/**
	 * Get the tail object of the queue, but not move the read position forward.
	 * If the queue is empty, wait for timeout, if still empty then a null will
	 * be returned.
	 * 
	 * @param timeout
	 *            The value of timeout, 0 stands for never timeout.
	 * @param timeUnit
	 * @return
	 * @throws InterruptedException
	 */
	public E peek(long timeout, TimeUnit timeUnit) throws InterruptedException;

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
