package com.amazon.ion.impl.bin.dense6;

/**
 * A thread-safe shared pool of {@link Dense6StringEncoder}s that can be used for dense-8 encoding.
 */
public class Dense6StringEncoderPool extends Pool<Dense6StringEncoder> {

    private static final Dense6StringEncoderPool INSTANCE = new Dense6StringEncoderPool();

    // Do not allow instantiation; all classes should share the singleton instance.
    private Dense6StringEncoderPool() {
        super(new Allocator<Dense6StringEncoder>() {
            @Override
            public Dense6StringEncoder newInstance(Pool<Dense6StringEncoder> pool) {
                return new Dense6StringEncoder(pool);
            }
        });
    }

    /**
     * @return a threadsafe shared instance of {@link Dense6StringEncoderPool}.
     */
    public static Dense6StringEncoderPool getInstance() {
        return INSTANCE;
    }

}
