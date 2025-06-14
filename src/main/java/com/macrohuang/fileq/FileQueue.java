package com.macrohuang.fileq;

/**
 * A high-performance, file-based persistent queue implementation that provides thread-safe,
 * FIFO queue operations with support for up to 500,000 writes per second.
 * 
 * <p>FileQueue uses memory-mapped files and NIO channels for optimal I/O performance,
 * supporting various serialization codecs and concurrency strategies.</p>
 * 
 * <h3>Storage Architecture</h3>
 * <p>The queue maintains three types of files in organized directories:</p>
 * <ul>
 *   <li><strong>Meta File</strong> ({@code .meta}) - 46-byte binary file containing queue state</li>
 *   <li><strong>Data Files</strong> ({@code .data}) - Object storage with headers and checksums</li>
 *   <li><strong>Backup Files</strong> ({@code .bak}) - Automatic backup rotation (optional)</li>
 * </ul>
 * 
 * <h4>Meta File Format (46 bytes)</h4>
 * <table border="1">
 *   <tr><th>Offset</th><th>Size</th><th>Field</th><th>Description</th></tr>
 *   <tr><td>0-1</td><td>2</td><td>WN</td><td>Write file number</td></tr>
 *   <tr><td>2-9</td><td>8</td><td>Write Seq</td><td>Current write file sequence</td></tr>
 *   <tr><td>10-11</td><td>2</td><td>WP</td><td>Write position marker</td></tr>
 *   <tr><td>12-19</td><td>8</td><td>Write Pos</td><td>Current write file offset</td></tr>
 *   <tr><td>20-21</td><td>2</td><td>RN</td><td>Read file number</td></tr>
 *   <tr><td>22-29</td><td>8</td><td>Read Seq</td><td>Current read file sequence</td></tr>
 *   <tr><td>30-31</td><td>2</td><td>RP</td><td>Read position marker</td></tr>
 *   <tr><td>32-39</td><td>8</td><td>Read Pos</td><td>Current read file offset</td></tr>
 *   <tr><td>40-41</td><td>2</td><td>OC</td><td>Object count marker</td></tr>
 *   <tr><td>42-45</td><td>4</td><td>Count</td><td>Unread object count</td></tr>
 * </table>
 * 
 * <h4>Data File Format</h4>
 * <p>Each object is stored with a 16-byte header containing magic number and length,
 * followed by serialized object data and 16-byte checksum for integrity validation.</p>
 * 
 * <h3>Concurrency Support</h3>
 * <p>FileQueue provides multiple concurrency strategies:</p>
 * <ul>
 *   <li><strong>READ_WRITE_LOCK</strong> - Optimized for read-heavy workloads</li>
 *   <li><strong>REENTRANT_LOCK</strong> - Balanced performance for mixed workloads</li>
 *   <li><strong>SINGLE_THREAD</strong> - High-performance single-threaded mode</li>
 *   <li><strong>FILE_LOCK</strong> - Multi-process support using file locks</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * Config config = new Config();
 * config.setBasePath("/tmp/myqueue");
 * config.setConcurrencyMode(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK);
 * config.setCodec(new EnhancedKryoCodec());
 * 
 * FileQueue<String> queue = new EnhancedFileQueueImpl<>(config);
 * 
 * // Add items
 * queue.add("Hello");
 * queue.add("World");
 * 
 * // Read items
 * String item = queue.take(); // "Hello"
 * 
 * // Always close to release resources
 * queue.close();
 * }</pre>
 * 
 * <h3>Thread Safety</h3>
 * <p>All implementations are thread-safe and support concurrent access from multiple threads.
 * The choice of concurrency strategy affects performance characteristics under different
 * read/write ratios.</p>
 * 
 * <h3>Persistence and Reliability</h3>
 * <p>All queue operations are immediately persisted to disk with optional backup rotation.
 * Includes built-in integrity checking with CRC32/Adler32 checksums and automatic
 * corruption recovery mechanisms.</p>
 * 
 * @param <E> the type of elements held in this queue (must be Serializable)
 * @author macro
 * @version 2.0
 * @since 1.0
 * @see com.macrohuang.fileq.impl.EnhancedFileQueueImpl
 * @see com.macrohuang.fileq.conf.Config
 * @see com.macrohuang.fileq.concurrent.ConcurrencyStrategy
 */

import java.util.concurrent.TimeUnit;

import com.macrohuang.fileq.exception.FileQueueClosedException;

public interface FileQueue<E extends Object> {
    
	/**
	 * Checks if there are any unread objects remaining in the queue.
	 * 
	 * <p>This method provides a quick way to determine if the queue has pending items
	 * without affecting the read position or blocking.</p>
	 * 
	 * @return {@code true} if there are unread objects in the queue, {@code false} otherwise
	 * @see #size() for getting the exact count of unread objects
	 */
    public boolean remain();

    
    /**
     * Adds an object to the tail of the queue.
     * 
     * <p>The object is immediately serialized and persisted to disk using the
     * configured codec. This operation is thread-safe and atomic.</p>
     * 
     * <p><strong>Performance:</strong> Optimized for high-throughput writes,
     * supporting up to 500,000 writes per second depending on object size and
     * storage configuration.</p>
     * 
     * @param e the object to add to the queue (must not be null)
     * @throws FileQueueClosedException if the queue has been closed
     * @throws IllegalArgumentException if the object is null
     * @throws com.macrohuang.fileq.exception.FileQueueIOException if an I/O error occurs
     * @see #take() for retrieving objects from the queue
     */
    public void add(E e) throws FileQueueClosedException;

