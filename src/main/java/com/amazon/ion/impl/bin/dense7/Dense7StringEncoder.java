package com.amazon.ion.impl.bin.dense7;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import com.amazon.ion.IonException;

/**
 * Encodes {@link String}s to Dense7. Instances of this class are reusable but are NOT threadsafe.
 *
 * Instances are vended by {@link Dense7StringEncoderPool#getOrCreate()}.
 *
 * {@link #encode(String)} can be called any number of times. Users are expected to call {@link #close()} when
 * the encoder is no longer needed.
 */
public class Dense7StringEncoder extends Poolable<Dense7StringEncoder> {
    // The longest String (as measured by {@link java.lang.String#length()}) that this instance can encode without
    // requiring additional allocations.
    private static final int SMALL_STRING_SIZE = 4 * 1024;
    private static final int MAX_BYTES_PER_CHAR = 10;

    private static final int ASCII_CONTROL_WHITESPACE_START = 0x09; // horizontal tab
    private static final int ASCII_CONTROL_WHITESPACE_END   = 0x0d; // carriage return
    private static final int ASCII_PRINTABLE_START = 0x20; // space
    private static final int ASCII_PRINTABLE_END = 0x7e; // ~

    private static final int ISOLATE_x_x_7   = 0b1111111;
    private static final int ISOLATE_x_7_x   = 0b1111111_0000000;
    private static final int ISOLATE_7_x_x   = 0b1111111_0000000_0000000;

    private static final int DENSE7_CONTROL_WHITESPACE_START = 0b001_1001; // horizontal tab  (x09)
    private static final int DENSE7_CONTROL_WHITESPACE_END   = 0b001_1101; // carriage return (x0d)

    private static final int DENSE7_2_BYTE_START = 0;
    private static final int DENSE7_3_BYTE_START = 0b001_0000;
    private static final int DENSE7_4_BYTE_START = 0b001_1000;

    // Add this value to an ASCII value to get the equivalent byte in Dense7
    private static final int ASCII_TO_DENSE7_CONTROL_WHITESPACE_OFFSET
        = DENSE7_CONTROL_WHITESPACE_START - ASCII_CONTROL_WHITESPACE_START;

    // Reusable resources for encoding Strings as Dense7 bytes
    final ByteBuffer dense7EncodingBuffer;
    final ByteBuffer reusableIntermediateBuffer;

    Dense7StringEncoder(Pool<Dense7StringEncoder> pool) {
        super(pool);
        dense7EncodingBuffer = ByteBuffer.allocate((int) (SMALL_STRING_SIZE * MAX_BYTES_PER_CHAR));
        reusableIntermediateBuffer = ByteBuffer.allocate((int) (SMALL_STRING_SIZE * MAX_BYTES_PER_CHAR));
    }

