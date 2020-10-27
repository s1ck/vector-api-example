package examples;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;

import java.nio.ByteOrder;

public abstract class StreamVByte {

    public abstract Result encode(int[] input, byte[] controlBytes, byte[] output);

    public abstract Result decode(byte[] input, byte[] controlBytes, int[] output);

    public static StreamVByte scalar() {
        return new Scalar();
    }

    public static StreamVByte vectorized() {
        return new Vectorized();
    }

    private static class Scalar extends StreamVByte {

        @Override
        public Result encode(int[] input, byte[] controlBytes, byte[] output) {
            int numsEncoded = 0;
            int bytesWritten = 0;

            for (int quads_encoded = 0; quads_encoded < controlBytes.length; quads_encoded++) {
                var num0 = input[numsEncoded];
                var num1 = input[numsEncoded + 1];
                var num2 = input[numsEncoded + 2];
                var num3 = input[numsEncoded + 3];

                var len0 = encodeNum(num0, output, bytesWritten);
                var len1 = encodeNum(num1, output, bytesWritten + len0);
                var len2 = encodeNum(num2, output, bytesWritten + len0 + len1);
                var len3 = encodeNum(num3, output, bytesWritten + len0 + len1 + len2);

                controlBytes[quads_encoded] = (byte) ((len0 - 1) | (len1 - 1) << 2 | (len2 - 1) << 4 | (len3 - 1) << 6);

                bytesWritten += len0 + len1 + len2 + len3;
                numsEncoded += 4;
            }
            return new Result(numsEncoded, bytesWritten);
        }

        private static int encodeNum(int num, byte[] output, int offset) {
            // This will calculate 0 as taking 0 bytes, so ensure at least 1 byte
            int len = Math.max(1, 4 - Integer.numberOfLeadingZeros(num) / 8);

            if (len == 1) {
                output[offset] = (byte) (num & 0xFF);
            } else if (len == 2) {
                output[offset] = (byte) (num & 0xFF);
                output[offset + 1] = (byte) (num >> 8 & 0xFF);
            } else if (len == 3) {
                output[offset] = (byte) (num & 0xFF);
                output[offset + 1] = (byte) (num >> 8 & 0xFF);
                output[offset + 2] = (byte) (num >> 16 & 0xFF);
            } else {
                output[offset] = (byte) (num & 0xFF);
                output[offset + 1] = (byte) (num >> 8 & 0xFF);
                output[offset + 2] = (byte) (num >> 16 & 0xFF);
                output[offset + 3] = (byte) (num >> 24 & 0xFF);
            }

            return len;
        }

        @Override
        public Result decode(byte[] input, byte[] controlBytes, int[] output) {
            int bytesRead = 0;
            int numsDecoded = 0;
            int controlByteLimit = controlBytes.length;

            for (int i = 0; i < controlByteLimit; i++) {
                int controlByte = Byte.toUnsignedInt(controlBytes[i]);
                int len0 = DECODE_LENGTH_PER_NUM_TABLE[controlByte][0];
                int len1 = DECODE_LENGTH_PER_NUM_TABLE[controlByte][1];
                int len2 = DECODE_LENGTH_PER_NUM_TABLE[controlByte][2];
                int len3 = DECODE_LENGTH_PER_NUM_TABLE[controlByte][3];

                output[numsDecoded++] = decodeNum(len0, input, bytesRead);
                output[numsDecoded++] = decodeNum(len1, input, bytesRead + len0);
                output[numsDecoded++] = decodeNum(len2, input, bytesRead + len0 + len1);
                output[numsDecoded++] = decodeNum(len3, input, bytesRead + len0 + len1 + len2);

                bytesRead += len0 + len1 + len2 + len3;
            }

            return new Result(numsDecoded, bytesRead);
        }

        private static int decodeNum(int len, byte[] input, int offset) {
            int value = 0;

            if (len == 1) {
                value = input[offset];
            } else if (len == 2) {
                value += (input[offset] & 0xFF);
                value += (input[offset + 1] & 0xFF) << 8;
            } else if (len == 3) {
                value += (input[offset] & 0xFF);
                value += (input[offset + 1] & 0xFF) << 8;
                value += (input[offset + 2] & 0xFF) << 16;
            } else {
                value += (input[offset] & 0xFF);
                value += (input[offset + 1] & 0xFF) << 8;
                value += (input[offset + 2] & 0xFF) << 16;
                value += (input[offset + 3] & 0xFF) << 24;
            }
            return value;
        }

