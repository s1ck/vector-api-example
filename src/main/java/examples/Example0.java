package examples;

// Vector addition
public class Example0 {

    static void vector_add(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    static float dot_product(float[] a, float[] b) {
        float sum = 0;
        int length = a.length;
        for (int i = 0; i < length; i++) {
            sum = Math.fma(a[i], b[i], sum);
        }
        return sum;
    }

}
