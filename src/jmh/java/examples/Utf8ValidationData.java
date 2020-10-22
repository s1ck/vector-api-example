package examples;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
public class Utf8ValidationData {

  public enum Encoding {ASCII, UTF8}

  @Param({"ASCII", "UTF8"})
  Encoding mode;

  List<byte[]> inputs;

  @Setup
  public void setup() {
    var r = new Random(1234);
    var inputs = new ArrayList<byte[]>();
    var total = 0;
    do {
      var c = getUTF8String(r, mode);
      total += c.length;
      inputs.add(c);
    } while (total < 10_000_000);
    this.inputs = inputs;
  }

  private static byte[] getUTF8String(Random r, Encoding mode) {
    var vals = mode == Encoding.ASCII
        ? new String[]{"a ", " working ", "potato", "hmm?", "in the garange!", "the dog", "Daniel", "elephant", "cylon "}
        : new String[]{"a ", " working ", "potato", "«hmm?»", "in the garange!", "look!", "...", "", "the ", "dog ", "Daniel ", "éléphant ", "®", "↧", "⿐", "😨", "😧", "😦", "😱", "😫", "😩"};

    var answer = r.ints(1337, 0, vals.length)
        .mapToObj(index -> vals[index])
        .collect(Collectors.joining());

    // we are going to make sure that the UTF-8 is proper
    var byteanswer = answer.getBytes(StandardCharsets.UTF_8);
    if (!new String(byteanswer, StandardCharsets.UTF_8).equals(answer)) {
      throw new RuntimeException("utf8 mismatch.");
    }
    return byteanswer;
  }
}
