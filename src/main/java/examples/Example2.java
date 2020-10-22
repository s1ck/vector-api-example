package examples;

import jdk.incubator.vector.*;

public class Example2 {


    static void scalar(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            float av = a[i];
            float bv = b[i];

            c[i] = av > 0 ? av + bv : av;
        }
    }

    static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    static {
        System.out.println("SPECIES.length() = " + SPECIES.length());
    }

    static void vector(float[] a, float[] b, float[] c) {
        int i = 0;
        int upperBound = SPECIES.loopBound(a.length);
        for (; i < upperBound; i += SPECIES.length()) {
            var av = FloatVector.fromArray(SPECIES, a, i);
            var bv = FloatVector.fromArray(SPECIES, b, i);

            VectorMask<Float> m = av.compare(VectorOperators.GT, 0);
            av.add(bv, m).intoArray(c, i);
        }

        for (; i < a.length; i++) {
            float av = a[i];
            float bv = b[i];
            c[i] = av > 0 ? av + bv : av;
        }
    }

}
