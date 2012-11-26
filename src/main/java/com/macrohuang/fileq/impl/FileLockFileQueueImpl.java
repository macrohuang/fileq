package com.macrohuang.fileq.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.codec.Codec;
import com.macrohuang.fileq.codec.impl.KryoCodec;
import com.macrohuang.fileq.conf.Config;
import com.macrohuang.fileq.exception.CheckSumFailException;
import com.macrohuang.fileq.util.IntegerBytesConvertUtil;

public class FileLockFileQueueImpl<E> implements FileQueue<E> {
	    private Config config;
	    private AtomicInteger objectCount;
	    private FileChannel fileChannel;
	    private Codec<E> codec;
	    private AtomicLong writePosition;
	    private AtomicLong readPosition;
	    private MappedByteBuffer byteBuffer;
	    private static final int META_SIZE = 5;
	    private static final int CHECKSUM_SIZE = 4;
	    private static final byte magic = (byte) 0xff;
	    RandomAccessFile randomAccessFile;

	    public FileLockFileQueueImpl() {
	        try {
	            File file = new File("test");
	            file.deleteOnExit();
	            randomAccessFile = new RandomAccessFile(file, "rw");
	            fileChannel = randomAccessFile.getChannel();
	            codec = new KryoCodec<E>();
	            objectCount = new AtomicInteger(0);
	            writePosition = new AtomicLong(0L);
	            readPosition = new AtomicLong(0L);
	        } catch (FileNotFoundException e) {
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
	        byte[] metaBytes = new byte[5];
	        metaBytes[0] = magic;
	        System.arraycopy(IntegerBytesConvertUtil.int2ByteArr(objBytes.length), 0, metaBytes, 1, 4);
	        byte[] checkSum = IntegerBytesConvertUtil.int2ByteArr(META_SIZE + objBytes.length);
	        long size = metaBytes.length + objBytes.length + checkSum.length;
	        FileLock writeLock = null;
	        long position=0;
	        try {
	        	while(writeLock==null){
	        		try{
	        			position = writePosition.get();
	        			writeLock = fileChannel.tryLock(position, size, false);
	        		}catch (Exception e1) {
	        			//get lock fail, some instance write this region now, wait.
					}
	        		if (writeLock==null){
	        			Thread.sleep(100);
	        		}
	        	}
	            byteBuffer = fileChannel.map(MapMode.READ_WRITE, writePosition.get(), size);
	            byteBuffer.put(metaBytes);
	            byteBuffer.put(objBytes);
	            byteBuffer.put(checkSum);
	            writePosition.addAndGet(size);
	            objectCount.getAndIncrement();
	        } catch (IOException e1) {
	            e1.printStackTrace();
	        } catch (InterruptedException e1) {
				e1.printStackTrace();
			} finally {
	        	if (writeLock!=null){
	        		try {
						writeLock.release();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
	        	}
	        }
	    }

	    private E peekInner(boolean remove) {
	        FileLock metaLock = null;
	        FileLock objLock = null;
	        FileLock checksumLock = null;
	        try {
	            long position = readPosition.get();
	            metaLock = fileChannel.lock(position, META_SIZE, false);
	            ByteBuffer metaBuffer = ByteBuffer.allocate(META_SIZE);
	            fileChannel.read(metaBuffer, position);
	            metaBuffer.flip();
	            if (metaBuffer.get() != magic) {// data error

	            }
	            int objLength = metaBuffer.getInt();
	            objLock = fileChannel.lock(position + META_SIZE, objLength, false);
	            ByteBuffer objBuffer = ByteBuffer.allocate(objLength);
	            fileChannel.read(objBuffer, position + META_SIZE);
	            E obj = (E) codec.decode(objBuffer.array());
	            checksumLock = fileChannel.lock(position + META_SIZE + objLength, CHECKSUM_SIZE,
	                    false);
	            ByteBuffer checksumBuffer = ByteBuffer.allocate(4);
	            fileChannel.read(checksumBuffer, position + META_SIZE + objLength);
	            checksumBuffer.flip();
	            if (checksumBuffer.getInt() != META_SIZE + objLength) {// checksum
	                throw new CheckSumFailException();
	            }
	            if (remove)
	                readPosition.getAndAdd(META_SIZE + objLength + CHECKSUM_SIZE);
	            return obj;

	        } catch (Exception e) {
	            e.printStackTrace();
	        } finally {
	            if (metaLock != null) {
	                try {
	                    metaLock.release();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	            if (objLock != null) {
	                try {
	                    objLock.release();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	            if (checksumLock != null) {
	                try {
	                    checksumLock.release();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	        }
	        return null;
	    }
	    @Override
	    public E remove() {
	        if (readPosition.get() == writePosition.get())
	            return null;
	        return peekInner(true);
	    }

	    @Override
	    public E peek() {
	        if (readPosition.get() == writePosition.get())
	            return null;
	        return peekInner(false);
	    }

	    @Override
	    public void clear() {
	        readPosition.getAndSet(writePosition.get());
	    }

	    @Override
	    public E take() throws InterruptedException {
	        while (readPosition.get() == writePosition.get()) {
	            Thread.sleep(100);
	        }
	        return peekInner(true);
	    }

	    @Override
	    public E take(long timeout, TimeUnit unit) throws InterruptedException {
	        if (readPosition.get() == writePosition.get()) {
	            Thread.sleep(unit.convert(timeout, TimeUnit.MILLISECONDS));
	        }
	        if (readPosition.get() == writePosition.get()) {
	            return null;
	        }
	        return peekInner(true);
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
