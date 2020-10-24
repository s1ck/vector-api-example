package examples;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreamVByteTest {

    @Test
    void encode() {
        int[] input = {1, 3, 42, 1024};
        byte[] controlBytes = new byte[StreamVByte.controlBytesLen(input.length)];
        byte[] encoded = new byte[input.length * Integer.BYTES]; // worst case assumption

        var res = StreamVByte.scalar().encode(input, controlBytes, encoded);
        int numsEncoded = res.nums;
        int bytesWritten = res.bytes;

        assertEquals(4, numsEncoded);
        assertEquals(5, bytesWritten);
        assertArrayEquals(new byte[]{64}, controlBytes);
        assertArrayEquals(new byte[]{1, 3, 42, 0, 4}, Arrays.copyOf(encoded, bytesWritten));
    }

    @Test
    void decode() {
        int[] input = {1, 3, 42, 1_024_000};

        byte[] controlBytes = new byte[StreamVByte.controlBytesLen(input.length)];
        byte[] encoded = new byte[input.length * Integer.BYTES]; // worst case assumption
        int[] decoded = new int[input.length];

        StreamVByte.scalar().encode(input, controlBytes, encoded);

        var result = StreamVByte.scalar().decode(encoded, controlBytes, decoded);
        int numsDecoded = result.nums;
        int bytesRead = result.bytes;

        assertEquals(4, numsDecoded);
        assertEquals(6, bytesRead);
        assertArrayEquals(input, decoded);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void randoms(int size) {
        var r = new Random(42);
        int[] input = new int[size];
        for (int i = 0; i < size; i++) {
            input[i] = Math.abs(r.nextInt());
        }

        byte[] controlBytes = new byte[StreamVByte.controlBytesLen(input.length)];
        byte[] encoded = new byte[input.length * Integer.BYTES]; // worst case assumption
        int[] decoded = new int[input.length];

        StreamVByte.scalar().encode(input, controlBytes, encoded);
        var result = StreamVByte.scalar().decode(encoded, controlBytes, decoded);
        assertEquals(size, result.nums);
        assertArrayEquals(input, decoded);
    }
}