package examples;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector"})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class Example2Benchmark {

    @Param({"10000", "100000", "1000000", "10000000", "100000000"})
    int size;

    private float[] a;
    private float[] b;
    private float[] c;

    @Setup
    public void setup() {
        a = Util.floatArray(size);
        b = Util.floatArray(size);
        c = new float[size];
    }

    @Benchmark
    public float[] scalar() {
        Example2.scalar(a, b, c);
        return c;
    }

    @Benchmark
    public float[] vector() {
        Example2.vector(a, b, c);
        return c;
    }
}