        private static final byte[][] DECODE_LENGTH_PER_NUM_TABLE = new byte[][]{
                new byte[]{1, 1, 1, 1}, // 0 = 0x0 = 0b00000000, lengths 1 1 1 1
                new byte[]{2, 1, 1, 1}, // 1 = 0x1 = 0b00000001, lengths 2 1 1 1
                new byte[]{3, 1, 1, 1}, // 2 = 0x2 = 0b00000010, lengths 3 1 1 1
                new byte[]{4, 1, 1, 1}, // 3 = 0x3 = 0b00000011, lengths 4 1 1 1
                new byte[]{1, 2, 1, 1}, // 4 = 0x4 = 0b00000100, lengths 1 2 1 1
                new byte[]{2, 2, 1, 1}, // 5 = 0x5 = 0b00000101, lengths 2 2 1 1
                new byte[]{3, 2, 1, 1}, // 6 = 0x6 = 0b00000110, lengths 3 2 1 1
                new byte[]{4, 2, 1, 1}, // 7 = 0x7 = 0b00000111, lengths 4 2 1 1
                new byte[]{1, 3, 1, 1}, // 8 = 0x8 = 0b00001000, lengths 1 3 1 1
                new byte[]{2, 3, 1, 1}, // 9 = 0x9 = 0b00001001, lengths 2 3 1 1
                new byte[]{3, 3, 1, 1}, // 10 = 0xA = 0b00001010, lengths 3 3 1 1
                new byte[]{4, 3, 1, 1}, // 11 = 0xB = 0b00001011, lengths 4 3 1 1
                new byte[]{1, 4, 1, 1}, // 12 = 0xC = 0b00001100, lengths 1 4 1 1
                new byte[]{2, 4, 1, 1}, // 13 = 0xD = 0b00001101, lengths 2 4 1 1
                new byte[]{3, 4, 1, 1}, // 14 = 0xE = 0b00001110, lengths 3 4 1 1
                new byte[]{4, 4, 1, 1}, // 15 = 0xF = 0b00001111, lengths 4 4 1 1
                new byte[]{1, 1, 2, 1}, // 16 = 0x10 = 0b00010000, lengths 1 1 2 1
                new byte[]{2, 1, 2, 1}, // 17 = 0x11 = 0b00010001, lengths 2 1 2 1
                new byte[]{3, 1, 2, 1}, // 18 = 0x12 = 0b00010010, lengths 3 1 2 1
                new byte[]{4, 1, 2, 1}, // 19 = 0x13 = 0b00010011, lengths 4 1 2 1
                new byte[]{1, 2, 2, 1}, // 20 = 0x14 = 0b00010100, lengths 1 2 2 1
                new byte[]{2, 2, 2, 1}, // 21 = 0x15 = 0b00010101, lengths 2 2 2 1
                new byte[]{3, 2, 2, 1}, // 22 = 0x16 = 0b00010110, lengths 3 2 2 1
                new byte[]{4, 2, 2, 1}, // 23 = 0x17 = 0b00010111, lengths 4 2 2 1
                new byte[]{1, 3, 2, 1}, // 24 = 0x18 = 0b00011000, lengths 1 3 2 1
                new byte[]{2, 3, 2, 1}, // 25 = 0x19 = 0b00011001, lengths 2 3 2 1
                new byte[]{3, 3, 2, 1}, // 26 = 0x1A = 0b00011010, lengths 3 3 2 1
                new byte[]{4, 3, 2, 1}, // 27 = 0x1B = 0b00011011, lengths 4 3 2 1
                new byte[]{1, 4, 2, 1}, // 28 = 0x1C = 0b00011100, lengths 1 4 2 1
                new byte[]{2, 4, 2, 1}, // 29 = 0x1D = 0b00011101, lengths 2 4 2 1
                new byte[]{3, 4, 2, 1}, // 30 = 0x1E = 0b00011110, lengths 3 4 2 1
                new byte[]{4, 4, 2, 1}, // 31 = 0x1F = 0b00011111, lengths 4 4 2 1
                new byte[]{1, 1, 3, 1}, // 32 = 0x20 = 0b00100000, lengths 1 1 3 1
                new byte[]{2, 1, 3, 1}, // 33 = 0x21 = 0b00100001, lengths 2 1 3 1
                new byte[]{3, 1, 3, 1}, // 34 = 0x22 = 0b00100010, lengths 3 1 3 1
                new byte[]{4, 1, 3, 1}, // 35 = 0x23 = 0b00100011, lengths 4 1 3 1
                new byte[]{1, 2, 3, 1}, // 36 = 0x24 = 0b00100100, lengths 1 2 3 1
                new byte[]{2, 2, 3, 1}, // 37 = 0x25 = 0b00100101, lengths 2 2 3 1
                new byte[]{3, 2, 3, 1}, // 38 = 0x26 = 0b00100110, lengths 3 2 3 1
                new byte[]{4, 2, 3, 1}, // 39 = 0x27 = 0b00100111, lengths 4 2 3 1
                new byte[]{1, 3, 3, 1}, // 40 = 0x28 = 0b00101000, lengths 1 3 3 1
                new byte[]{2, 3, 3, 1}, // 41 = 0x29 = 0b00101001, lengths 2 3 3 1
                new byte[]{3, 3, 3, 1}, // 42 = 0x2A = 0b00101010, lengths 3 3 3 1
                new byte[]{4, 3, 3, 1}, // 43 = 0x2B = 0b00101011, lengths 4 3 3 1
                new byte[]{1, 4, 3, 1}, // 44 = 0x2C = 0b00101100, lengths 1 4 3 1
                new byte[]{2, 4, 3, 1}, // 45 = 0x2D = 0b00101101, lengths 2 4 3 1
                new byte[]{3, 4, 3, 1}, // 46 = 0x2E = 0b00101110, lengths 3 4 3 1
                new byte[]{4, 4, 3, 1}, // 47 = 0x2F = 0b00101111, lengths 4 4 3 1
                new byte[]{1, 1, 4, 1}, // 48 = 0x30 = 0b00110000, lengths 1 1 4 1
                new byte[]{2, 1, 4, 1}, // 49 = 0x31 = 0b00110001, lengths 2 1 4 1
                new byte[]{3, 1, 4, 1}, // 50 = 0x32 = 0b00110010, lengths 3 1 4 1
                new byte[]{4, 1, 4, 1}, // 51 = 0x33 = 0b00110011, lengths 4 1 4 1
                new byte[]{1, 2, 4, 1}, // 52 = 0x34 = 0b00110100, lengths 1 2 4 1
                new byte[]{2, 2, 4, 1}, // 53 = 0x35 = 0b00110101, lengths 2 2 4 1
                new byte[]{3, 2, 4, 1}, // 54 = 0x36 = 0b00110110, lengths 3 2 4 1
                new byte[]{4, 2, 4, 1}, // 55 = 0x37 = 0b00110111, lengths 4 2 4 1
                new byte[]{1, 3, 4, 1}, // 56 = 0x38 = 0b00111000, lengths 1 3 4 1
                new byte[]{2, 3, 4, 1}, // 57 = 0x39 = 0b00111001, lengths 2 3 4 1
                new byte[]{3, 3, 4, 1}, // 58 = 0x3A = 0b00111010, lengths 3 3 4 1
                new byte[]{4, 3, 4, 1}, // 59 = 0x3B = 0b00111011, lengths 4 3 4 1
                new byte[]{1, 4, 4, 1}, // 60 = 0x3C = 0b00111100, lengths 1 4 4 1
                new byte[]{2, 4, 4, 1}, // 61 = 0x3D = 0b00111101, lengths 2 4 4 1
                new byte[]{3, 4, 4, 1}, // 62 = 0x3E = 0b00111110, lengths 3 4 4 1
                new byte[]{4, 4, 4, 1}, // 63 = 0x3F = 0b00111111, lengths 4 4 4 1
                new byte[]{1, 1, 1, 2}, // 64 = 0x40 = 0b01000000, lengths 1 1 1 2
                new byte[]{2, 1, 1, 2}, // 65 = 0x41 = 0b01000001, lengths 2 1 1 2
                new byte[]{3, 1, 1, 2}, // 66 = 0x42 = 0b01000010, lengths 3 1 1 2
                new byte[]{4, 1, 1, 2}, // 67 = 0x43 = 0b01000011, lengths 4 1 1 2
                new byte[]{1, 2, 1, 2}, // 68 = 0x44 = 0b01000100, lengths 1 2 1 2
                new byte[]{2, 2, 1, 2}, // 69 = 0x45 = 0b01000101, lengths 2 2 1 2
                new byte[]{3, 2, 1, 2}, // 70 = 0x46 = 0b01000110, lengths 3 2 1 2
                new byte[]{4, 2, 1, 2}, // 71 = 0x47 = 0b01000111, lengths 4 2 1 2
                new byte[]{1, 3, 1, 2}, // 72 = 0x48 = 0b01001000, lengths 1 3 1 2
                new byte[]{2, 3, 1, 2}, // 73 = 0x49 = 0b01001001, lengths 2 3 1 2
                new byte[]{3, 3, 1, 2}, // 74 = 0x4A = 0b01001010, lengths 3 3 1 2
                new byte[]{4, 3, 1, 2}, // 75 = 0x4B = 0b01001011, lengths 4 3 1 2
                new byte[]{1, 4, 1, 2}, // 76 = 0x4C = 0b01001100, lengths 1 4 1 2
                new byte[]{2, 4, 1, 2}, // 77 = 0x4D = 0b01001101, lengths 2 4 1 2
                new byte[]{3, 4, 1, 2}, // 78 = 0x4E = 0b01001110, lengths 3 4 1 2
                new byte[]{4, 4, 1, 2}, // 79 = 0x4F = 0b01001111, lengths 4 4 1 2
                new byte[]{1, 1, 2, 2}, // 80 = 0x50 = 0b01010000, lengths 1 1 2 2
                new byte[]{2, 1, 2, 2}, // 81 = 0x51 = 0b01010001, lengths 2 1 2 2
                new byte[]{3, 1, 2, 2}, // 82 = 0x52 = 0b01010010, lengths 3 1 2 2
                new byte[]{4, 1, 2, 2}, // 83 = 0x53 = 0b01010011, lengths 4 1 2 2
                new byte[]{1, 2, 2, 2}, // 84 = 0x54 = 0b01010100, lengths 1 2 2 2
                new byte[]{2, 2, 2, 2}, // 85 = 0x55 = 0b01010101, lengths 2 2 2 2
                new byte[]{3, 2, 2, 2}, // 86 = 0x56 = 0b01010110, lengths 3 2 2 2
                new byte[]{4, 2, 2, 2}, // 87 = 0x57 = 0b01010111, lengths 4 2 2 2
                new byte[]{1, 3, 2, 2}, // 88 = 0x58 = 0b01011000, lengths 1 3 2 2
                new byte[]{2, 3, 2, 2}, // 89 = 0x59 = 0b01011001, lengths 2 3 2 2
                new byte[]{3, 3, 2, 2}, // 90 = 0x5A = 0b01011010, lengths 3 3 2 2
                new byte[]{4, 3, 2, 2}, // 91 = 0x5B = 0b01011011, lengths 4 3 2 2
                new byte[]{1, 4, 2, 2}, // 92 = 0x5C = 0b01011100, lengths 1 4 2 2
                new byte[]{2, 4, 2, 2}, // 93 = 0x5D = 0b01011101, lengths 2 4 2 2
                new byte[]{3, 4, 2, 2}, // 94 = 0x5E = 0b01011110, lengths 3 4 2 2
                new byte[]{4, 4, 2, 2}, // 95 = 0x5F = 0b01011111, lengths 4 4 2 2
                new byte[]{1, 1, 3, 2}, // 96 = 0x60 = 0b01100000, lengths 1 1 3 2
                new byte[]{2, 1, 3, 2}, // 97 = 0x61 = 0b01100001, lengths 2 1 3 2
                new byte[]{3, 1, 3, 2}, // 98 = 0x62 = 0b01100010, lengths 3 1 3 2
                new byte[]{4, 1, 3, 2}, // 99 = 0x63 = 0b01100011, lengths 4 1 3 2
                new byte[]{1, 2, 3, 2}, // 100 = 0x64 = 0b01100100, lengths 1 2 3 2
                new byte[]{2, 2, 3, 2}, // 101 = 0x65 = 0b01100101, lengths 2 2 3 2
                new byte[]{3, 2, 3, 2}, // 102 = 0x66 = 0b01100110, lengths 3 2 3 2
                new byte[]{4, 2, 3, 2}, // 103 = 0x67 = 0b01100111, lengths 4 2 3 2
                new byte[]{1, 3, 3, 2}, // 104 = 0x68 = 0b01101000, lengths 1 3 3 2
                new byte[]{2, 3, 3, 2}, // 105 = 0x69 = 0b01101001, lengths 2 3 3 2
                new byte[]{3, 3, 3, 2}, // 106 = 0x6A = 0b01101010, lengths 3 3 3 2
                new byte[]{4, 3, 3, 2}, // 107 = 0x6B = 0b01101011, lengths 4 3 3 2
                new byte[]{1, 4, 3, 2}, // 108 = 0x6C = 0b01101100, lengths 1 4 3 2
                new byte[]{2, 4, 3, 2}, // 109 = 0x6D = 0b01101101, lengths 2 4 3 2
                new byte[]{3, 4, 3, 2}, // 110 = 0x6E = 0b01101110, lengths 3 4 3 2
                new byte[]{4, 4, 3, 2}, // 111 = 0x6F = 0b01101111, lengths 4 4 3 2
                new byte[]{1, 1, 4, 2}, // 112 = 0x70 = 0b01110000, lengths 1 1 4 2
                new byte[]{2, 1, 4, 2}, // 113 = 0x71 = 0b01110001, lengths 2 1 4 2
                new byte[]{3, 1, 4, 2}, // 114 = 0x72 = 0b01110010, lengths 3 1 4 2
                new byte[]{4, 1, 4, 2}, // 115 = 0x73 = 0b01110011, lengths 4 1 4 2
                new byte[]{1, 2, 4, 2}, // 116 = 0x74 = 0b01110100, lengths 1 2 4 2
                new byte[]{2, 2, 4, 2}, // 117 = 0x75 = 0b01110101, lengths 2 2 4 2
                new byte[]{3, 2, 4, 2}, // 118 = 0x76 = 0b01110110, lengths 3 2 4 2
                new byte[]{4, 2, 4, 2}, // 119 = 0x77 = 0b01110111, lengths 4 2 4 2
                new byte[]{1, 3, 4, 2}, // 120 = 0x78 = 0b01111000, lengths 1 3 4 2
                new byte[]{2, 3, 4, 2}, // 121 = 0x79 = 0b01111001, lengths 2 3 4 2
                new byte[]{3, 3, 4, 2}, // 122 = 0x7A = 0b01111010, lengths 3 3 4 2
                new byte[]{4, 3, 4, 2}, // 123 = 0x7B = 0b01111011, lengths 4 3 4 2
                new byte[]{1, 4, 4, 2}, // 124 = 0x7C = 0b01111100, lengths 1 4 4 2
                new byte[]{2, 4, 4, 2}, // 125 = 0x7D = 0b01111101, lengths 2 4 4 2
                new byte[]{3, 4, 4, 2}, // 126 = 0x7E = 0b01111110, lengths 3 4 4 2
                new byte[]{4, 4, 4, 2}, // 127 = 0x7F = 0b01111111, lengths 4 4 4 2
                new byte[]{1, 1, 1, 3}, // -127 = 0x80 = 0b10000000, lengths 1 1 1 3
                new byte[]{2, 1, 1, 3}, // 129 = 0x81 = 0b10000001, lengths 2 1 1 3
                new byte[]{3, 1, 1, 3}, // 130 = 0x82 = 0b10000010, lengths 3 1 1 3
                new byte[]{4, 1, 1, 3}, // 131 = 0x83 = 0b10000011, lengths 4 1 1 3
                new byte[]{1, 2, 1, 3}, // 132 = 0x84 = 0b10000100, lengths 1 2 1 3
                new byte[]{2, 2, 1, 3}, // 133 = 0x85 = 0b10000101, lengths 2 2 1 3
                new byte[]{3, 2, 1, 3}, // 134 = 0x86 = 0b10000110, lengths 3 2 1 3
                new byte[]{4, 2, 1, 3}, // 135 = 0x87 = 0b10000111, lengths 4 2 1 3
                new byte[]{1, 3, 1, 3}, // 136 = 0x88 = 0b10001000, lengths 1 3 1 3
                new byte[]{2, 3, 1, 3}, // 137 = 0x89 = 0b10001001, lengths 2 3 1 3
                new byte[]{3, 3, 1, 3}, // 138 = 0x8A = 0b10001010, lengths 3 3 1 3
                new byte[]{4, 3, 1, 3}, // 139 = 0x8B = 0b10001011, lengths 4 3 1 3
                new byte[]{1, 4, 1, 3}, // 140 = 0x8C = 0b10001100, lengths 1 4 1 3
                new byte[]{2, 4, 1, 3}, // 141 = 0x8D = 0b10001101, lengths 2 4 1 3
                new byte[]{3, 4, 1, 3}, // 142 = 0x8E = 0b10001110, lengths 3 4 1 3
                new byte[]{4, 4, 1, 3}, // 143 = 0x8F = 0b10001111, lengths 4 4 1 3
                new byte[]{1, 1, 2, 3}, // 144 = 0x90 = 0b10010000, lengths 1 1 2 3
                new byte[]{2, 1, 2, 3}, // 145 = 0x91 = 0b10010001, lengths 2 1 2 3
                new byte[]{3, 1, 2, 3}, // 146 = 0x92 = 0b10010010, lengths 3 1 2 3
                new byte[]{4, 1, 2, 3}, // 147 = 0x93 = 0b10010011, lengths 4 1 2 3
                new byte[]{1, 2, 2, 3}, // 148 = 0x94 = 0b10010100, lengths 1 2 2 3
                new byte[]{2, 2, 2, 3}, // 149 = 0x95 = 0b10010101, lengths 2 2 2 3
                new byte[]{3, 2, 2, 3}, // 150 = 0x96 = 0b10010110, lengths 3 2 2 3
                new byte[]{4, 2, 2, 3}, // 151 = 0x97 = 0b10010111, lengths 4 2 2 3
                new byte[]{1, 3, 2, 3}, // 152 = 0x98 = 0b10011000, lengths 1 3 2 3
                new byte[]{2, 3, 2, 3}, // 153 = 0x99 = 0b10011001, lengths 2 3 2 3
                new byte[]{3, 3, 2, 3}, // 154 = 0x9A = 0b10011010, lengths 3 3 2 3
                new byte[]{4, 3, 2, 3}, // 155 = 0x9B = 0b10011011, lengths 4 3 2 3
                new byte[]{1, 4, 2, 3}, // 156 = 0x9C = 0b10011100, lengths 1 4 2 3
                new byte[]{2, 4, 2, 3}, // 157 = 0x9D = 0b10011101, lengths 2 4 2 3
                new byte[]{3, 4, 2, 3}, // 158 = 0x9E = 0b10011110, lengths 3 4 2 3
                new byte[]{4, 4, 2, 3}, // 159 = 0x9F = 0b10011111, lengths 4 4 2 3
                new byte[]{1, 1, 3, 3}, // 160 = 0xA0 = 0b10100000, lengths 1 1 3 3
                new byte[]{2, 1, 3, 3}, // 161 = 0xA1 = 0b10100001, lengths 2 1 3 3
                new byte[]{3, 1, 3, 3}, // 162 = 0xA2 = 0b10100010, lengths 3 1 3 3
                new byte[]{4, 1, 3, 3}, // 163 = 0xA3 = 0b10100011, lengths 4 1 3 3
                new byte[]{1, 2, 3, 3}, // 164 = 0xA4 = 0b10100100, lengths 1 2 3 3
                new byte[]{2, 2, 3, 3}, // 165 = 0xA5 = 0b10100101, lengths 2 2 3 3
                new byte[]{3, 2, 3, 3}, // 166 = 0xA6 = 0b10100110, lengths 3 2 3 3
                new byte[]{4, 2, 3, 3}, // 167 = 0xA7 = 0b10100111, lengths 4 2 3 3
                new byte[]{1, 3, 3, 3}, // 168 = 0xA8 = 0b10101000, lengths 1 3 3 3
                new byte[]{2, 3, 3, 3}, // 169 = 0xA9 = 0b10101001, lengths 2 3 3 3
                new byte[]{3, 3, 3, 3}, // 170 = 0xAA = 0b10101010, lengths 3 3 3 3
                new byte[]{4, 3, 3, 3}, // 171 = 0xAB = 0b10101011, lengths 4 3 3 3
                new byte[]{1, 4, 3, 3}, // 172 = 0xAC = 0b10101100, lengths 1 4 3 3
                new byte[]{2, 4, 3, 3}, // 173 = 0xAD = 0b10101101, lengths 2 4 3 3
                new byte[]{3, 4, 3, 3}, // 174 = 0xAE = 0b10101110, lengths 3 4 3 3
                new byte[]{4, 4, 3, 3}, // 175 = 0xAF = 0b10101111, lengths 4 4 3 3
                new byte[]{1, 1, 4, 3}, // 176 = 0xB0 = 0b10110000, lengths 1 1 4 3
                new byte[]{2, 1, 4, 3}, // 177 = 0xB1 = 0b10110001, lengths 2 1 4 3
                new byte[]{3, 1, 4, 3}, // 178 = 0xB2 = 0b10110010, lengths 3 1 4 3
                new byte[]{4, 1, 4, 3}, // 179 = 0xB3 = 0b10110011, lengths 4 1 4 3
                new byte[]{1, 2, 4, 3}, // 180 = 0xB4 = 0b10110100, lengths 1 2 4 3
                new byte[]{2, 2, 4, 3}, // 181 = 0xB5 = 0b10110101, lengths 2 2 4 3
                new byte[]{3, 2, 4, 3}, // 182 = 0xB6 = 0b10110110, lengths 3 2 4 3
                new byte[]{4, 2, 4, 3}, // 183 = 0xB7 = 0b10110111, lengths 4 2 4 3
                new byte[]{1, 3, 4, 3}, // 184 = 0xB8 = 0b10111000, lengths 1 3 4 3
                new byte[]{2, 3, 4, 3}, // 185 = 0xB9 = 0b10111001, lengths 2 3 4 3
                new byte[]{3, 3, 4, 3}, // 186 = 0xBA = 0b10111010, lengths 3 3 4 3
                new byte[]{4, 3, 4, 3}, // 187 = 0xBB = 0b10111011, lengths 4 3 4 3
                new byte[]{1, 4, 4, 3}, // 188 = 0xBC = 0b10111100, lengths 1 4 4 3
                new byte[]{2, 4, 4, 3}, // 189 = 0xBD = 0b10111101, lengths 2 4 4 3
                new byte[]{3, 4, 4, 3}, // 190 = 0xBE = 0b10111110, lengths 3 4 4 3
                new byte[]{4, 4, 4, 3}, // 191 = 0xBF = 0b10111111, lengths 4 4 4 3
                new byte[]{1, 1, 1, 4}, // 192 = 0xC0 = 0b11000000, lengths 1 1 1 4
                new byte[]{2, 1, 1, 4}, // 193 = 0xC1 = 0b11000001, lengths 2 1 1 4
                new byte[]{3, 1, 1, 4}, // 194 = 0xC2 = 0b11000010, lengths 3 1 1 4
                new byte[]{4, 1, 1, 4}, // 195 = 0xC3 = 0b11000011, lengths 4 1 1 4
                new byte[]{1, 2, 1, 4}, // 196 = 0xC4 = 0b11000100, lengths 1 2 1 4
                new byte[]{2, 2, 1, 4}, // 197 = 0xC5 = 0b11000101, lengths 2 2 1 4
                new byte[]{3, 2, 1, 4}, // 198 = 0xC6 = 0b11000110, lengths 3 2 1 4
                new byte[]{4, 2, 1, 4}, // 199 = 0xC7 = 0b11000111, lengths 4 2 1 4
                new byte[]{1, 3, 1, 4}, // 200 = 0xC8 = 0b11001000, lengths 1 3 1 4
                new byte[]{2, 3, 1, 4}, // 201 = 0xC9 = 0b11001001, lengths 2 3 1 4
                new byte[]{3, 3, 1, 4}, // 202 = 0xCA = 0b11001010, lengths 3 3 1 4
                new byte[]{4, 3, 1, 4}, // 203 = 0xCB = 0b11001011, lengths 4 3 1 4
                new byte[]{1, 4, 1, 4}, // 204 = 0xCC = 0b11001100, lengths 1 4 1 4
                new byte[]{2, 4, 1, 4}, // 205 = 0xCD = 0b11001101, lengths 2 4 1 4
                new byte[]{3, 4, 1, 4}, // 206 = 0xCE = 0b11001110, lengths 3 4 1 4
                new byte[]{4, 4, 1, 4}, // 207 = 0xCF = 0b11001111, lengths 4 4 1 4
                new byte[]{1, 1, 2, 4}, // 208 = 0xD0 = 0b11010000, lengths 1 1 2 4
                new byte[]{2, 1, 2, 4}, // 209 = 0xD1 = 0b11010001, lengths 2 1 2 4
                new byte[]{3, 1, 2, 4}, // 210 = 0xD2 = 0b11010010, lengths 3 1 2 4
                new byte[]{4, 1, 2, 4}, // 211 = 0xD3 = 0b11010011, lengths 4 1 2 4
                new byte[]{1, 2, 2, 4}, // 212 = 0xD4 = 0b11010100, lengths 1 2 2 4
                new byte[]{2, 2, 2, 4}, // 213 = 0xD5 = 0b11010101, lengths 2 2 2 4
                new byte[]{3, 2, 2, 4}, // 214 = 0xD6 = 0b11010110, lengths 3 2 2 4
                new byte[]{4, 2, 2, 4}, // 215 = 0xD7 = 0b11010111, lengths 4 2 2 4
                new byte[]{1, 3, 2, 4}, // 216 = 0xD8 = 0b11011000, lengths 1 3 2 4
                new byte[]{2, 3, 2, 4}, // 217 = 0xD9 = 0b11011001, lengths 2 3 2 4
                new byte[]{3, 3, 2, 4}, // 218 = 0xDA = 0b11011010, lengths 3 3 2 4
                new byte[]{4, 3, 2, 4}, // 219 = 0xDB = 0b11011011, lengths 4 3 2 4
                new byte[]{1, 4, 2, 4}, // 220 = 0xDC = 0b11011100, lengths 1 4 2 4
                new byte[]{2, 4, 2, 4}, // 221 = 0xDD = 0b11011101, lengths 2 4 2 4
                new byte[]{3, 4, 2, 4}, // 222 = 0xDE = 0b11011110, lengths 3 4 2 4
                new byte[]{4, 4, 2, 4}, // 223 = 0xDF = 0b11011111, lengths 4 4 2 4
                new byte[]{1, 1, 3, 4}, // 224 = 0xE0 = 0b11100000, lengths 1 1 3 4
                new byte[]{2, 1, 3, 4}, // 225 = 0xE1 = 0b11100001, lengths 2 1 3 4
                new byte[]{3, 1, 3, 4}, // 226 = 0xE2 = 0b11100010, lengths 3 1 3 4
                new byte[]{4, 1, 3, 4}, // 227 = 0xE3 = 0b11100011, lengths 4 1 3 4
                new byte[]{1, 2, 3, 4}, // 228 = 0xE4 = 0b11100100, lengths 1 2 3 4
                new byte[]{2, 2, 3, 4}, // 229 = 0xE5 = 0b11100101, lengths 2 2 3 4
                new byte[]{3, 2, 3, 4}, // 230 = 0xE6 = 0b11100110, lengths 3 2 3 4
                new byte[]{4, 2, 3, 4}, // 231 = 0xE7 = 0b11100111, lengths 4 2 3 4
                new byte[]{1, 3, 3, 4}, // 232 = 0xE8 = 0b11101000, lengths 1 3 3 4
                new byte[]{2, 3, 3, 4}, // 233 = 0xE9 = 0b11101001, lengths 2 3 3 4
                new byte[]{3, 3, 3, 4}, // 234 = 0xEA = 0b11101010, lengths 3 3 3 4
                new byte[]{4, 3, 3, 4}, // 235 = 0xEB = 0b11101011, lengths 4 3 3 4
                new byte[]{1, 4, 3, 4}, // 236 = 0xEC = 0b11101100, lengths 1 4 3 4
                new byte[]{2, 4, 3, 4}, // 237 = 0xED = 0b11101101, lengths 2 4 3 4
                new byte[]{3, 4, 3, 4}, // 238 = 0xEE = 0b11101110, lengths 3 4 3 4
                new byte[]{4, 4, 3, 4}, // 239 = 0xEF = 0b11101111, lengths 4 4 3 4
                new byte[]{1, 1, 4, 4}, // 240 = 0xF0 = 0b11110000, lengths 1 1 4 4
                new byte[]{2, 1, 4, 4}, // 241 = 0xF1 = 0b11110001, lengths 2 1 4 4
                new byte[]{3, 1, 4, 4}, // 242 = 0xF2 = 0b11110010, lengths 3 1 4 4
                new byte[]{4, 1, 4, 4}, // 243 = 0xF3 = 0b11110011, lengths 4 1 4 4
                new byte[]{1, 2, 4, 4}, // 244 = 0xF4 = 0b11110100, lengths 1 2 4 4
                new byte[]{2, 2, 4, 4}, // 245 = 0xF5 = 0b11110101, lengths 2 2 4 4
                new byte[]{3, 2, 4, 4}, // 246 = 0xF6 = 0b11110110, lengths 3 2 4 4
                new byte[]{4, 2, 4, 4}, // 247 = 0xF7 = 0b11110111, lengths 4 2 4 4
                new byte[]{1, 3, 4, 4}, // 248 = 0xF8 = 0b11111000, lengths 1 3 4 4
                new byte[]{2, 3, 4, 4}, // 249 = 0xF9 = 0b11111001, lengths 2 3 4 4
                new byte[]{3, 3, 4, 4}, // 250 = 0xFA = 0b11111010, lengths 3 3 4 4
                new byte[]{4, 3, 4, 4}, // 251 = 0xFB = 0b11111011, lengths 4 3 4 4
                new byte[]{1, 4, 4, 4}, // 252 = 0xFC = 0b11111100, lengths 1 4 4 4
                new byte[]{2, 4, 4, 4}, // 253 = 0xFD = 0b11111101, lengths 2 4 4 4
                new byte[]{3, 4, 4, 4}, // 254 = 0xFE = 0b11111110, lengths 3 4 4 4
                new byte[]{4, 4, 4, 4} // 255 = 0xFF = 0b11111111, lengths 4 4 4 4
        };
    }

