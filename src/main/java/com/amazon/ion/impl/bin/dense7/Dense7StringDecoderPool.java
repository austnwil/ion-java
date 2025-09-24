package com.amazon.ion.impl.bin.dense7;

/**
 * A thread-safe shared pool of {@link Dense7StringDecoder}s that can be used for UTF8 decoding.
 */
public class Dense7StringDecoderPool extends Pool<Dense7StringDecoder> {

    private static final Dense7StringDecoderPool INSTANCE = new Dense7StringDecoderPool();

    // Do not allow instantiation; all classes should share the singleton instance.
    private Dense7StringDecoderPool() {
        super(new Allocator<Dense7StringDecoder>() {
            @Override
            public Dense7StringDecoder newInstance(Pool<Dense7StringDecoder> pool) {
                return new Dense7StringDecoder(pool);
            }
        });
    }

    /**
     * @return a threadsafe shared instance of {@link Dense7StringDecoderPool}.
     */
    public static Dense7StringDecoderPool getInstance() {
        return INSTANCE;
    }
}