    /**
     * Retrieves and removes the head object from the queue, blocking if necessary
     * until an object becomes available.
     * 
     * <p>This method will block indefinitely if the queue is empty. Use
     * {@link #take(long, TimeUnit)} for timeout-based waiting.</p>
     * 
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently from multiple threads. Objects are returned in FIFO order.</p>
     * 
     * @return the head object from the queue (never null)
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public E take() throws InterruptedException,FileQueueClosedException;

    /**
     * Retrieves and removes the head object from the queue, waiting up to the
     * specified timeout if necessary for an object to become available.
     * 
     * <p>If an object is available immediately, it is returned without waiting.
     * If the queue is empty, this method will wait for the specified timeout.
     * If no object becomes available within the timeout period, {@code null} is returned.</p>
     * 
     * @param timeout the maximum time to wait for an object
     * @param timeUnit the time unit of the timeout parameter
     * @return the head object from the queue, or {@code null} if timeout expires
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws FileQueueClosedException if the queue has been closed
     * @throws IllegalArgumentException if timeout is negative
     */
    public E take(long timeout, TimeUnit timeUnit) throws InterruptedException,FileQueueClosedException;

    /**
     * Retrieves and removes the head object from the queue without blocking.
     * 
     * <p>This method returns immediately, throwing an exception if the queue is empty.
     * Use {@link #take()} for blocking behavior or {@link #peek()} to inspect
     * without removing.</p>
     * 
     * @return the head object from the queue
     * @throws FileQueueClosedException if the queue has been closed
     * @throws java.util.NoSuchElementException if the queue is empty
     */
    public E remove() throws FileQueueClosedException;

	/**
	 * Retrieves, but does not remove, the head object from the queue.
	 * 
	 * <p>This method allows inspection of the next object without affecting
	 * the queue state. Returns {@code null} if the queue is empty.</p>
	 * 
	 * <p><strong>Performance:</strong> This is a lightweight operation that
	 * does not modify queue state or require serialization.</p>
	 * 
	 * @return the head object from the queue, or {@code null} if empty
	 * @throws FileQueueClosedException if the queue has been closed
	 */
    public E peek() throws FileQueueClosedException;

	/**
	 * Retrieves, but does not remove, the head object from the queue,
	 * waiting up to the specified timeout if necessary.
	 * 
	 * <p>If an object is available immediately, it is returned without waiting.
	 * If the queue is empty, this method will wait for the specified timeout.
	 * A timeout value of 0 means wait indefinitely (equivalent to blocking behavior).</p>
	 * 
	 * @param timeout the maximum time to wait; 0 means wait indefinitely
	 * @param timeUnit the time unit of the timeout parameter
	 * @return the head object from the queue, or {@code null} if timeout expires
	 * @throws InterruptedException if the current thread is interrupted while waiting
	 * @throws FileQueueClosedException if the queue has been closed
	 * @throws IllegalArgumentException if timeout is negative
	 */
	public E peek(long timeout, TimeUnit timeUnit) throws InterruptedException;

    /**
     * Removes all objects from the queue by advancing the read position to match
     * the write position.
     * 
     * <p>This operation is atomic and thread-safe. Any unread objects are effectively
     * discarded, but the underlying data files remain unchanged for potential recovery.</p>
     * 
     * <p><strong>Performance:</strong> This is a very fast operation that only
     * updates metadata without modifying data files.</p>
     * 
     * @throws FileQueueClosedException if the queue has been closed
     */
    public void clear() throws FileQueueClosedException;

    /**
     * Returns the number of unread objects currently in the queue.
     * 
     * <p>This count represents objects that have been added but not yet
     * retrieved via {@link #take()} or {@link #remove()}.</p>
     * 
     * <p><strong>Thread Safety:</strong> The returned value is a snapshot
     * at the time of the call and may change immediately in concurrent scenarios.</p>
     * 
     * @return the number of unread objects in the queue (always non-negative)
     */
    public int size();

    /**
     * Closes the queue and releases all associated system resources.
     * 
     * <p>After calling this method, no further operations can be performed on the queue.
     * All file handles, channels, and memory mappings are safely released. Any pending
     * metadata is flushed to ensure consistency.</p>
     * 
     * <p><strong>Thread Safety:</strong> This method is safe to call from multiple
     * threads, but subsequent operations will throw {@link FileQueueClosedException}.</p>
     * 
     * <p><strong>Resource Management:</strong> Always call this method in a finally
     * block or use try-with-resources to ensure proper cleanup.</p>
     * 
     * @see #delete() for removing queue files from disk
     */
    public void close();

	/**
	 * Permanently deletes the queue and all its associated files from disk.
	 * 
	 * <p>This method first closes the queue (if not already closed) and then
	 * removes all data files, meta files, backup files, and the queue directory.
	 * This operation cannot be undone.</p>
	 * 
	 * <p><strong>Warning:</strong> All queue data will be permanently lost.
	 * Ensure you have backups if the data is important.</p>
	 * 
	 * <p><strong>File System:</strong> Some files may not be deletable due to
	 * file system locks or permissions. Check the return value and logs.</p>
	 * 
	 * @return {@code true} if all files were successfully deleted, {@code false}
	 *         if some files could not be deleted (check logs for details)
	 * @see #close() for closing without deleting files
	 */
	public boolean delete();
}