    private static class Vectorized extends StreamVByte {

        private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
        private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_128;

        private static final ByteVector ZEROES = ByteVector.zero(SPECIES);
        private static final ByteVector ONES = ByteVector.broadcast(SPECIES, 1);

        private static final ByteVector LANE_CODES = ByteVector.fromArray(SPECIES,
            new byte[] {0, 3, 2, 3, 1, 3, 2, 3, -127, -127, -127, -127, -127, -127, -127, -127},
            0
        );
        private static final VectorShuffle<Byte> GATHER_HI = ByteVector.fromArray(SPECIES,
            new byte[] {15, 11, 7, 3, 15, 11, 7, 3, 0, 0, 0, 0, 0, 0, 0, 0},
            0
        ).toShuffle();

        private static final IntVector SHIFTS = IntVector.broadcast(INT_SPECIES, 1 | 1 << 9 | 1 << 18);

        private static final IntVector AGGREGATORS = IntVector.fromArray(
            INT_SPECIES,
            new int[] {1 | 1 << 10 | 1 << 20 | 1 << 30, 1 | 1 << 8 | 1 << 16 | 1 << 24, 0, 0},
            0
        );

        @Override
        public Result encode(int[] input, byte[] controlBytes, byte[] output) {
            var nums_encoded = 0;
            var bytes_encoded = 0;

            // Encoding writes 16 bytes at a time, but if numbers are encoded with 1 byte each, that
            // means the last 3 quads could write past what is actually necessary. So, don't process
            // the last few control bytes.
//            var control_byte_limit =  control_bytes.len().saturating_sub(3);

            for (var controlByteIndex = 0; controlByteIndex < controlBytes.length; controlByteIndex++) {

                // let to_encode = simd::u8x16::from(unsafe {
                //     _mm_loadu_si-127(input[nums_encoded..(nums_encoded + 4)].as_ptr()
                //         as *const __m-127i)
                // });
                var to_encode = IntVector.fromArray(INT_SPECIES, input, nums_encoded).reinterpretAsBytes();

                // clamp each byte to 1 if nonzero
                // let mins = simd::i32x4::from(unsafe { _mm_min_epu8(to_encode, ones) });
                var mins = to_encode.min(ONES).max(ZEROES).or(to_encode.lanewise(VectorOperators.LSHR, 1).min(ONES));

                // Apply shifts to clamped bytes. e.g. u32::max_value() would be (little endian):
                // 00000001 00000001 00000001 00000001
                // and after multiplication aka shifting:
                // 00000001 00000011 00000111 00000111
                // 1 << 16 | 1 would be:
                // 00000001 00000000 00000001 00000000
                // and shifted:
                // 00000001 00000010 00000101 00000010
                // At most the bottom 3 bits of each byte will be set by shifting.
                // What we care about is the bottom 3 bits of the high byte in each num.
                // A 1-byte number (clamped to 0x01000000) will accumulate to 0x00 in the top byte
                // because there isn't a 3-byte shift to get that set bit into the top byte.
                // A 2-byte number (clamped to 0x00010000) will accumulate to 0x04 in the top byte
                // because the set bit would have been shifted 2 bytes + 2 bits higher.
                // A 3-byte number will have the 0x02 bit set in the top byte, and possibly the 0x04
                // bit set as well if the 2nd byte was non-zero.
                // A 4-byte number will have the 0x01 bit set in the top byte, and possibly 0x02 and
                // 0x04.
                // In summary, byte lengths -> high byte:
                // 1-byte -> 0x00
                // 2-byte -> 0x04
                // 3-byte -> 0x02, 0x06
                // 4-byte -> 0x01, 0x05, 0x03, 0x07
                // let bytemaps = simd::u8x16::from(unsafe { _mm_mullo_epi32(mins, shifts) });
                var byteMaps = mins.reinterpretAsInts().mul(SHIFTS).reinterpretAsBytes();

                // Map high bytes to the corresponding lane codes. (Other bytes are mapped as well
                // but are not used.)
                // let shuffled_lanecodes = unsafe { _mm_shuffle_epi8(lanecodes, bytemaps) };
                var shuffled_lanecodes = LANE_CODES.rearrange(byteMaps.toShuffle());

                // Assemble 2 copies of the high byte from each of the 4 numbers.
                // The first copy will be used to calculate the control byte, the second the length.
                // let hi_bytes =
                //     simd::i32x4::from(unsafe { _mm_shuffle_epi8(shuffled_lanecodes, gather_hi) });
                var hi_bytes = shuffled_lanecodes.rearrange(GATHER_HI).reinterpretAsInts();

                // use CONCAT to shift the lane code bits from bytes 0-3 into 1 byte (byte 3)
                // use SUM to sum lane code bits from bytes 4-7 into 1 byte (byte 7)
                // let code_and_length = unsafe { _mm_mullo_epi32(hi_bytes, aggregators) };
                var code_and_length = hi_bytes.mul(AGGREGATORS);

                // let bytes = simd::u8x16::from(code_and_length);
                var bytes = code_and_length.reinterpretAsBytes();
                // let code = bytes.extract(3);
                var code = bytes.lane(3);
                // let length = bytes.extract(7) + 4;
                var length = bytes.lane(7) + 4;

                // let mask_bytes = tables::X86_ENCODE_SHUFFLE_TABLE[code as usize];
                var mask_bytes = SHUFFLE_ENCODE[code & 0xFF];
                // let encode_mask = simd::u8x16::from(unsafe {
                //     _mm_loadu_si-127(mask_bytes.as_ptr() as *const __m-127i)
                // });
                var encode_mask = ByteVector.fromArray(SPECIES, mask_bytes, 0).toShuffle();

//                let encoded = unsafe { _mm_shuffle_epi8(to_encode, encode_mask) };
                var encoded = to_encode.rearrange(encode_mask);

                // _mm_storeu_si-127(
                //     output[bytes_encoded..(bytes_encoded + 16)].as_ptr() as *mut __m-127i,
                //     simd::i8x16::from(encoded),
                // );
                encoded.intoByteArray(output, bytes_encoded, ByteOrder.LITTLE_ENDIAN);
                controlBytes[controlByteIndex] = code;

                bytes_encoded += length;
                nums_encoded += 4;
            }

            return new Result(nums_encoded, bytes_encoded);
        }

