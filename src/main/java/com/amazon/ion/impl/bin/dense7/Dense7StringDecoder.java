package com.amazon.ion.impl.bin.dense7;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import com.amazon.ion.IonException;

/**
 * Decodes {@link String}s from UTF-8. Instances of this class are reusable but are NOT threadsafe.
 *
 * Instances are vended by {@link Dense7StringDecoderPool#getOrCreate()}.
 *
 * Users are expected to call {@link #close()} when the decoder is no longer needed.
 *
 * There are two ways of using this class:
 * <ol>
 *     <li>Use {@link #decode(ByteBuffer, int)} to decode the requested number of bytes from the given ByteBuffer in
 *     a single step. Or,</li>
 *     <li>Use the following sequence of method calls:
 *     <ol>
 *         <li>{@link #prepareDecode(int)} to prepare the decoder to decode the requested number of bytes.</li>
 *         <li>{@link #partialDecode(ByteBuffer, boolean)} to decode the available bytes from the byte buffer. This may
 *         be repeated as more bytes are made available in the ByteBuffer, which is the caller's responsibility.</li>
 *         <li>{@link #finishDecode()} to finish decoding and return the resulting String.</li>
 *     </ol>
 *     Note: {@link #decode(ByteBuffer, int)} must not be called between calls to {@link #prepareDecode(int)} and
 *     {@link #finishDecode()}.
 *     </li>
 * </ol>
 */
public class Dense7StringDecoder extends Poolable<Dense7StringDecoder> {

    private static final int ISOLATE_HIGH_BYTE = 0b100_0000;
    private static final int ISOLATE_HIGH_2    = 0b110_0000;
    private static final int ISOLATE_HIGH_3    = 0b111_0000;
    private static final int ISOLATE_HIGH_5    = 0b111_1100;

    private static final int DENSE7_CONTROL_WHITESPACE_START = 0b001_1001; // horizontal tab  (x09)
    private static final int DENSE7_CONTROL_WHITESPACE_END   = 0b001_1101; // carriage return (x0d)

    private static final int DENSE7_2_BYTE_START = 0;
    private static final int DENSE7_3_BYTE_START = 0b001_0000;
    private static final int DENSE7_4_BYTE_START = 0b001_1000;

    private static final int ASCII_CONTROL_WHITESPACE_START = 0x09; // horizontal tab

    // Add this value to an ASCII value to get the equivalent byte in Dense7
    private static final int ASCII_TO_DENSE7_CONTROL_WHITESPACE_OFFSET
        = DENSE7_CONTROL_WHITESPACE_START - ASCII_CONTROL_WHITESPACE_START;

    // The size of the UTF-8 decoding buffer.
    private static final int DENSE7_BUFFER_SIZE_IN_BYTES = 4 * 8192;

    private final CharBuffer reusableDense7DecodingBuffer;
    private CharBuffer dense7DecodingBuffer;

    // The 
    private int byteStage;
    private byte packedCodeUnit;
    private int inProgressCodePoint;
    private int continuationBytesRemaining;

    Dense7StringDecoder(Pool<Dense7StringDecoder> pool) {
        super(pool);
        reusableDense7DecodingBuffer = CharBuffer.allocate(DENSE7_BUFFER_SIZE_IN_BYTES);
        byteStage = 1;
        packedCodeUnit = 0;
        inProgressCodePoint = 0;
        continuationBytesRemaining = 0;
    }

    /**
     * Prepares the decoder to decode the given number of UTF-8 bytes.
     * @param numberOfBytes the number of bytes to decode.
     */
    public void prepareDecode(int numberOfBytes) {
        dense7DecodingBuffer = reusableDense7DecodingBuffer;
        // TODO: quick fix with *2
        if (numberOfBytes * 2 > reusableDense7DecodingBuffer.capacity()) {
            dense7DecodingBuffer = CharBuffer.allocate(numberOfBytes * 2);
        }
    }

    /**
     * Decodes the available bytes from the given ByteBuffer.
     * @param dense7InputBuffer a ByteBuffer containing UTF-8 bytes.
     * @param endOfInput true if the end of the UTF-8 sequence is expected to occur in the buffer; otherwise, false.
     */
    public void partialDecode(ByteBuffer dense7InputBuffer, boolean endOfInput) {
        // System.out.println("Decoding " + dense7InputBuffer.remaining() + " bytes.");
        final byte[] array = dense7InputBuffer.array();
        final int start = dense7InputBuffer.arrayOffset() + dense7InputBuffer.position();
        final int end = start + dense7InputBuffer.remaining();
        for(int i = start; i < end; i++) {
            final byte nextByte = array[i];// dense7InputBuffer.get();
            
            packedCodeUnit |= (nextByte & 0b10_00_00_00) >>> byteStage;
            byteStage++;
            
            processCodeUnit((byte) (nextByte & 0b111_1111));

            if(byteStage == 8) {
                processCodeUnit(packedCodeUnit);
                packedCodeUnit = 0;
                byteStage = 1;
            }
        }
    }

