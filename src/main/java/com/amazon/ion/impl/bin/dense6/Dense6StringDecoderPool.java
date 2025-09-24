package com.amazon.ion.impl.bin.dense6;

/**
 * A thread-safe shared pool of {@link Dense6StringDecoder}s that can be used for UTF8 decoding.
 */
public class Dense6StringDecoderPool extends Pool<Dense6StringDecoder> {

    private static final Dense6StringDecoderPool INSTANCE = new Dense6StringDecoderPool();

    // Do not allow instantiation; all classes should share the singleton instance.
    private Dense6StringDecoderPool() {
        super(new Allocator<Dense6StringDecoder>() {
            @Override
            public Dense6StringDecoder newInstance(Pool<Dense6StringDecoder> pool) {
                return new Dense6StringDecoder(pool);
            }
        });
    }

    /**
     * @return a threadsafe shared instance of {@link Dense6StringDecoderPool}.
     */
    public static Dense6StringDecoderPool getInstance() {
        return INSTANCE;
    }
}
