package examples;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector"})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class Example3Benchmark {

    @Param({"10000", "100000", "1000000", "10000000", "100000000"})
    int size;

    private float[] a;
    private float[] b;

    @Setup
    public void setup() {
        a = Util.floatArray(size);
        b = Util.floatArray(size);
    }

    @Benchmark
    public float scalar() {
        return Example3.scalar(a, b);
    }

    @Benchmark
    public float scalar_unrolled() {
        return Example3.scalar_unrolled(a, b);
    }

    @Benchmark
    public float vector() {
        return Example3.vector(a, b);
    }

    @Benchmark
    public float vector_unrolled() {
        return Example3.vector_unrolled(a, b);
    }

}
