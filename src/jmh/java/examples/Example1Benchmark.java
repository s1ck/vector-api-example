package examples;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Fork(value = 1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class Example1Benchmark {

    @Param({"10000", "100000", "1000000", "10000000", "100000000"})
    int size;

    private float[] a;
    private float[] b;
    private float[] c;

    @Setup
    public void setup() {
        a = new float[size];
        b = new float[size];
        c = new float[size];
        Arrays.fill(a, 42);
        Arrays.fill(b, 42);
        a[size / 2] = 23;
        b[size / 2] = 23;
        a[size / 3] = 84;
        b[size / 3] = 84;
    }

    @Benchmark
    public float[] scalar() {
        Example1.scalar(a, b, c);
        return c;
    }

    @Benchmark
    public float[] vectorWithMask() {
        Example1.vectorWithMask(a, b, c);
        return c;
    }

    @Benchmark
    public float[] vectorWithoutMask() {
        Example1.vectorWithoutMask(a, b, c);
        return c;
    }

}
