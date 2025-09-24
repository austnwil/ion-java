package com.amazon.ion.impl.bin.dense7;

/**
 * A thread-safe shared pool of {@link Dense7StringEncoder}s that can be used for dense-8 encoding.
 */
public class Dense7StringEncoderPool extends Pool<Dense7StringEncoder> {

    private static final Dense7StringEncoderPool INSTANCE = new Dense7StringEncoderPool();

    // Do not allow instantiation; all classes should share the singleton instance.
    private Dense7StringEncoderPool() {
        super(new Allocator<Dense7StringEncoder>() {
            @Override
            public Dense7StringEncoder newInstance(Pool<Dense7StringEncoder> pool) {
                return new Dense7StringEncoder(pool);
            }
        });
    }

    /**
     * @return a threadsafe shared instance of {@link Dense7StringEncoderPool}.
     */
    public static Dense7StringEncoderPool getInstance() {
        return INSTANCE;
    }

}
