fileq
=====

A file base queue, using file channel, mmap and less meta info. 

## Features

- High performance file-based queue implementation
- Uses Java NIO FileChannel and memory mapping for optimal I/O performance
- Thread-safe operations with ReentrantLock
- Configurable file size and backup options
- Multiple codec support (Kryo, Default Object Serialization)
- Comprehensive test coverage

## Performance

In my PC, Intel i5 3.10GHz, 4G Ram, Win7 Professional 64bit, it can reach up to 500,000 times/second of 1K data write and 50,000 times/second of 1K data read.

## Requirements

- Java 17 or higher
- Maven 3.6+

## Quick Start

```java
import com.macrohuang.fileq.FileQueue;
import com.macrohuang.fileq.impl.ThreadLockFileQueueImpl;
import com.macrohuang.fileq.conf.Config;

// Create configuration
Config config = new Config();
config.setBasePath("/tmp/myqueue");
config.setFileSize(1024 * 1024 * 100); // 100MB per file

// Create queue
FileQueue<String> queue = new ThreadLockFileQueueImpl<>(config);

// Add items
queue.add("Hello");
queue.add("World");

// Retrieve items
String item1 = queue.take(); // "Hello"
String item2 = queue.take(); // "World"

// Clean up
queue.close();
```

## Codec Options

### Default Kryo Codec
```java
Config config = new Config();
// Uses KryoCodec by default
```

### Enhanced Kryo Codec (Recommended for better performance)
```java
import com.macrohuang.fileq.codec.impl.EnhancedKryoCodec;

Config config = new Config();
config.setCodec(new EnhancedKryoCodec());
```

## Known Issues

### ✅ RESOLVED: Arrays.asList() Serialization Issue

**Previous Issue**: In earlier versions with Kryo 2.20, objects containing `Arrays.asList()` fields could not be properly deserialized.

**Solution**: This issue has been completely resolved by upgrading to Kryo 5.6.0. You can now safely use `Arrays.asList()` in your DTOs.

For detailed information about the solution, see [Kryo Serialization Guide](docs/KRYO_SERIALIZATION_GUIDE.md).

### Platform-Specific Issues

**Windows JDK 6 Memory Mapping Issue**: There is a bug in JDK 6 under Windows platform where memory regions cannot be unmapped when mapped multiple times. See [Oracle Bug Database](http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6521677) for details. This issue is resolved in newer JDK versions.

## Documentation

- [Kryo Serialization Guide](docs/KRYO_SERIALIZATION_GUIDE.md) - Detailed guide for serialization issues and solutions
- [TODO List](TODO.md) - Project roadmap and completed tasks

## Contributing

1. Fork the repository
2. Create your feature branch
3. Add tests for your changes
4. Ensure all tests pass
5. Submit a pull request

## License

This project is open source. Please check the license file for details.
