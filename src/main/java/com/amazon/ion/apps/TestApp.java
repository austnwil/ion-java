package com.amazon.ion.apps;

import java.nio.ByteBuffer;

import com.amazon.ion.impl.bin.dense6.Dense6StringDecoder;
import com.amazon.ion.impl.bin.dense6.Dense6StringDecoderPool;
import com.amazon.ion.impl.bin.dense6.Dense6StringEncoder;
import com.amazon.ion.impl.bin.dense6.Dense6StringEncoderPool;

public class TestApp {
    public static void main(String[] args) {
        // roundTripString("Hi. abc 123");
        // roundTripString("2025-09-15 19:50:32,745 [SystemProfileOSX] <WARNING> Failed to get MAC address with cmd:`['/sbin/ifconfig', 'utun1000']`, error:Command '['/sbin/ifconfig', 'utun1000']' returned non-zero exit status 1.");
        // roundTripString("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<root>\n<elem>hello</elem>\n</root>\n");
        // roundTripString("Ἀνέβην δέ με σῖτος εὐρυβίοιο Ἰλιάδης τε καὶ Ὀδυσσείας καὶ Φοινικίων");
        roundTripString("Love it! 😍❤️💕😻💖😍❤️💕😻💖😍❤️💕😻💖😍❤️💕😻💖😍❤️💕😻💖😍❤️💕😻💖");
        // roundTripString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`~!@#$%^&*()-_=+[{]}\\|;:'\",<.>/?");
    }

    private static void roundTripString(String string) {
        System.out.printf("\nPre-processed string: %s\n", string);
        
        final Dense6StringEncoder encoder = Dense6StringEncoderPool.getInstance().getOrCreate();
        final Dense6StringDecoder decoder = Dense6StringDecoderPool.getInstance().getOrCreate();

        final Dense6StringEncoder.Result encResult = encoder.encode(string);
        final int encodedByteLength = encResult.getEncodedLength();

        System.out.printf("Encoded string to %d bytes\n", encodedByteLength);
        final byte[] buffer = encResult.getBuffer();
        System.out.printf("Bytes: ");
        for(int i = 0; i < encodedByteLength; i++) {
            System.out.printf("%x ", buffer[i]);
        }
        System.out.printf("\n");

        final ByteBuffer decodeBuf = ByteBuffer.wrap(buffer, 0, encodedByteLength);

        final String decodedString = decoder.decode(decodeBuf, encodedByteLength);

        System.out.printf("Decoded string:       %s\n", decodedString);
    }
}