        private static final byte[][] SHUFFLE_ENCODE = new byte[][] {
    // 0 = 0x0 = 0b00000000, lengths 1 1 1 1
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2, -127, -127, -127,   3, -127, -127, -127 },
    // 1 = 0x1 = 0b00000001, lengths 2 1 1 1
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3, -127, -127, -127,   4, -127, -127, -127 },
    // 2 = 0x2 = 0b00000010, lengths 3 1 1 1
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4, -127, -127, -127,   5, -127, -127, -127 },
    // 3 = 0x3 = 0b00000011, lengths 4 1 1 1
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5, -127, -127, -127,   6, -127, -127, -127 },
    // 4 = 0x4 = 0b00000100, lengths 1 2 1 1
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3, -127, -127, -127,   4, -127, -127, -127 },
    // 5 = 0x5 = 0b00000101, lengths 2 2 1 1
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4, -127, -127, -127,   5, -127, -127, -127 },
    // 6 = 0x6 = 0b00000110, lengths 3 2 1 1
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5, -127, -127, -127,   6, -127, -127, -127 },
    // 7 = 0x7 = 0b00000111, lengths 4 2 1 1
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6, -127, -127, -127,   7, -127, -127, -127 },
    // 8 = 0x8 = 0b00001000, lengths 1 3 1 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4, -127, -127, -127,   5, -127, -127, -127 },
    // 9 = 0x9 = 0b00001001, lengths 2 3 1 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5, -127, -127, -127,   6, -127, -127, -127 },
    // 10 = 0xA = 0b00001010, lengths 3 3 1 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6, -127, -127, -127,   7, -127, -127, -127 },
    // 11 = 0xB = 0b00001011, lengths 4 3 1 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7, -127, -127, -127,   8, -127, -127, -127 },
    // 12 = 0xC = 0b00001100, lengths 1 4 1 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5, -127, -127, -127,   6, -127, -127, -127 },
    // 13 = 0xD = 0b00001101, lengths 2 4 1 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6, -127, -127, -127,   7, -127, -127, -127 },
    // 14 = 0xE = 0b00001110, lengths 3 4 1 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7, -127, -127, -127,   8, -127, -127, -127 },
    // 15 = 0xF = 0b00001111, lengths 4 4 1 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8, -127, -127, -127,   9, -127, -127, -127 },
    // 16 = 0x10 = 0b00010000, lengths 1 1 2 1
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3, -127, -127,   4, -127, -127, -127 },
    // 17 = 0x11 = 0b00010001, lengths 2 1 2 1
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4, -127, -127,   5, -127, -127, -127 },
    // 18 = 0x12 = 0b00010010, lengths 3 1 2 1
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5, -127, -127,   6, -127, -127, -127 },
    // 19 = 0x13 = 0b00010011, lengths 4 1 2 1
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6, -127, -127,   7, -127, -127, -127 },
    // 20 = 0x14 = 0b00010100, lengths 1 2 2 1
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4, -127, -127,   5, -127, -127, -127 },
    // 21 = 0x15 = 0b00010101, lengths 2 2 2 1
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5, -127, -127,   6, -127, -127, -127 },
    // 22 = 0x16 = 0b00010110, lengths 3 2 2 1
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6, -127, -127,   7, -127, -127, -127 },
    // 23 = 0x17 = 0b00010111, lengths 4 2 2 1
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7, -127, -127,   8, -127, -127, -127 },
    // 24 = 0x18 = 0b00011000, lengths 1 3 2 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5, -127, -127,   6, -127, -127, -127 },
    // 25 = 0x19 = 0b00011001, lengths 2 3 2 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6, -127, -127,   7, -127, -127, -127 },
    // 26 = 0x1A = 0b00011010, lengths 3 3 2 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7, -127, -127,   8, -127, -127, -127 },
    // 27 = 0x1B = 0b00011011, lengths 4 3 2 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8, -127, -127,   9, -127, -127, -127 },
    // 28 = 0x1C = 0b00011100, lengths 1 4 2 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6, -127, -127,   7, -127, -127, -127 },
    // 29 = 0x1D = 0b00011101, lengths 2 4 2 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7, -127, -127,   8, -127, -127, -127 },
    // 30 = 0x1E = 0b00011110, lengths 3 4 2 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8, -127, -127,   9, -127, -127, -127 },
    // 31 = 0x1F = 0b00011111, lengths 4 4 2 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9, -127, -127,  10, -127, -127, -127 },
    // 32 = 0x20 = 0b00100000, lengths 1 1 3 1
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4, -127,   5, -127, -127, -127 },
    // 33 = 0x21 = 0b00100001, lengths 2 1 3 1
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5, -127,   6, -127, -127, -127 },
    // 34 = 0x22 = 0b00100010, lengths 3 1 3 1
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6, -127,   7, -127, -127, -127 },
    // 35 = 0x23 = 0b00100011, lengths 4 1 3 1
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7, -127,   8, -127, -127, -127 },
    // 36 = 0x24 = 0b00100100, lengths 1 2 3 1
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4,   5, -127,   6, -127, -127, -127 },
    // 37 = 0x25 = 0b00100101, lengths 2 2 3 1
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6, -127,   7, -127, -127, -127 },
    // 38 = 0x26 = 0b00100110, lengths 3 2 3 1
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7, -127,   8, -127, -127, -127 },
    // 39 = 0x27 = 0b00100111, lengths 4 2 3 1
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8, -127,   9, -127, -127, -127 },
    // 40 = 0x28 = 0b00101000, lengths 1 3 3 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6, -127,   7, -127, -127, -127 },
    // 41 = 0x29 = 0b00101001, lengths 2 3 3 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7, -127,   8, -127, -127, -127 },
    // 42 = 0x2A = 0b00101010, lengths 3 3 3 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8, -127,   9, -127, -127, -127 },
    // 43 = 0x2B = 0b00101011, lengths 4 3 3 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9, -127,  10, -127, -127, -127 },
    // 44 = 0x2C = 0b00101100, lengths 1 4 3 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7, -127,   8, -127, -127, -127 },
    // 45 = 0x2D = 0b00101101, lengths 2 4 3 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8, -127,   9, -127, -127, -127 },
    // 46 = 0x2E = 0b00101110, lengths 3 4 3 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9, -127,  10, -127, -127, -127 },
    // 47 = 0x2F = 0b00101111, lengths 4 4 3 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10, -127,  11, -127, -127, -127 },
    // 48 = 0x30 = 0b00110000, lengths 1 1 4 1
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4,   5,   6, -127, -127, -127 },
    // 49 = 0x31 = 0b00110001, lengths 2 1 4 1
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5,   6,   7, -127, -127, -127 },
    // 50 = 0x32 = 0b00110010, lengths 3 1 4 1
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6,   7,   8, -127, -127, -127 },
    // 51 = 0x33 = 0b00110011, lengths 4 1 4 1
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7,   8,   9, -127, -127, -127 },
    // 52 = 0x34 = 0b00110100, lengths 1 2 4 1
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4,   5,   6,   7, -127, -127, -127 },
    // 53 = 0x35 = 0b00110101, lengths 2 2 4 1
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6,   7,   8, -127, -127, -127 },
    // 54 = 0x36 = 0b00110110, lengths 3 2 4 1
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7,   8,   9, -127, -127, -127 },
    // 55 = 0x37 = 0b00110111, lengths 4 2 4 1
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8,   9,  10, -127, -127, -127 },
    // 56 = 0x38 = 0b00111000, lengths 1 3 4 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6,   7,   8, -127, -127, -127 },
    // 57 = 0x39 = 0b00111001, lengths 2 3 4 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7,   8,   9, -127, -127, -127 },
    // 58 = 0x3A = 0b00111010, lengths 3 3 4 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8,   9,  10, -127, -127, -127 },
    // 59 = 0x3B = 0b00111011, lengths 4 3 4 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9,  10,  11, -127, -127, -127 },
    // 60 = 0x3C = 0b00111100, lengths 1 4 4 1
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7,   8,   9, -127, -127, -127 },
    // 61 = 0x3D = 0b00111101, lengths 2 4 4 1
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8,   9,  10, -127, -127, -127 },
    // 62 = 0x3E = 0b00111110, lengths 3 4 4 1
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9,  10,  11, -127, -127, -127 },
    // 63 = 0x3F = 0b00111111, lengths 4 4 4 1
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12, -127, -127, -127 },
    // 64 = 0x40 = 0b01000000, lengths 1 1 1 2
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2, -127, -127, -127,   3,   4, -127, -127 },
    // 65 = 0x41 = 0b01000001, lengths 2 1 1 2
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3, -127, -127, -127,   4,   5, -127, -127 },
    // 66 = 0x42 = 0b01000010, lengths 3 1 1 2
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4, -127, -127, -127,   5,   6, -127, -127 },
    // 67 = 0x43 = 0b01000011, lengths 4 1 1 2
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5, -127, -127, -127,   6,   7, -127, -127 },
    // 68 = 0x44 = 0b01000100, lengths 1 2 1 2
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3, -127, -127, -127,   4,   5, -127, -127 },
    // 69 = 0x45 = 0b01000101, lengths 2 2 1 2
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4, -127, -127, -127,   5,   6, -127, -127 },
    // 70 = 0x46 = 0b01000110, lengths 3 2 1 2
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5, -127, -127, -127,   6,   7, -127, -127 },
    // 71 = 0x47 = 0b01000111, lengths 4 2 1 2
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6, -127, -127, -127,   7,   8, -127, -127 },
    // 72 = 0x48 = 0b01001000, lengths 1 3 1 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4, -127, -127, -127,   5,   6, -127, -127 },
    // 73 = 0x49 = 0b01001001, lengths 2 3 1 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5, -127, -127, -127,   6,   7, -127, -127 },
    // 74 = 0x4A = 0b01001010, lengths 3 3 1 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6, -127, -127, -127,   7,   8, -127, -127 },
    // 75 = 0x4B = 0b01001011, lengths 4 3 1 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7, -127, -127, -127,   8,   9, -127, -127 },
    // 76 = 0x4C = 0b01001100, lengths 1 4 1 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5, -127, -127, -127,   6,   7, -127, -127 },
    // 77 = 0x4D = 0b01001101, lengths 2 4 1 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6, -127, -127, -127,   7,   8, -127, -127 },
    // 78 = 0x4E = 0b01001110, lengths 3 4 1 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7, -127, -127, -127,   8,   9, -127, -127 },
    // 79 = 0x4F = 0b01001111, lengths 4 4 1 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8, -127, -127, -127,   9,  10, -127, -127 },
    // 80 = 0x50 = 0b01010000, lengths 1 1 2 2
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3, -127, -127,   4,   5, -127, -127 },
    // 81 = 0x51 = 0b01010001, lengths 2 1 2 2
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4, -127, -127,   5,   6, -127, -127 },
    // 82 = 0x52 = 0b01010010, lengths 3 1 2 2
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5, -127, -127,   6,   7, -127, -127 },
    // 83 = 0x53 = 0b01010011, lengths 4 1 2 2
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6, -127, -127,   7,   8, -127, -127 },
    // 84 = 0x54 = 0b01010100, lengths 1 2 2 2
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4, -127, -127,   5,   6, -127, -127 },
    // 85 = 0x55 = 0b01010101, lengths 2 2 2 2
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5, -127, -127,   6,   7, -127, -127 },
    // 86 = 0x56 = 0b01010110, lengths 3 2 2 2
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6, -127, -127,   7,   8, -127, -127 },
    // 87 = 0x57 = 0b01010111, lengths 4 2 2 2
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7, -127, -127,   8,   9, -127, -127 },
    // 88 = 0x58 = 0b01011000, lengths 1 3 2 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5, -127, -127,   6,   7, -127, -127 },
    // 89 = 0x59 = 0b01011001, lengths 2 3 2 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6, -127, -127,   7,   8, -127, -127 },
    // 90 = 0x5A = 0b01011010, lengths 3 3 2 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7, -127, -127,   8,   9, -127, -127 },
    // 91 = 0x5B = 0b01011011, lengths 4 3 2 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8, -127, -127,   9,  10, -127, -127 },
    // 92 = 0x5C = 0b01011100, lengths 1 4 2 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6, -127, -127,   7,   8, -127, -127 },
    // 93 = 0x5D = 0b01011101, lengths 2 4 2 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7, -127, -127,   8,   9, -127, -127 },
    // 94 = 0x5E = 0b01011110, lengths 3 4 2 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8, -127, -127,   9,  10, -127, -127 },
    // 95 = 0x5F = 0b01011111, lengths 4 4 2 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9, -127, -127,  10,  11, -127, -127 },
    // 96 = 0x60 = 0b01100000, lengths 1 1 3 2
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4, -127,   5,   6, -127, -127 },
    // 97 = 0x61 = 0b01100001, lengths 2 1 3 2
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5, -127,   6,   7, -127, -127 },
    // 98 = 0x62 = 0b01100010, lengths 3 1 3 2
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6, -127,   7,   8, -127, -127 },
    // 99 = 0x63 = 0b01100011, lengths 4 1 3 2
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7, -127,   8,   9, -127, -127 },
    // 100 = 0x64 = 0b01100100, lengths 1 2 3 2
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4,   5, -127,   6,   7, -127, -127 },
    // 101 = 0x65 = 0b01100101, lengths 2 2 3 2
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6, -127,   7,   8, -127, -127 },
    // 102 = 0x66 = 0b01100110, lengths 3 2 3 2
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7, -127,   8,   9, -127, -127 },
    // 103 = 0x67 = 0b01100111, lengths 4 2 3 2
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8, -127,   9,  10, -127, -127 },
    // 104 = 0x68 = 0b01101000, lengths 1 3 3 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6, -127,   7,   8, -127, -127 },
    // 105 = 0x69 = 0b01101001, lengths 2 3 3 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7, -127,   8,   9, -127, -127 },
    // 106 = 0x6A = 0b01101010, lengths 3 3 3 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8, -127,   9,  10, -127, -127 },
    // 107 = 0x6B = 0b01101011, lengths 4 3 3 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9, -127,  10,  11, -127, -127 },
    // 108 = 0x6C = 0b01101100, lengths 1 4 3 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7, -127,   8,   9, -127, -127 },
    // 109 = 0x6D = 0b01101101, lengths 2 4 3 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8, -127,   9,  10, -127, -127 },
    // 110 = 0x6E = 0b01101110, lengths 3 4 3 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9, -127,  10,  11, -127, -127 },
    // 111 = 0x6F = 0b01101111, lengths 4 4 3 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10, -127,  11,  12, -127, -127 },
    // 112 = 0x70 = 0b01110000, lengths 1 1 4 2
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4,   5,   6,   7, -127, -127 },
    // 113 = 0x71 = 0b01110001, lengths 2 1 4 2
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5,   6,   7,   8, -127, -127 },
    // 114 = 0x72 = 0b01110010, lengths 3 1 4 2
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6,   7,   8,   9, -127, -127 },
    // 115 = 0x73 = 0b01110011, lengths 4 1 4 2
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7,   8,   9,  10, -127, -127 },
    // 116 = 0x74 = 0b01110100, lengths 1 2 4 2
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4,   5,   6,   7,   8, -127, -127 },
    // 117 = 0x75 = 0b01110101, lengths 2 2 4 2
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6,   7,   8,   9, -127, -127 },
    // 118 = 0x76 = 0b01110110, lengths 3 2 4 2
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7,   8,   9,  10, -127, -127 },
    // 119 = 0x77 = 0b01110111, lengths 4 2 4 2
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8,   9,  10,  11, -127, -127 },
    // 120 = 0x78 = 0b01111000, lengths 1 3 4 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6,   7,   8,   9, -127, -127 },
    // 121 = 0x79 = 0b01111001, lengths 2 3 4 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7,   8,   9,  10, -127, -127 },
    // 122 = 0x7A = 0b01111010, lengths 3 3 4 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8,   9,  10,  11, -127, -127 },
    // 123 = 0x7B = 0b01111011, lengths 4 3 4 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9,  10,  11,  12, -127, -127 },
    // 124 = 0x7C = 0b01111100, lengths 1 4 4 2
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10, -127, -127 },
    // 125 = 0x7D = 0b01111101, lengths 2 4 4 2
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11, -127, -127 },
    // 126 = 0x7E = 0b01111110, lengths 3 4 4 2
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12, -127, -127 },
    // 127 = 0x7F = 0b01111111, lengths 4 4 4 2
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13, -127, -127 },
    // -127 = 0x80 = 0b10000000, lengths 1 1 1 3
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2, -127, -127, -127,   3,   4,   5, -127 },
    // 129 = 0x81 = 0b10000001, lengths 2 1 1 3
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3, -127, -127, -127,   4,   5,   6, -127 },
    // 130 = 0x82 = 0b10000010, lengths 3 1 1 3
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4, -127, -127, -127,   5,   6,   7, -127 },
    // 131 = 0x83 = 0b10000011, lengths 4 1 1 3
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5, -127, -127, -127,   6,   7,   8, -127 },
    // 132 = 0x84 = 0b10000100, lengths 1 2 1 3
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3, -127, -127, -127,   4,   5,   6, -127 },
    // 133 = 0x85 = 0b10000101, lengths 2 2 1 3
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4, -127, -127, -127,   5,   6,   7, -127 },
    // 134 = 0x86 = 0b10000110, lengths 3 2 1 3
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5, -127, -127, -127,   6,   7,   8, -127 },
    // 135 = 0x87 = 0b10000111, lengths 4 2 1 3
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6, -127, -127, -127,   7,   8,   9, -127 },
    // 136 = 0x88 = 0b10001000, lengths 1 3 1 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4, -127, -127, -127,   5,   6,   7, -127 },
    // 137 = 0x89 = 0b10001001, lengths 2 3 1 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5, -127, -127, -127,   6,   7,   8, -127 },
    // 138 = 0x8A = 0b10001010, lengths 3 3 1 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6, -127, -127, -127,   7,   8,   9, -127 },
    // 139 = 0x8B = 0b10001011, lengths 4 3 1 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7, -127, -127, -127,   8,   9,  10, -127 },
    // 140 = 0x8C = 0b10001100, lengths 1 4 1 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5, -127, -127, -127,   6,   7,   8, -127 },
    // 141 = 0x8D = 0b10001101, lengths 2 4 1 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6, -127, -127, -127,   7,   8,   9, -127 },
    // 142 = 0x8E = 0b10001110, lengths 3 4 1 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7, -127, -127, -127,   8,   9,  10, -127 },
    // 143 = 0x8F = 0b10001111, lengths 4 4 1 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8, -127, -127, -127,   9,  10,  11, -127 },
    // 144 = 0x90 = 0b10010000, lengths 1 1 2 3
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3, -127, -127,   4,   5,   6, -127 },
    // 145 = 0x91 = 0b10010001, lengths 2 1 2 3
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4, -127, -127,   5,   6,   7, -127 },
    // 146 = 0x92 = 0b10010010, lengths 3 1 2 3
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5, -127, -127,   6,   7,   8, -127 },
    // 147 = 0x93 = 0b10010011, lengths 4 1 2 3
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6, -127, -127,   7,   8,   9, -127 },
    // 148 = 0x94 = 0b10010100, lengths 1 2 2 3
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4, -127, -127,   5,   6,   7, -127 },
    // 149 = 0x95 = 0b10010101, lengths 2 2 2 3
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5, -127, -127,   6,   7,   8, -127 },
    // 150 = 0x96 = 0b10010110, lengths 3 2 2 3
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6, -127, -127,   7,   8,   9, -127 },
    // 151 = 0x97 = 0b10010111, lengths 4 2 2 3
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7, -127, -127,   8,   9,  10, -127 },
    // 152 = 0x98 = 0b10011000, lengths 1 3 2 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5, -127, -127,   6,   7,   8, -127 },
    // 153 = 0x99 = 0b10011001, lengths 2 3 2 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6, -127, -127,   7,   8,   9, -127 },
    // 154 = 0x9A = 0b10011010, lengths 3 3 2 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7, -127, -127,   8,   9,  10, -127 },
    // 155 = 0x9B = 0b10011011, lengths 4 3 2 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8, -127, -127,   9,  10,  11, -127 },
    // 156 = 0x9C = 0b10011100, lengths 1 4 2 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6, -127, -127,   7,   8,   9, -127 },
    // 157 = 0x9D = 0b10011101, lengths 2 4 2 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7, -127, -127,   8,   9,  10, -127 },
    // 158 = 0x9E = 0b10011110, lengths 3 4 2 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8, -127, -127,   9,  10,  11, -127 },
    // 159 = 0x9F = 0b10011111, lengths 4 4 2 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9, -127, -127,  10,  11,  12, -127 },
    // 160 = 0xA0 = 0b10100000, lengths 1 1 3 3
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4, -127,   5,   6,   7, -127 },
    // 161 = 0xA1 = 0b10100001, lengths 2 1 3 3
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5, -127,   6,   7,   8, -127 },
    // 162 = 0xA2 = 0b10100010, lengths 3 1 3 3
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6, -127,   7,   8,   9, -127 },
    // 163 = 0xA3 = 0b10100011, lengths 4 1 3 3
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7, -127,   8,   9,  10, -127 },
    // 164 = 0xA4 = 0b10100100, lengths 1 2 3 3
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4,   5, -127,   6,   7,   8, -127 },
    // 165 = 0xA5 = 0b10100101, lengths 2 2 3 3
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6, -127,   7,   8,   9, -127 },
    // 166 = 0xA6 = 0b10100110, lengths 3 2 3 3
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7, -127,   8,   9,  10, -127 },
    // 167 = 0xA7 = 0b10100111, lengths 4 2 3 3
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8, -127,   9,  10,  11, -127 },
    // 168 = 0xA8 = 0b10101000, lengths 1 3 3 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6, -127,   7,   8,   9, -127 },
    // 169 = 0xA9 = 0b10101001, lengths 2 3 3 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7, -127,   8,   9,  10, -127 },
    // 170 = 0xAA = 0b10101010, lengths 3 3 3 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8, -127,   9,  10,  11, -127 },
    // 171 = 0xAB = 0b10101011, lengths 4 3 3 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9, -127,  10,  11,  12, -127 },
    // 172 = 0xAC = 0b10101100, lengths 1 4 3 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7, -127,   8,   9,  10, -127 },
    // 173 = 0xAD = 0b10101101, lengths 2 4 3 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8, -127,   9,  10,  11, -127 },
    // 174 = 0xAE = 0b10101110, lengths 3 4 3 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9, -127,  10,  11,  12, -127 },
    // 175 = 0xAF = 0b10101111, lengths 4 4 3 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10, -127,  11,  12,  13, -127 },
    // 176 = 0xB0 = 0b10110000, lengths 1 1 4 3
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4,   5,   6,   7,   8, -127 },
    // 177 = 0xB1 = 0b10110001, lengths 2 1 4 3
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5,   6,   7,   8,   9, -127 },
    // 178 = 0xB2 = 0b10110010, lengths 3 1 4 3
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6,   7,   8,   9,  10, -127 },
    // 179 = 0xB3 = 0b10110011, lengths 4 1 4 3
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7,   8,   9,  10,  11, -127 },
    // 180 = 0xB4 = 0b10110100, lengths 1 2 4 3
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4,   5,   6,   7,   8,   9, -127 },
    // 181 = 0xB5 = 0b10110101, lengths 2 2 4 3
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6,   7,   8,   9,  10, -127 },
    // 182 = 0xB6 = 0b10110110, lengths 3 2 4 3
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7,   8,   9,  10,  11, -127 },
    // 183 = 0xB7 = 0b10110111, lengths 4 2 4 3
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8,   9,  10,  11,  12, -127 },
    // 184 = 0xB8 = 0b10111000, lengths 1 3 4 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6,   7,   8,   9,  10, -127 },
    // 185 = 0xB9 = 0b10111001, lengths 2 3 4 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7,   8,   9,  10,  11, -127 },
    // 186 = 0xBA = 0b10111010, lengths 3 3 4 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8,   9,  10,  11,  12, -127 },
    // 187 = 0xBB = 0b10111011, lengths 4 3 4 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9,  10,  11,  12,  13, -127 },
    // 188 = 0xBC = 0b10111100, lengths 1 4 4 3
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11, -127 },
    // 189 = 0xBD = 0b10111101, lengths 2 4 4 3
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12, -127 },
    // 190 = 0xBE = 0b10111110, lengths 3 4 4 3
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13, -127 },
    // 191 = 0xBF = 0b10111111, lengths 4 4 4 3
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13,  14, -127 },
    // 192 = 0xC0 = 0b11000000, lengths 1 1 1 4
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2, -127, -127, -127,   3,   4,   5,   6 },
    // 193 = 0xC1 = 0b11000001, lengths 2 1 1 4
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3, -127, -127, -127,   4,   5,   6,   7 },
    // 194 = 0xC2 = 0b11000010, lengths 3 1 1 4
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4, -127, -127, -127,   5,   6,   7,   8 },
    // 195 = 0xC3 = 0b11000011, lengths 4 1 1 4
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5, -127, -127, -127,   6,   7,   8,   9 },
    // 196 = 0xC4 = 0b11000100, lengths 1 2 1 4
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3, -127, -127, -127,   4,   5,   6,   7 },
    // 197 = 0xC5 = 0b11000101, lengths 2 2 1 4
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4, -127, -127, -127,   5,   6,   7,   8 },
    // 198 = 0xC6 = 0b11000110, lengths 3 2 1 4
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5, -127, -127, -127,   6,   7,   8,   9 },
    // 199 = 0xC7 = 0b11000111, lengths 4 2 1 4
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6, -127, -127, -127,   7,   8,   9,  10 },
    // 200 = 0xC8 = 0b11001000, lengths 1 3 1 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4, -127, -127, -127,   5,   6,   7,   8 },
    // 201 = 0xC9 = 0b11001001, lengths 2 3 1 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5, -127, -127, -127,   6,   7,   8,   9 },
    // 202 = 0xCA = 0b11001010, lengths 3 3 1 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6, -127, -127, -127,   7,   8,   9,  10 },
    // 203 = 0xCB = 0b11001011, lengths 4 3 1 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7, -127, -127, -127,   8,   9,  10,  11 },
    // 204 = 0xCC = 0b11001100, lengths 1 4 1 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5, -127, -127, -127,   6,   7,   8,   9 },
    // 205 = 0xCD = 0b11001101, lengths 2 4 1 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6, -127, -127, -127,   7,   8,   9,  10 },
    // 206 = 0xCE = 0b11001110, lengths 3 4 1 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7, -127, -127, -127,   8,   9,  10,  11 },
    // 207 = 0xCF = 0b11001111, lengths 4 4 1 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8, -127, -127, -127,   9,  10,  11,  12 },
    // 208 = 0xD0 = 0b11010000, lengths 1 1 2 4
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3, -127, -127,   4,   5,   6,   7 },
    // 209 = 0xD1 = 0b11010001, lengths 2 1 2 4
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4, -127, -127,   5,   6,   7,   8 },
    // 210 = 0xD2 = 0b11010010, lengths 3 1 2 4
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5, -127, -127,   6,   7,   8,   9 },
    // 211 = 0xD3 = 0b11010011, lengths 4 1 2 4
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6, -127, -127,   7,   8,   9,  10 },
    // 212 = 0xD4 = 0b11010100, lengths 1 2 2 4
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4, -127, -127,   5,   6,   7,   8 },
    // 213 = 0xD5 = 0b11010101, lengths 2 2 2 4
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5, -127, -127,   6,   7,   8,   9 },
    // 214 = 0xD6 = 0b11010110, lengths 3 2 2 4
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6, -127, -127,   7,   8,   9,  10 },
    // 215 = 0xD7 = 0b11010111, lengths 4 2 2 4
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7, -127, -127,   8,   9,  10,  11 },
    // 216 = 0xD8 = 0b11011000, lengths 1 3 2 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5, -127, -127,   6,   7,   8,   9 },
    // 217 = 0xD9 = 0b11011001, lengths 2 3 2 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6, -127, -127,   7,   8,   9,  10 },
    // 218 = 0xDA = 0b11011010, lengths 3 3 2 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7, -127, -127,   8,   9,  10,  11 },
    // 219 = 0xDB = 0b11011011, lengths 4 3 2 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8, -127, -127,   9,  10,  11,  12 },
    // 220 = 0xDC = 0b11011100, lengths 1 4 2 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6, -127, -127,   7,   8,   9,  10 },
    // 221 = 0xDD = 0b11011101, lengths 2 4 2 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7, -127, -127,   8,   9,  10,  11 },
    // 222 = 0xDE = 0b11011110, lengths 3 4 2 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8, -127, -127,   9,  10,  11,  12 },
    // 223 = 0xDF = 0b11011111, lengths 4 4 2 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9, -127, -127,  10,  11,  12,  13 },
    // 224 = 0xE0 = 0b11100000, lengths 1 1 3 4
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4, -127,   5,   6,   7,   8 },
    // 225 = 0xE1 = 0b11100001, lengths 2 1 3 4
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5, -127,   6,   7,   8,   9 },
    // 226 = 0xE2 = 0b11100010, lengths 3 1 3 4
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6, -127,   7,   8,   9,  10 },
    // 227 = 0xE3 = 0b11100011, lengths 4 1 3 4
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7, -127,   8,   9,  10,  11 },
    // 228 = 0xE4 = 0b11100100, lengths 1 2 3 4
    new byte[] {   0, 4, 5, 8, 9, 10, 12, 13, 14, 15, 1, 1, 1, 1, 1, 1 },
    // 229 = 0xE5 = 0b11100101, lengths 2 2 3 4
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6, -127,   7,   8,   9,  10 },
    // 230 = 0xE6 = 0b11100110, lengths 3 2 3 4
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7, -127,   8,   9,  10,  11 },
    // 231 = 0xE7 = 0b11100111, lengths 4 2 3 4
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8, -127,   9,  10,  11,  12 },
    // 232 = 0xE8 = 0b11101000, lengths 1 3 3 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6, -127,   7,   8,   9,  10 },
    // 233 = 0xE9 = 0b11101001, lengths 2 3 3 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7, -127,   8,   9,  10,  11 },
    // 234 = 0xEA = 0b11101010, lengths 3 3 3 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8, -127,   9,  10,  11,  12 },
    // 235 = 0xEB = 0b11101011, lengths 4 3 3 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9, -127,  10,  11,  12,  13 },
    // 236 = 0xEC = 0b11101100, lengths 1 4 3 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7, -127,   8,   9,  10,  11 },
    // 237 = 0xED = 0b11101101, lengths 2 4 3 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8, -127,   9,  10,  11,  12 },
    // 238 = 0xEE = 0b11101110, lengths 3 4 3 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9, -127,  10,  11,  12,  13 },
    // 239 = 0xEF = 0b11101111, lengths 4 4 3 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10, -127,  11,  12,  13,  14 },
    // 240 = 0xF0 = 0b11110000, lengths 1 1 4 4
    new byte[] {   0, -127, -127, -127,   1, -127, -127, -127,   2,   3,   4,   5,   6,   7,   8,   9 },
    // 241 = 0xF1 = 0b11110001, lengths 2 1 4 4
    new byte[] {   0,   1, -127, -127,   2, -127, -127, -127,   3,   4,   5,   6,   7,   8,   9,  10 },
    // 242 = 0xF2 = 0b11110010, lengths 3 1 4 4
    new byte[] {   0,   1,   2, -127,   3, -127, -127, -127,   4,   5,   6,   7,   8,   9,  10,  11 },
    // 243 = 0xF3 = 0b11110011, lengths 4 1 4 4
    new byte[] {   0,   1,   2,   3,   4, -127, -127, -127,   5,   6,   7,   8,   9,  10,  11,  12 },
    // 244 = 0xF4 = 0b11110100, lengths 1 2 4 4
    new byte[] {   0, -127, -127, -127,   1,   2, -127, -127,   3,   4,   5,   6,   7,   8,   9,  10 },
    // 245 = 0xF5 = 0b11110101, lengths 2 2 4 4
    new byte[] {   0,   1, -127, -127,   2,   3, -127, -127,   4,   5,   6,   7,   8,   9,  10,  11 },
    // 246 = 0xF6 = 0b11110110, lengths 3 2 4 4
    new byte[] {   0,   1,   2, -127,   3,   4, -127, -127,   5,   6,   7,   8,   9,  10,  11,  12 },
    // 247 = 0xF7 = 0b11110111, lengths 4 2 4 4
    new byte[] {   0,   1,   2,   3,   4,   5, -127, -127,   6,   7,   8,   9,  10,  11,  12,  13 },
    // 248 = 0xF8 = 0b11111000, lengths 1 3 4 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3, -127,   4,   5,   6,   7,   8,   9,  10,  11 },
    // 249 = 0xF9 = 0b11111001, lengths 2 3 4 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4, -127,   5,   6,   7,   8,   9,  10,  11,  12 },
    // 250 = 0xFA = 0b11111010, lengths 3 3 4 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5, -127,   6,   7,   8,   9,  10,  11,  12,  13 },
    // 251 = 0xFB = 0b11111011, lengths 4 3 4 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6, -127,   7,   8,   9,  10,  11,  12,  13,  14 },
    // 252 = 0xFC = 0b11111100, lengths 1 4 4 4
    new byte[] {   0, -127, -127, -127,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12 },
    // 253 = 0xFD = 0b11111101, lengths 2 4 4 4
    new byte[] {   0,   1, -127, -127,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13 },
    // 254 = 0xFE = 0b11111110, lengths 3 4 4 4
    new byte[] {   0,   1,   2, -127,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13,  14 },
    // 255 = 0xFF = 0b11111111, lengths 4 4 4 4
    new byte[] {   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13,  14,  15 },
        };

        @Override
        public Result decode(byte[] input, byte[] controlBytes, int[] output) {
            throw new UnsupportedOperationException("examples.StreamVByte.Vectorized.decode is not implemented.");
        }
    }

    public static int controlBytesLen(int count) {
        return 1 + (count - 1) / 4;
    }

    public static int complete_control_bytes_len(int count) {
        return count >> 2;
    }

    public static int leftover_numbers(int count) {
        return count & 0x03;
    }

    public static class Result {
        int nums;
        int bytes;

        public Result(int nums, int bytes) {
            this.nums = nums;
            this.bytes = bytes;
        }
    }

    private StreamVByte() {
    }
}
