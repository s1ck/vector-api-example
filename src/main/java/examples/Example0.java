package examples;

// Vector addition
public class Example0 {

    static void vector_add(float[] a, float[] b, float[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }
}