    private final void processCodeUnit(byte cu) {
        // System.out.printf("%x ", cu);
        if (continuationBytesRemaining > 0) {
            // System.out.printf("D7 continuation");
            inProgressCodePoint |= cu << (7 * (continuationBytesRemaining - 1));
            continuationBytesRemaining--;

            if(continuationBytesRemaining == 0) {
                // System.out.printf(" (decoded to %c)", (char) inProgressCodePoint);
                // System.out.printf("Got a supplementary code point %x\n", inProgressCodePoint);
                final char[] chars = Character.toChars(inProgressCodePoint);
                dense7DecodingBuffer.put(chars[0]);
                if(chars.length > 1) {
                    dense7DecodingBuffer.put(chars[1]);
                }
                // dense7DecodingBuffer.put((char) Character.highSurrogate(inProgressCodePoint));
                // dense7DecodingBuffer.put((char) Character.lowSurrogate (inProgressCodePoint));
                inProgressCodePoint = 0;
            }
            
            // System.out.printf("\n");
            return;
        }

        if      (isD7AsciiEquivalent(cu)) {
            // System.out.printf("D7 ASCII: %c\n", (char) cu);
            dense7DecodingBuffer.put((char) cu);
        }
        else if (isD7ControlWhitespace(cu)) {
            // System.out.printf("D7 control WS: %c\n", (char) (cu - ASCII_TO_DENSE7_CONTROL_WHITESPACE_OFFSET));
            dense7DecodingBuffer.put((char) (cu - ASCII_TO_DENSE7_CONTROL_WHITESPACE_OFFSET));
        }
        else if (isD7StartOf2ByteCodePoint(cu)) {
            // System.out.printf("D7 2 byte start\n");
            inProgressCodePoint |= cu << 7;
            continuationBytesRemaining = 1;
        }
        else if (isD7StartOf3ByteCodePoint(cu)) {
            // System.out.printf("D7 3 byte start\n");
            inProgressCodePoint |= (cu & 0b000_0011) << 14;
            continuationBytesRemaining = 2;
        }
        else if (isD7StartOf4ByteCodePoint(cu)) {
            // System.out.printf("D7 4 byte start\n");
            continuationBytesRemaining = 3;
        }
        else if (cu == 0b111_1111) {
            // System.out.printf("D7 null\n");
            dense7DecodingBuffer.put('\0');
        }
        else if (cu == 0b001_1111) {
            // System.out.printf("D7 u+0007\n");
            dense7DecodingBuffer.put('\u0007');
        }
        else if (cu == 0b001_1110) {
            // System.out.printf("D7 u+0008\n");
            dense7DecodingBuffer.put('\u0008');
        }
        else {
            throw new IonException(String.format("Cannot handle byte %x.", cu));
        }
    }

    /**
     * Finishes decoding and returns the resulting String.
     * @return the decoded Java String.
     */
    public String finishDecode() {
        byteStage = 1;
        packedCodeUnit = 0;
        inProgressCodePoint = 0;
        continuationBytesRemaining = 0;
        dense7DecodingBuffer.flip();
        return dense7DecodingBuffer.toString();
    }

    private static final boolean isD7AsciiEquivalent(byte b) {
        return (b & ISOLATE_HIGH_BYTE) == 0b100_0000 || (b & ISOLATE_HIGH_2) == 0b010_0000;
    }

    private static final boolean isD7StartOf2ByteCodePoint(byte b) {
        return (b & ISOLATE_HIGH_3) == DENSE7_2_BYTE_START;
    }

    private static final boolean isD7StartOf3ByteCodePoint(byte b) {
        return (b & ISOLATE_HIGH_5) == DENSE7_3_BYTE_START;
    }

    private static final boolean isD7StartOf4ByteCodePoint(byte b) {
        return b == DENSE7_4_BYTE_START;
    }

    private static final boolean isD7ControlWhitespace(byte b) {
        return b >= DENSE7_CONTROL_WHITESPACE_START && b <= DENSE7_CONTROL_WHITESPACE_END;
    }

    /**
     * Decodes the given number of UTF-8 bytes from the given ByteBuffer into a Java String.
     * @param dense7InputBuffer a ByteBuffer containing UTF-8 bytes.
     * @param numberOfBytes the number of bytes from the utf8InputBuffer to decode.
     * @return the decoded Java String.
     */
    public String decode(ByteBuffer dense7InputBuffer, int numberOfBytes) {        
        prepareDecode(numberOfBytes);

        dense7DecodingBuffer.position(0);
        dense7DecodingBuffer.limit(dense7DecodingBuffer.capacity());

        partialDecode(dense7InputBuffer, true);
        return finishDecode();
    }
}
