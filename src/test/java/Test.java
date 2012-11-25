import java.io.IOException;
import java.util.Arrays;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.macrohuang.fileq.MyObject;


public class Test {
    public static void main(String[] args) throws IOException {
        // Codec codec = new KryoCodec();
        // Integer integer;
        // integer = new Integer(1);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(11);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(1111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(11111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(111111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(1111111);
        // System.out.println(codec.encode(integer).length);
        //
        // codec = new ObjectCodec();
        // integer = new Integer(1);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(11);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(1111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(11111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(111111);
        // System.out.println(codec.encode(integer).length);
        // integer = new Integer(1111111);
        // System.out.println(codec.encode(integer).length);

        int a = 0xafab1234;
        byte[] b = new byte[] { (byte) ((a & 0xff000000) >> 24), (byte) ((a & 0x00ff0000) >> 16),
                (byte) ((a & 0x0000ff00) >> 8), (byte) ((a & 0x000000ff)) };
        System.out.println(Arrays.toString(b));
        int c = (b[0] << 24) | b[1] << 16 & 0x00ff0000 | b[2] << 8 & 0x0000ff00 | b[3] & 0x000000ff;
        // int c = (b[0] << 24) | (b[1] << 16) & 0x00ff0000;
        System.out.printf("%x,%x,%x,%x\n", b[0], b[1], b[2], b[3]);
        System.out.printf("c:%x\n", c);
        System.out.printf("a:%x\n", a);
        System.out.printf("d:%x\n", (0xaf << 24) | (0xab << 16));
        System.out.printf("%x\n",Integer.MAX_VALUE);
        // System.out.printf("%d,%d\n", a, b);
        Kryo kryo = new Kryo();
        Output output = new Output(1024);
        kryo.writeObject(output, new MyObject());
        Input input = new Input();
        input.setBuffer(output.toBytes());
        System.out.println((MyObject) kryo.readObject(input, Object.class));
    }
}
