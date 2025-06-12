# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FileQ is a high-performance, file-based queue library for Java that provides persistent, thread-safe queue operations with up to 500,000 writes/second performance. The project uses NIO FileChannel and memory mapping for optimal I/O performance.

## Development Commands

### Maven Build Commands
```bash
# Build and run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=EnhancedFileQueueImplTest

# Build without tests
mvn clean compile

# Package JAR
mvn package

# Run tests in quiet mode
mvn test -q
```

### Running Individual Tests
Use Maven's `-Dtest` parameter to run specific test classes or methods:
```bash
mvn test -Dtest=ConcurrencySafetyTest
mvn test -Dtest=KryoSerializationBugTest
mvn test -Dtest=FileIntegrityTest
```

## Core Architecture

### Main Components

1. **FileQueue Interface** (`FileQueue.java`): Core contract defining queue operations (add, take, peek, clear)

2. **Queue Implementations** (`impl/`):
   - `ThreadLockFileQueueImpl`: Standard ReentrantLock-based implementation
   - `EnhancedFileQueueImpl`: Latest implementation with pluggable concurrency strategies
   - `FileLockFileQueueImpl`: Multi-process support using file locks

3. **Concurrency Strategies** (`concurrent/`):
   - `ReadWriteLockStrategy`: Optimized for read-heavy workloads
   - `ReentrantLockStrategy`: Balanced performance
   - `SingleThreadStrategy`: High-performance single-threaded mode
   - `LockStatistics`: Performance monitoring and lock contention tracking

4. **Serialization Layer** (`codec/`):
   - `EnhancedKryoCodec`: High-performance codec (97% faster than standard)
   - `KryoCodec`: Standard Kryo 5.6.0 serialization
   - `DefaultObjectCodec`: Java object serialization fallback

5. **Data Integrity** (`integrity/`):
   - `FileIntegrityChecker`: Corruption detection and validation
   - `FileRecoveryManager`: Automatic backup and recovery mechanisms
   - `EnhancedChecksumUtil`: CRC32/Adler32 checksum implementations

### Configuration System

The `Config` class centralizes all configuration options:
- File paths and sizes
- Codec selection
- Concurrency strategy configuration  
- Backup and integrity settings
- Performance tuning parameters

### File Storage Format

- **Meta File** (`.meta`): 46-byte binary file storing queue state (positions, count)
- **Data Files** (`.data`): Object storage with 16-byte headers and checksums
- **Backup System**: Automatic daily backup rotation with date-based organization

## Key Development Patterns

### Error Handling
- Custom exception hierarchy under `exception/` package
- Use SLF4J logging instead of printStackTrace
- `ResourceManager` for safe resource cleanup

### Testing Strategy
- 64 comprehensive tests covering unit, integration, concurrency scenarios
- Performance benchmarks for codec comparison
- Concurrency safety tests with multiple threads
- Error injection and recovery testing

### Recent Improvements
- Java 17 migration with modern language features
- Kryo 5.6.0 upgrade resolving Arrays.asList() serialization issues
- Complete concurrency strategy system rewrite
- Enhanced error handling and resource management

## Typical Usage Pattern

```java
Config config = new Config();
config.setBasePath("/tmp/myqueue");
config.setConcurrencyMode(ConcurrencyStrategy.AccessMode.READ_WRITE_LOCK);
config.setCodec(new EnhancedKryoCodec());

FileQueue<String> queue = new EnhancedFileQueueImpl<>(config);
// Use queue operations
queue.close(); // Always close to release resources
```

## Current Status

- **Branch**: `upgrade-java17` (active development)
- **Test Status**: 63/64 tests passing (1 file integrity test needs attention)
- **Performance**: Production-ready with excellent throughput characteristics
- **Dependencies**: Modern stack (Kryo 5.6.0, SLF4J 2.0.13, JUnit Jupiter 5.10.2)