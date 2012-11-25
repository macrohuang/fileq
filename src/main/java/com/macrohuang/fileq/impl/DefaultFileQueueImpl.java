package com.macrohuang.fileq.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.exception.CheckSumFailException;
import com.macrohuang.fileq.util.IntByteArrayConverter;

/**
 * You should always keep this class Singleton in your application, or there will be something damage! To use multion, use {@link com.macrohuang.fileq.MultionFileQueueImpl} instead.
 * @author macro
 *
 * @param <E>
 */
public class DefaultFileQueueImpl<E> implements FileQueue<E> {
    private Config config;
    private AtomicInteger objectCount;
    private FileChannel fileChannel;
    private Codec<E> codec;
    private AtomicLong writePosition;
    private AtomicLong readPosition;
    private MappedByteBuffer byteBuffer;
    private static final int META_SIZE = 16;
    private static final int CHECKSUM_SIZE = 16;
    private static final int magic =1314520;
    private static final byte[] LEADING_HEAD=IntByteArrayConverter.int2ByteArr(magic);
    private ReentrantLock writeLock = new ReentrantLock();
    private ReentrantLock readLock = new ReentrantLock();
    RandomAccessFile randomAccessFile;

    public DefaultFileQueueImpl() {
        try {
            File file = new File("test");
            file.delete();
            file.createNewFile();
            randomAccessFile = new RandomAccessFile(file, "rw");
            fileChannel = randomAccessFile.getChannel();
            codec = new KryoCodec<E>();
            objectCount = new AtomicInteger(0);
            writePosition = new AtomicLong(0L);
            readPosition = new AtomicLong(0L);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
			e.printStackTrace();
		}
    }
    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public boolean remain() {
        return readPosition == writePosition;
    }

    public int size() {
        return objectCount.get();
    }
    @Override
    public void add(E e) {
        byte[] objBytes = codec.encode(e);
        byte[] metaBytes = new byte[META_SIZE];
        Arrays.fill(metaBytes, (byte)0xff);
        System.arraycopy(LEADING_HEAD, 0, metaBytes, 0, 4);
        System.arraycopy(IntByteArrayConverter.int2ByteArr(objBytes.length), 0, metaBytes, 4, 4);
        byte[] checkSum = new byte[CHECKSUM_SIZE];
        Arrays.fill(checkSum, (byte)0xff);
        System.arraycopy(IntByteArrayConverter.int2ByteArr(META_SIZE + objBytes.length), 0, checkSum, 0, IntByteArrayConverter.int2ByteArr(META_SIZE + objBytes.length).length);
        long size = metaBytes.length + objBytes.length + checkSum.length;
        try {
        	writeLock.lock();
            byteBuffer = fileChannel.map(MapMode.READ_WRITE, writePosition.get(), size);
            byteBuffer.put(metaBytes);
            byteBuffer.put(objBytes);
            byteBuffer.put(checkSum);
            writePosition.addAndGet(size);
            objectCount.getAndIncrement();
        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            writeLock.unlock();
        }
    }

	private E peekInner(boolean remove,long timeout) {
        try {
        	if (remove){
        		readLock.lock();
        	}
            long position = readPosition.get();
            ByteBuffer metaBuffer = ByteBuffer.allocate(META_SIZE);
            fileChannel.read(metaBuffer, position);
            metaBuffer.flip();
            if (metaBuffer.getInt()!= magic) {// data error,try to read from the right position.
            	if (!readLock.isHeldByCurrentThread())
            		readLock.lock();
            	position = readPosition.incrementAndGet();
            	boolean success = false;
            	while(!success){
            		if (position == writePosition.get()){
            			if (timeout>0){
            				Thread.sleep(timeout);
            				if (position == writePosition.get()){
            					return null;
            				}
            			}else{
            				Thread.sleep(100);
            			}
            		}
            		metaBuffer.clear();
                    fileChannel.read(metaBuffer, position);
                    metaBuffer.flip();
                    if (metaBuffer.getInt()==magic){
                    	success=true;
                    }else{
                    	position=readPosition.incrementAndGet();
                    }
            	}
            }
            int objLength = metaBuffer.getInt();
            ByteBuffer objBuffer = ByteBuffer.allocate(objLength);
            fileChannel.read(objBuffer, position + META_SIZE);
            E obj = (E) codec.decode(objBuffer.array());
            ByteBuffer checksumBuffer = ByteBuffer.allocate(CHECKSUM_SIZE);
            fileChannel.read(checksumBuffer, position + META_SIZE + objLength);
            checksumBuffer.flip();
            if (checksumBuffer.getInt() != META_SIZE + objLength) {// checksum
                throw new CheckSumFailException();
            }
            if (remove){
            	readPosition.getAndAdd(META_SIZE + objLength + CHECKSUM_SIZE);
            }
            return obj;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        	if (readLock.isHeldByCurrentThread())
        		readLock.unlock();
        }
        return null;
    }
    @Override
    public E remove() {
        if (readPosition.get() == writePosition.get())
            return null;
        return peekInner(true,0L);
    }

    @Override
    public E peek() {
        if (readPosition.get() == writePosition.get())
            return null;
        return peekInner(false,0L);
    }

    @Override
    public void clear() {
    	objectCount.getAndSet(0);
        readPosition.getAndSet(writePosition.get());
    }

    @Override
    public E take() throws InterruptedException {
        while (readPosition.get() == writePosition.get()) {
            Thread.sleep(100);
        }
        return peekInner(true,0L);
    }

    @Override
    public E take(long timeout, TimeUnit unit) throws InterruptedException {
        if (readPosition.get() == writePosition.get()) {
            Thread.sleep(unit.convert(timeout, TimeUnit.MILLISECONDS));
        }
        if (readPosition.get() == writePosition.get()) {
            return null;
        }
        return peekInner(true,unit.convert(timeout, TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        try {
        	randomAccessFile.close();
            fileChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
