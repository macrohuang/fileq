import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import com.macrohuang.fileq.util.NumberBytesConvertUtil;


public class Test {
    public static void main(String[] args) throws IOException {
    	File file = new File("test1");
    	file.createNewFile();
    	FileInputStream fileInputStream = new FileInputStream(file);
    	FileOutputStream fileOutputStream = new FileOutputStream(file);
    	FileChannel writeChannel = fileOutputStream.getChannel();
    	FileChannel readChannel = fileInputStream.getChannel();
    	byte[] data = "This is a test for file channel".getBytes();
    	writeChannel.write(ByteBuffer.wrap(data));
		writeChannel.force(false);
    	ByteBuffer readBuffer = ByteBuffer.allocate(1024);
    	readChannel.read(readBuffer);
    	readBuffer.flip();
    	System.out.println(new String(readBuffer.array()));
    	writeChannel.close();
    	readChannel.close();
    	fileInputStream.close();
    	fileOutputStream.close();
    	file.delete();
		System.out.println(String.format("%019d", 1));
		System.out.printf("%x\n", Long.MAX_VALUE);
		System.out.printf("%x\n", Long.MIN_VALUE);
		System.out.println(Arrays.toString(NumberBytesConvertUtil.long2ByteArr(Long.MAX_VALUE)));
		System.out.println(Arrays.toString(NumberBytesConvertUtil.long2ByteArr(Long.MIN_VALUE)));
    }
}
