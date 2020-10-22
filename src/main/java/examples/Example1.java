package examples;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;

public class Example1 {

    static void scalar(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = (a[i] * a[i] + b[i] * b[i]) * -1.0f;
        }
    }

    static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    static {
        System.out.println("SPECIES.length() = " + SPECIES.length());
    }

    static void vectorWithMask(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i += SPECIES.length()) {
            var m = SPECIES.indexInRange(i, a.length);
            var va = FloatVector.fromArray(SPECIES, a, i, m);
            var vb = FloatVector.fromArray(SPECIES, b, i, m);
            var vc = va.mul(va).add(vb.mul(vb)).neg();
            vc.intoArray(c, i, m);
        }
    }

    static void vectorWithoutMask(float[] a, float[] b, float[] c) {
        int i = 0;
        int upperBound = SPECIES.loopBound(a.length);
        for (; i < upperBound; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, a, i);
            var vb = FloatVector.fromArray(SPECIES, b, i);
            var vc = va.mul(va).add(vb.mul(vb)).neg();
            vc.intoArray(c, i);
        }
        // Process remainder
        for (; i < a.length; i++) {
            c[i] = (a[i] * a[i] + b[i] * b[i]) * -1.0f;
        }
    }

    public static void main(String[] args) {
        var size = 100_000;

        var a = new float[size];
        var b = new float[size];
        var c = new float[size];
        Arrays.fill(a, 42);
        Arrays.fill(b, 42);
        a[size / 2] = 23;
        b[size / 2] = 23;
        a[size / 3] = 84;
        b[size / 3] = 84;

        for (int i = 0; i < 100_000; i++) {
            vectorWithoutMask(a, b, c);
        }
    }
}
