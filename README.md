fileq
=====

A file base queue, using file channel, mmap and less meta info. 
There is a bug of jdk6 under Windows platform, it can't be unmap while map a region more than one times, see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6521677 for more detail. So if you are running the tests under windows platform, it may be fail.

Known bugs:
1. If your DTO has some fields type of Object or List, assign from Arrays.asList(), then it can't be deserialized via Kryo. Cause Arrays.asList return an private static inner class of Arrays named ArrayList, not the java.util.ArrayList, it doesn't have non-arguments constroctor, and it can be create out of Arrays.

Performance:
In my PC, Intel i5 3.10GHz, 4G Ram, Win7 Professional 64bit, it can reach up to 500,000 times/second of 1K data write and 50,000 times/second of 1K data read. 
