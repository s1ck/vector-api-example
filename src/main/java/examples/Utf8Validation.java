package examples;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings({"PointlessBitwiseExpression", "UnnecessaryLocalVariable"})
public final class Utf8Validation {

  public static boolean validate(byte[] input) {
    var validation = new Utf8Validation();
    var bytes = BytesReader.read(input);
    for (; bytes.has_block(); bytes.next_block()) {
      validation.check_next_input(bytes.vec);
    }
    validation.check_eof();
    return validation.no_errors();
  }

  public static boolean fallback_validate(byte[] input) {
    try {
      StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(input));
      return true;
    } catch (CharacterCodingException e) {
      return false;
    }
  }

  // If this is nonzero, there has been a UTF-8 error.
  private ByteVector error = ByteVector.zero(SPECIES);
  // The last input we received
  private ByteVector prev_input_block = ByteVector.zero(SPECIES);
  // Whether the last input we received was incomplete (used for ASCII fast path)
  private ByteVector prev_incomplete = ByteVector.zero(SPECIES);

  private void check_next_input(ByteVector input) {
    if (is_ascii(input)) {
      // If the previous block had incomplete UTF-8 characters at the end, an ASCII block can't
      // possibly finish them.
      this.error = this.error.or(this.prev_incomplete);
    } else {
      this.check_utf8_bytes(input, this.prev_input_block);
      this.prev_incomplete = is_incomplete(input);
      this.prev_input_block = input;
    }
  }

  //
  // Check whether the current bytes are valid UTF-8.
  //
  private void check_utf8_bytes(ByteVector input, ByteVector prev_input) {
    // Flip prev1...prev3 so we can easily determine if they are 2+, 3+ or 4+ lead bytes
    // (2, 3, 4-byte leads become large positive numbers instead of small negative numbers)
    //      simd8<uint8_t> prev1 = input.prev<1>(prev_input);
    //      simd8<uint8_t> sc = check_special_cases(input, prev1);
    var sc = check_special_cases(input, prev_input);
    //      this->error |= check_multibyte_lengths(input, prev_input, sc);
    this.error = this.error.or(check_multibyte_lengths(input, prev_input, sc));
  }

  // Bit 0 = Too Short (lead byte/ASCII followed by lead byte/ASCII)
  // Bit 1 = Too Long (ASCII followed by continuation)
  // Bit 2 = Overlong 3-byte
  // Bit 4 = Surrogate
  // Bit 5 = Overlong 2-byte
  // Bit 7 = Two Continuations
  private static final byte TOO_SHORT = (byte) (1 << 0); // 11______ 0_______
  // 11______ 11______
  private static final byte TOO_LONG = (byte) (1 << 1); // 0_______ 10______
  private static final byte OVERLONG_3 = (byte) (1 << 2); // 11100000 100_____
  private static final byte SURROGATE = (byte) (1 << 4); // 11101101 101_____
  private static final byte OVERLONG_2 = (byte) (1 << 5); // 1100000_ 10______
  private static final byte TWO_CONTS = (byte) (1 << 7); // 10______ 10______
  private static final byte TOO_LARGE = (byte) (1 << 3); // 11110100 1001____
  // 11110100 101_____
  // 11110101 1001____
  // 11110101 101_____
  // 1111011_ 1001____
  // 1111011_ 101_____
  // 11111___ 1001____
  // 11111___ 101_____
  private static final int TOO_LARGE_1000 = 1 << 6;
  // 11110101 1000____
  // 1111011_ 1000____
  // 11111___ 1000____
  private static final int OVERLONG_4 = 1 << 6; // 11110000 1000____

  //    constexpr const uint8_t CARRY = TOO_SHORT | TOO_LONG | TWO_CONTS; // These all have ____ in byte 1 .
  private static final int CARRY = TOO_SHORT | TOO_LONG | TWO_CONTS;

  private static final byte[] SHUFFLE_TABLE_1 = new byte[]{
      // 0_______ ________ <ASCII in byte 1>
      TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
      TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
      // 10______ ________ <continuation in byte 1>
      TWO_CONTS, TWO_CONTS, TWO_CONTS, TWO_CONTS,
      // 1100____ ________ <two byte lead in byte 1>
      TOO_SHORT | OVERLONG_2,
      // 1101____ ________ <two byte lead in byte 1>
      TOO_SHORT,
      // 1110____ ________ <three byte lead in byte 1>
      TOO_SHORT | OVERLONG_3 | SURROGATE,
      // 1111____ ________ <four+ byte lead in byte 1>
      TOO_SHORT | TOO_LARGE | TOO_LARGE_1000 | OVERLONG_4,
      // ---- repeat ----
      // 0_______ ________ <ASCII in byte 1>
      TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
      TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
      // 10______ ________ <continuation in byte 1>
      TWO_CONTS, TWO_CONTS, TWO_CONTS, TWO_CONTS,
      // 1100____ ________ <two byte lead in byte 1>
      TOO_SHORT | OVERLONG_2,
      // 1101____ ________ <two byte lead in byte 1>
      TOO_SHORT,
      // 1110____ ________ <three byte lead in byte 1>
      TOO_SHORT | OVERLONG_3 | SURROGATE,
      // 1111____ ________ <four+ byte lead in byte 1>
      TOO_SHORT | TOO_LARGE | TOO_LARGE_1000 | OVERLONG_4,
  };

  private static final byte[] SHUFFLE_TABLE_2 = new byte[]{
      // ____0000 ________
      CARRY | OVERLONG_3 | OVERLONG_2 | OVERLONG_4,
      // ____0001 ________
      CARRY | OVERLONG_2,
      // ____001_ ________
      CARRY,
      CARRY,

      // ____0100 ________
      CARRY | TOO_LARGE,
      // ____0101 ________
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      // ____011_ ________
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,

      // ____1___ ________
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      // ____1101 ________
      CARRY | TOO_LARGE | TOO_LARGE_1000 | SURROGATE,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      // ---- repeat ----
      // ____0000 ________
      CARRY | OVERLONG_3 | OVERLONG_2 | OVERLONG_4,
      // ____0001 ________
      CARRY | OVERLONG_2,
      // ____001_ ________
      CARRY,
      CARRY,

      // ____0100 ________
      CARRY | TOO_LARGE,
      // ____0101 ________
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      // ____011_ ________
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,

      // ____1___ ________
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      // ____1101 ________
      CARRY | TOO_LARGE | TOO_LARGE_1000 | SURROGATE,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
      CARRY | TOO_LARGE | TOO_LARGE_1000,
  };

  private static final byte[] SHUFFLE_TABLE_3 = new byte[]{
      // ________ 0_______ <ASCII in byte 2>
      TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
      TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,

      // ________ 1000____
      TOO_LONG | OVERLONG_2 | TWO_CONTS | OVERLONG_3 | TOO_LARGE_1000 | OVERLONG_4,
      // ________ 1001____
      TOO_LONG | OVERLONG_2 | TWO_CONTS | OVERLONG_3 | TOO_LARGE,
      // ________ 101_____
      TOO_LONG | OVERLONG_2 | TWO_CONTS | SURROGATE | TOO_LARGE,
      TOO_LONG | OVERLONG_2 | TWO_CONTS | SURROGATE | TOO_LARGE,

      // ________ 11______
      TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
      // ---- repeat ----
      // ________ 0_______ <ASCII in byte 2>
      TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
      TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,

      // ________ 1000____
      TOO_LONG | OVERLONG_2 | TWO_CONTS | OVERLONG_3 | TOO_LARGE_1000 | OVERLONG_4,
      // ________ 1001____
      TOO_LONG | OVERLONG_2 | TWO_CONTS | OVERLONG_3 | TOO_LARGE,
      // ________ 101_____
      TOO_LONG | OVERLONG_2 | TWO_CONTS | SURROGATE | TOO_LARGE,
      TOO_LONG | OVERLONG_2 | TWO_CONTS | SURROGATE | TOO_LARGE,

      // ________ 11______
      TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
  };

  private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_256;
  private static final ByteVector TABLE_1 = ByteVector.fromArray(SPECIES, SHUFFLE_TABLE_1, 0);
  private static final ByteVector TABLE_2 = ByteVector.fromArray(SPECIES, SHUFFLE_TABLE_2, 0);
  private static final ByteVector TABLE_3 = ByteVector.fromArray(SPECIES, SHUFFLE_TABLE_3, 0);

  private static ByteVector check_special_cases(ByteVector input, ByteVector prev_input) {
    // input 39 C3 A7 E9 8F A1 F0 9F 98 80 00
    // previous_input (set to zero) 00 00 00 00 00 00 00 00 00 00 00

    // prev1 (shifted input) 00 39 C3 A7 E9 8F A1 F0 9F 98 80
    var prev1 = prev1(input, prev_input);

    // high nibbles: prev1.shift_right<4>() 00 03 0C 0A 0E 08 0A 0F 09 09 08
    var high_nibbles_prev = prev1.lanewise(VectorOperators.LSHR, 4).and((byte) 0x0F);

    // low nibbles: (prev1 & 0x0F) 00 09 03 07 09 0F 01 00 0F 08 00
    var low_nibbles_prev = prev1.and((byte) 0x0F);

    // high nibbles: input.shift_right<4>() 03 0C 0A 0E 08 0A 0F 09 09 08 00
    var hjgh_nibbles_input = input.lanewise(VectorOperators.LSHR, 4).and((byte) 0x0F);

    // lookup result: byte_1_high 02 02 21 80 15 80 80 49 80 80 80
    var byte_1_high = TABLE_1.rearrange(high_nibbles_prev.toShuffle());

    // lookup result: byte_1_low E7 CB 83 CB CB CB A3 E7 CB CB E7
    var byte_1_low = TABLE_2.rearrange(low_nibbles_prev.toShuffle());

    // lookup result: byte_2_high 01 01 BA 01 E6 BA 01 AE AE E6 01
    var byte_2_high = TABLE_3.rearrange(hjgh_nibbles_input.toShuffle());

    // (byte_1_high & byte_1_low & byte_2_high) 00 00 00 00 00 80 00 00 80 80 00
    var result = byte_1_high.and(byte_1_low).and(byte_2_high);
    return result;
  }

  @SuppressWarnings("unused")
  private static String debug(ByteVector vec) {
    var data = vec.toArray();
    return IntStream.range(0, data.length)
        .mapToObj(i -> String.format("%02X", data[i] & 0xFF))
        .collect(Collectors.joining(" "));
  }

  private static ByteVector check_multibyte_lengths(ByteVector input, ByteVector prev_input, ByteVector sc) {
    //    simd8<uint8_t> prev2 = input.prev<2>(prev_input);
    var prev2 = prev2(input, prev_input);
    //    simd8<uint8_t> prev3 = input.prev<3>(prev_input);
    var prev3 = prev3(input, prev_input);
    //    simd8<uint8_t> must23 = simd8<uint8_t>(must_be_2_3_continuation(prev2, prev3));
    var must23 = must_be_2_3_continuation(prev2, prev3).toVector().reinterpretAsBytes();
    //    simd8<uint8_t> must23_80 = must23 & uint8_t(0x80);
    var must23_80 = must23.and((byte) 0x80);
    //    return must23_80 ^ sc;
    var xor = must23_80.lanewise(VectorOperators.XOR, sc);
    return xor;
  }

  private static VectorMask<Byte> must_be_2_3_continuation(ByteVector prev2, ByteVector prev3) {
    //  simd8<uint8_t> is_third_byte  = prev2.saturating_sub(0b11100000u-1); // Only 111_____ will be > 0
    var is_third_byte = saturating_sub(prev2, ByteVector.broadcast(SPECIES, (byte) (0b11100000 - 1)));
    //  simd8<uint8_t> is_fourth_byte = prev3.saturating_sub(0b11110000u-1); // Only 1111____ will be > 0
    var is_fourth_byte = saturating_sub(prev3, ByteVector.broadcast(SPECIES, (byte) (0b11110000 - 1)));
    // Caller requires a bool (all 1's). All values resulting from the subtraction will be <= 64, so signed comparison is fine.
    //  return simd8<int8_t>(is_third_byte | is_fourth_byte) > int8_t(0);
    var is_third_or_fourth_byte = is_third_byte.or(is_fourth_byte);
    var lt = SPECIES.zero().lt(is_third_or_fourth_byte);
    return lt;
  }

  // If the previous input's last 3 bytes match this, they're too short (they ended at EOF):
  // ... 1111____ 111_____ 11______
  private static final byte[] max_array = {
      (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
      (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
      (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255,
      (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) (0b11110000 - 1), (byte) (0b11100000 - 1), (byte) (0b11000000 - 1)
  };

  private static final ByteVector MAX_ARRAY = ByteVector.fromArray(SPECIES, max_array, 0);

  //
  // Return nonzero if there are incomplete multibyte characters at the end of the block:
  // e.g. if there is a 4-byte character, but it's 3 bytes from the end.
  //
  private static ByteVector is_incomplete(ByteVector input) {
//    const simd8<uint8_t> max_value(&max_array[sizeof(max_array)-sizeof(simd8<uint8_t>)]);
//    return input.gt_bits(max_value);
    // really_inline simd8<uint8_t> gt_bits(const simd8<uint8_t> other) const { return this->saturating_sub(other); }
    // really_inline simd8<uint8_t> saturating_sub(const simd8<uint8_t> other) const { return _mm256_subs_epu8(*this, other); }
    return saturating_sub(input, MAX_ARRAY);
  }

  static ByteVector saturating_sub(ByteVector input, ByteVector op) {
    // left is bigger if
    // |- left is positive
    // |  |- and right is positive
    // |  |  |- and left is bigger than right
    // |  |  |- never when left is smaller than right
    // |  |- never if right is negative
    // |- or left is negative
    // |  |- and right is negative
    // |  |  |- and left is bigger than right
    // |  |  |- never when left is smaller than right
    // |  |- always if right is positive

    var left_is_negative = input.test(VectorOperators.IS_NEGATIVE);
    var positive_input = left_is_negative.not();

    var right_is_negative = op.test(VectorOperators.IS_NEGATIVE);
    var right_is_positive = right_is_negative.not();

    var left_bigger_than_right = input.lt(op).not();

    var left_positive = positive_input.and(right_is_positive).and(left_bigger_than_right);
    var left_negative = left_is_negative.and(right_is_negative).and(left_bigger_than_right);
    var left_neg_and_right_pos = left_is_negative.and(right_is_positive);

    var subMask = left_positive.or(left_negative).or(left_neg_and_right_pos);
    return ByteVector.zero(SPECIES).blend(input.sub(op, subMask), subMask);
  }

  // The only problem that can happen at EOF is that a multibyte character is too short.
  private void check_eof() {
    // If the previous block had incomplete UTF-8 characters at the end, an ASCII block can't
    // possibly finish them.
    this.error = this.error.or(this.prev_incomplete);
  }

  private boolean no_errors() {
//      return this.error.any_bits_set_anywhere() ? error_code::UTF8_ERROR : error_code::SUCCESS;
    return this.error.eq(ByteVector.zero(SPECIES)).allTrue();
  }

  private static boolean is_ascii(ByteVector simd) {
    //  really_inline bool is_ascii() const { return _mm256_movemask_epi8(*this) == 0; }
    return !simd.test(VectorOperators.IS_NEGATIVE).anyTrue();
  }

  //    template<int N=1>
//    really_inline simd8<T> prev(const simd8<T> prev_chunk) const {
//      return _mm256_alignr_epi8(*this, _mm256_permute2x128_si256(prev_chunk, *this, 0x21), 16 - N);
//    }

  private static ByteVector prev1(ByteVector input, ByteVector prev_input) {
    return prev_input.slice(31, input);
  }

  private static ByteVector prev2(final ByteVector input, final ByteVector prev_input) {
    return prev_input.slice(30, input);
  }

  private static ByteVector prev3(final ByteVector input, final ByteVector prev_input) {
    return prev_input.slice(29, input);
  }

  private Utf8Validation() {
  }

  private static final class BytesReader {
    private final int full_block_length;

    private final byte[] input;
    private int offset;

    private ByteVector vec;

    BytesReader(byte[] input, int offset, int full_block_length, ByteVector vec) {
      this.input = input;
      this.offset = offset;
      this.full_block_length = full_block_length;
      this.vec = vec;
    }

    private static BytesReader read(byte[] input) {
      var vec = input.length < SPECIES.length()
          ? ByteVector.fromArray(SPECIES, input, 0, SPECIES.indexInRange(0, input.length))
          : ByteVector.fromArray(SPECIES, input, 0);
      var full_block_length = SPECIES.loopBound(input.length);
      return new BytesReader(input, 0, full_block_length, vec);
    }

    boolean has_block() {
      return vec != null;
    }

    void next_block() {
      var offset = this.offset += SPECIES.length();
      if (offset < full_block_length) {
        vec = ByteVector.fromArray(SPECIES, input, offset);
      } else if (offset < input.length) {
        vec = ByteVector.fromArray(SPECIES, input, offset, SPECIES.indexInRange(offset, input.length));
      } else {
        vec = null;
      }
    }
  }
}
