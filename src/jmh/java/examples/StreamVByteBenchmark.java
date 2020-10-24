package examples;

import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector"})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class StreamVByteBenchmark {

    @Param({"10000000", "100000000"})
    int size;

    private int[] input;
    private int[] output;

    private byte[] controlBytes;
    private byte[] encoded;

    private Random r = new Random(42);

    @Setup
    public void setup() {
        input = new int[size];
        output = new int[size];
        controlBytes = new byte[StreamVByte.controlBytesLen(size)];
        encoded = new byte[input.length * Integer.BYTES]; // worst case

        for (int i = 0; i < size; i++) {
            input[i] = Math.abs(r.nextInt());
        }

        StreamVByte.scalar().encode(input, controlBytes, encoded);
    }

    @Benchmark
    public int scalarEncode() {
        return StreamVByte.scalar().encode(input, controlBytes, encoded).nums;
    }

    @Benchmark
    public int scalarDecode() {
        return StreamVByte.scalar().decode(encoded, controlBytes, output).nums;
    }
}