    /**
     * Encodes the provided String's text to Dense7. Unlike {@link String#getBytes(Charset)}, this method will not
     * silently replace characters that cannot be encoded with a substitute character. Instead, it will throw
     * an {@link IllegalArgumentException}.
     *
     * Some resources in the returned {@link Result} may be reused across calls to this method. Consequently,
     * callers should use the Result and discard it immediately.
     *
     * @param text A Java String to encode as UTF8 bytes.
     * @return  A {@link Result} containing a byte array of Dense7 bytes and encoded length.
     * @throws IllegalArgumentException if the String cannot be encoded as Dense7.
     */
    public Result encode(String text) {
        // System.out.printf("Encoding %s\n", text);

        ByteBuffer encodingBuffer;
        ByteBuffer intermediateBuffer;

        int length = text.length();

        if (length > SMALL_STRING_SIZE) {
            // Allocate a new buffer for large strings
            encodingBuffer = ByteBuffer.allocate((int) (length * MAX_BYTES_PER_CHAR));
            intermediateBuffer = ByteBuffer.allocate((int) (length * MAX_BYTES_PER_CHAR));
        } else {
            // Reuse our existing buffers for small strings
            // System.out.println("Small buffer will do c");
            encodingBuffer = dense7EncodingBuffer;
            encodingBuffer.clear();
            intermediateBuffer = reusableIntermediateBuffer;
            intermediateBuffer.clear();
        }

        for(int i = 0; i < length; i++) {
            final char char1 = text.charAt(i);

            if     (isCharAsciiPrintable(char1)) {
                intermediateBuffer.put((byte) char1);
            }
            else if(isCharAsciiControlWhitespace(char1)) {
                intermediateBuffer.put((byte)(char1 + ASCII_TO_DENSE7_CONTROL_WHITESPACE_OFFSET));
            }
            else if(isCharHighSurrogate(char1)) {
                if(++i >= length) {
                    throw new IonException("Could not encode stirng. String was terminated by lone high surrogate.");
                }
                final char char2 = text.charAt(i);
                if(!isCharLowSurrogate(char2)) {
                    throw new IonException("Could not encode string. String contained high surrogate not followed by low surrogate.");
                }

                final int codePoint = (char1 - 0xd800) * 0x400 + (char2 - 0xdc00) + 0x10000;
                
                final byte b1 = (byte)DENSE7_4_BYTE_START;
                final byte b2 = (byte)((codePoint & ISOLATE_7_x_x) >>> 14);
                final byte b3 = (byte)((codePoint & ISOLATE_x_7_x) >>> 7);
                final byte b4 = (byte)(codePoint & ISOLATE_x_x_7);
                intermediateBuffer.put(b1);
                intermediateBuffer.put(b2);
                intermediateBuffer.put(b3);
                intermediateBuffer.put(b4);
            }
            else if(isCharLowSurrogate(char1)) {
                throw new IonException("Could not encode string. String contained lone low surrogate.");
            }
            else if(isChar2ByteCodePoint(char1)) {
                final byte b1 = (byte)(DENSE7_2_BYTE_START | ((char1 & ISOLATE_x_7_x) >>> 7));
                final byte b2 = (byte)(char1 & ISOLATE_x_x_7);
                intermediateBuffer.put(b1);
                intermediateBuffer.put(b2);
            }
            else if(isChar3ByteCodePoint(char1)) {
                final byte b1 = (byte)(DENSE7_3_BYTE_START | ((char1 & ISOLATE_7_x_x) >>> 14));
                final byte b2 = (byte)((char1 & ISOLATE_x_7_x) >>> 7);
                final byte b3 = (byte)(char1 & ISOLATE_x_x_7);
                intermediateBuffer.put(b1);
                intermediateBuffer.put(b2);
                intermediateBuffer.put(b3);
            }
            else if(char1 == '\0') { // null
                intermediateBuffer.put((byte)0b111_1111);
            }
            else if(char1 == '\u0007') { // BEL
                intermediateBuffer.put((byte)0b001_1111);
            }
            else if(char1 == '\u0008') { // Backspace
                intermediateBuffer.put((byte)0b001_1110);
            }
            else {
                // It's some other ASCII control character. Just encode it with overlong UTF-8.
                final byte b1 = (byte)DENSE7_2_BYTE_START;
                final byte b2 = (byte)(char1 & ISOLATE_x_x_7);
                intermediateBuffer.put(b1);
                intermediateBuffer.put(b2);
            }
        }

        
        intermediateBuffer.flip();
        while(intermediateBuffer.hasRemaining()) {
            final int bytesToWrite = intermediateBuffer.remaining();

            final int char1 = intermediateBuffer.get();
            final int char2 = bytesToWrite > 1 ? intermediateBuffer.get() : 0;
            final int char3 = bytesToWrite > 2 ? intermediateBuffer.get() : 0;
            final int char4 = bytesToWrite > 3 ? intermediateBuffer.get() : 0;
            final int char5 = bytesToWrite > 4 ? intermediateBuffer.get() : 0;
            final int char6 = bytesToWrite > 5 ? intermediateBuffer.get() : 0;
            final int char7 = bytesToWrite > 6 ? intermediateBuffer.get() : 0;
            final int char8 = bytesToWrite > 7 ? intermediateBuffer.get() : 0;

            encodingBuffer.put((byte)(char1 | ((char8 & 0b1000000) << 1)));
            if(bytesToWrite > 1) encodingBuffer.put((byte)(char2 | ((char8 & 0b0100000) << 2)));
            if(bytesToWrite > 2) encodingBuffer.put((byte)(char3 | ((char8 & 0b0010000) << 3)));
            if(bytesToWrite > 3) encodingBuffer.put((byte)(char4 | ((char8 & 0b0001000) << 4)));
            if(bytesToWrite > 4) encodingBuffer.put((byte)(char5 | ((char8 & 0b0000100) << 5)));
            if(bytesToWrite > 5) encodingBuffer.put((byte)(char6 | ((char8 & 0b0000010) << 6)));
            if(bytesToWrite > 6) encodingBuffer.put((byte)(char7 | ((char8 & 0b0000001) << 7)));
        }

        encodingBuffer.flip();
        int dense7Length = encodingBuffer.remaining();

        // System.out.printf("Just encoded a string. The input was '%s', length %d. Encoded byte length is %d. First 3 bytes in buffer are: %d %d %d. %n",
        //     text, text.length(), dense7Length, encodingBuffer.get(), encodingBuffer.get(), encodingBuffer.get());

        // In most usages, the JVM should be able to eliminate this allocation via an escape analysis of the caller.
        return new Result(dense7Length, encodingBuffer.array());
    }

    private static final boolean isCharAsciiPrintable(char c) {
        return c >= ASCII_PRINTABLE_START && c <= ASCII_PRINTABLE_END;
    }

    private static final boolean isCharAsciiControlWhitespace(char c) {
        return c >= ASCII_CONTROL_WHITESPACE_START && c <= ASCII_CONTROL_WHITESPACE_END;
    }

    private static final boolean isCharHighSurrogate(char c) {
        return c >= 0xd800 && c <= 0xdbff;
    }

    private static final boolean isCharLowSurrogate(char c) {
        return c >= 0xdc00 && c <= 0xdfff;
    }

    private static final boolean isChar2ByteCodePoint(char c) {
        return c >= 0x0080 && c <= 0x07ff;
    }

    private static final boolean isChar3ByteCodePoint(char c) {
        return c >= 0x0800 && c <= 0xffff;
    }

    /**
     * Represents the result of a {@link Dense7StringEncoder#encode(String)} operation.
     */
    public static class Result {
        final private byte[] buffer;
        final private int encodedLength;

        public Result(int encodedLength, byte[] buffer) {
            this.encodedLength = encodedLength;
            this.buffer = buffer;
        }

        /**
         * Returns a byte array containing the encoded Dense7 bytes starting at index 0. This byte array is NOT
         * guaranteed to be the same length as the data it contains. Callers must use {@link #getEncodedLength()}
         * to determine the number of bytes that should be read from the byte array.
         *
         * @return the buffer containing Dense7 bytes.
         */
        public byte[] getBuffer() {
            return buffer;
        }

        /**
         * @return the number of encoded bytes in the array returned by {@link #getBuffer()}.
         */
        public int getEncodedLength() {
            return encodedLength;
        }
    }
}
