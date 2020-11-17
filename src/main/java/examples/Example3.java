package examples;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

// Dot product
public class Example3 {

    static float scalar(float[] a, float[] b) {
        float sum = 0;
        int length = a.length;
        for (int i = 0; i < length; i++) {
            sum = Math.fma(a[i], b[i], sum);
        }
        return sum;
    }

    static float scalar_unrolled(float[] a, float[] b) {
        float sum0 = 0;
        float sum1 = 0;
        float sum2 = 0;
        float sum3 = 0;
        float sum4 = 0;
        float sum5 = 0;
        float sum6 = 0;
        float sum7 = 0;
        int length = a.length & ~7;

        int i;
        for (i = 0; i < length; i += 8) {
            sum0 = Math.fma(a[i], b[i], sum0);
            sum1 = Math.fma(a[i + 1], b[i + 1], sum1);
            sum2 = Math.fma(a[i + 2], b[i + 2], sum2);
            sum3 = Math.fma(a[i + 3], b[i + 3], sum3);
            sum4 = Math.fma(a[i + 4], b[i + 4], sum4);
            sum5 = Math.fma(a[i + 5], b[i + 5], sum5);
            sum6 = Math.fma(a[i + 6], b[i + 6], sum6);
            sum7 = Math.fma(a[i + 7], b[i + 7], sum7);
        }
        var vector_sum = sum0 + sum1 + sum2 + sum3 + sum4 + sum5 + sum6 + sum7;

        for (; i < a.length; i++) {
            vector_sum = Math.fma(a[i], b[i], vector_sum);
        }

        return vector_sum;

    }

    static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    static float vector(float[] a, float[] b) {
        int i = 0;
        var sum0 = FloatVector.zero(SPECIES);
        int upperBound = SPECIES.loopBound(a.length);
        for (; i < upperBound; i += SPECIES.length()) {
            var av = FloatVector.fromArray(SPECIES, a, i);
            var bv = FloatVector.fromArray(SPECIES, b, i);
            sum0 = av.fma(bv, sum0);
        }

        var vector_sum = sum0.reduceLanes(VectorOperators.ADD);

        for (; i < a.length; i++) {
            vector_sum = Math.fma(a[i], b[i], vector_sum);
        }
        return vector_sum;
    }

    static float vector_unrolled(float[] a, float[] b) {
        int i = 0;
        var sum0 = FloatVector.zero(SPECIES);
        var sum1 = FloatVector.zero(SPECIES);
        var sum2 = FloatVector.zero(SPECIES);
        var sum3 = FloatVector.zero(SPECIES);
        int length = SPECIES.length();
        int upperBound = SPECIES.loopBound(a.length) & -(length * 4);
        for (; i < upperBound; i += (length * 4)) {
            var av0 = FloatVector.fromArray(SPECIES, a, i);
            var bv0 = FloatVector.fromArray(SPECIES, b, i);
            sum0 = av0.fma(bv0, sum0);
            var av1 = FloatVector.fromArray(SPECIES, a, i + length);
            var bv1 = FloatVector.fromArray(SPECIES, b, i + length);
            sum1 = av1.fma(bv1, sum1);
            var av2 = FloatVector.fromArray(SPECIES, a, i + 2 * length);
            var bv2 = FloatVector.fromArray(SPECIES, b, i + 2 * length);
            sum2 = av2.fma(bv2, sum2);
            var av3 = FloatVector.fromArray(SPECIES, a, i + 3 * length);
            var bv3 = FloatVector.fromArray(SPECIES, b, i + 3 * length);
            sum3 = av3.fma(bv3, sum3);
        }

        var vector_sum = sum0.add(sum1).add(sum2).add(sum3).reduceLanes(VectorOperators.ADD);

        for (; i < a.length; i++) {
            vector_sum = Math.fma(a[i], b[i], vector_sum);
        }
        return vector_sum;
    }
}
