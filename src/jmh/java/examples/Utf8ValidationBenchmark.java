package examples;

import com.google.common.base.Utf8;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Fork(value = 1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class Utf8ValidationBenchmark {

  @Benchmark
  public boolean stdValidation(Utf8ValidationData data) {
    for (var x : data.inputs) {
      if (!Utf8Validation.fallback_validate(x)) {
        throw new RuntimeException("not UTF8?");
      }
    }
    return true;
  }

  @Benchmark
  public boolean vectorValidation(Utf8ValidationData data) {
    for (var x : data.inputs) {
      if (!Utf8Validation.validate(x)) {
        throw new RuntimeException("not UTF8?");
      }
    }
    return true;
  }

  @Benchmark
  public boolean guavaValidation(Utf8ValidationData data) {
    for (var x : data.inputs) {
      if (!Utf8.isWellFormed(x)) {
        throw new RuntimeException("not UTF8?");
      }
    }
    return true;
  }
}
