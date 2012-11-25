package com.macrohuang.fileq;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.macrohuang.fileq.impl.DefaultFileQueueImpl;

public class FileQueueTest {
    FileQueue<MyObject> fileQueue;
    int max = 10000;
    int threads = 20;
    ExecutorService executorService = Executors.newFixedThreadPool(threads);

    @Before
    public void init(){
    	fileQueue = new DefaultFileQueueImpl<MyObject>();
    }
    
    @After
    public void clean(){
    	fileQueue.clear();
    }
    @Test
    public void testAdd() {
        for (int i = 0; i < max; i++) {
            fileQueue.add(new MyObject());
        }
        Assert.assertEquals(max, fileQueue.size());
    }
    @Test
    public void testAddMultThread() throws InterruptedException {
    	for (int i=0;i<threads;i++){
    		executorService.submit(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < max / threads; i++) {
						fileQueue.add(new MyObject());
					}
				}
			});
    	}
    	executorService.shutdown();
    	executorService.awaitTermination(100, TimeUnit.SECONDS);
    	Assert.assertEquals(max, fileQueue.size());
    }

    @Test
    public void testPeek() {
        fileQueue.add(new MyObject());
        MyObject myObject = (MyObject) fileQueue.peek();
        MyObject myObject2 = (MyObject) fileQueue.peek();
        Assert.assertNotNull(myObject);
        Assert.assertNotNull(myObject2);
        org.junit.Assert.assertEquals(myObject, myObject2);
    }
}
