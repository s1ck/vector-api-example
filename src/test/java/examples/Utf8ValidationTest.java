package examples;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

final class Utf8ValidationTest {

  @Test
  void testPaper() {
    // 9ç鏡😀
    byte[] input = {
        (byte) 0x39, // 9
        (byte) 0xC3, (byte) 0xA7, // ç
        (byte) 0xE9, (byte) 0x8F, (byte) 0xA1, // 鏡
        (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80, // 😀
    };

    assertTrue(Utf8Validation.validate(input));
  }

  @Test
  void testValidation() {
    // é9€ç鏡😀e{U+10000}
    byte[] input = {
        (byte) 0xC3, (byte) 0xA9, // é
        (byte) 0x39, // 9
        (byte) 0xE2, (byte) 0x82, (byte) 0xAC, // €
        (byte) 0xC3, (byte) 0xA7, // ç
        (byte) 0xE9, (byte) 0x8F, (byte) 0xA1, // 鏡
        (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80, // 😀
        (byte) 0x65, // e
        (byte) 0xE2, (byte) 0x9C, (byte) 0x90, // U+10000
    };

    assertTrue(Utf8Validation.validate(input));
  }

  @ParameterizedTest
  @CsvSource({
      "2, 00111001  10000000",
      "2, 11101001  10001111  00111001",
      "2, 11111010  10010000  10010000  10000000  10000000",
      "2, 11101101  10111000  10000000",
      "2, 11110100  10010000  10000000  10000000",
      "2, 11100000  10000001  10100001",
      "16, F0 8F BF BF",
      "16, 80 81"
  })
  void testInvalid(int radix, CharSequence inputString) {
    var input = Pattern.compile("\s+")
        .splitAsStream(inputString)
        .map(s -> Integer.parseInt(s, radix))
        .map(Integer::byteValue)
        .collect(
            ByteArrayOutputStream::new,
            ByteArrayOutputStream::write,
            (a, b) -> a.writeBytes(b.toByteArray())
        ).toByteArray();

    assertFalse(Utf8Validation.validate(input));
  }

  @ParameterizedTest
  @MethodSource("testBadInput")
  void testBadInput(int brokenIndex, int fullSize) {
    var input = new byte[fullSize];
    Arrays.fill(input, (byte) 0x20);
    input[brokenIndex] = (byte) 0xFF;
    assertFalse(Utf8Validation.validate(input));
  }

  private static Stream<Arguments> testBadInput() {
    return IntStream.range(0, 128).mapToObj(i -> arguments(i, 128));
  }

  @Test
  void testRandom() {
    var input = new byte[64];
    Arrays.fill(input, (byte) 0x20);
    for (var i = 0; i < 10_000; i++) {

      input[i % 64] ^= (byte) (1235 * i);
      var result = Utf8Validation.validate(input);
      var fallback = Utf8Validation.fallback_validate(input);
      var cycle = i;
      assertEquals(fallback,
          result,
          () -> String.format(
              "Failed to verify for i = %d, (input = %n%n%s%n%n)",
              cycle,
              IntStream.range(0, 64)
                  .mapToObj(j -> String.format("(byte) 0x%02X", input[j]))
                  .collect(Collectors.joining(", ", "new byte[]{", "};"))));
      input[i % 64] ^= (byte) (1235 * i);
    }
  }

  @ParameterizedTest
  @CsvSource({
      "42, 0, 42",
      "42, 10, 32",
      "42, 84, 0",
      "42, -10, 0",
      "42, -84, 0",
      "-23, 0, -23",
      "-23, 42, -65",
      "-23, 127, 106",
      "-23, -10, 0",
      "-23, -84, 61",
  })
  void testSaturatingSub(int left, int right, int expected) {
    var uleft = ((byte) left) & 0xFF;
    var uright = ((byte) right) & 0xFF;
    var uexpected = ((byte) expected) & 0xFF;
    assertEquals(Math.max(0, (uleft - uright)), uexpected);

    var spec = ByteVector.SPECIES_256;
    var vleft = spec.broadcast(left).reinterpretAsBytes();
    var vright = spec.broadcast(right).reinterpretAsBytes();

    var result = Utf8Validation.saturating_sub(vleft, vright).reduceLanes(VectorOperators.MAX,spec.indexInRange(0, 1));
    assertEquals(expected, result);
  }
}
