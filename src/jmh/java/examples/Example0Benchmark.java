package examples;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Fork(value = 1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class Example0Benchmark {

    @Param({"10000000"})
    int array_size;

    private float[] a;
    private float[] b;
    private float[] c;

    @Setup
    public void setup() {
        a = Util.floatArray(array_size);
        b = Util.floatArray(array_size);
        c = new float[array_size];
    }

    @Benchmark
    public float[] vector_add() {
        Example0.vector_add(a, b, c);
        return c;
    }

    @Fork(jvmArgsAppend = "-XX:-UseSuperWord")
    @Benchmark
    public float[] vector_add_no_auto() {
        Example0.vector_add(a, b, c);
        return c;
    }
}
