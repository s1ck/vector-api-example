package examples;

import java.util.concurrent.ThreadLocalRandom;

public class Util {

    static long[] longArray(int size) {
        var a = new long[size];
        for (int i = 0; i < size; i++) {
            a[i] = ThreadLocalRandom.current().nextLong();
        }
        return a;
    }

    static float[] floatArray(int size) {
        var a = new float[size];
        for (int i = 0; i < size; i++) {
            a[i] = ThreadLocalRandom.current().nextFloat();
        }
        return a;
    }
}
