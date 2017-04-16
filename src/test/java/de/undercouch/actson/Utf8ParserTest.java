package de.undercouch.actson;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.*;

public class Utf8ParserTest {

  private void checkSample(String sample, boolean isStrict) {
    final byte[] bytes = sample.getBytes(StandardCharsets.UTF_8);
    final boolean standardEquals = sample.equals(new String(bytes, StandardCharsets.UTF_8));
    assertTrue(!isStrict || standardEquals); // Self test
    if (isStrict || standardEquals) {
      final StringBuilder parsed = new StringBuilder();
      final Utf8Parser parser = new Utf8Parser(parsed::append);
      for (byte b : bytes) {
        parser.put(b);
      }
      parser.end();
      assertEquals(sample, parsed.toString());
    }
  }

  @Test
  public void testParser3() {
    for (char c1 = 0; c1 < 600; c1++) {
      for (char c2 = 0; c2 < 300; c2++) {
        for (char c3 = 0; c3 < 50; c3++) {
          checkSample(new String(new char[]{c1, c2, c3}), false);
        }
      }
    }
  }

  @Test
  public void testSurrogates() {
    // TODO (test) check that incorrect surrogate pairs are rejected
    for (char c1 = Character.MIN_HIGH_SURROGATE; c1 < Character.MAX_HIGH_SURROGATE; c1++) {
      for (char c2 = Character.MIN_LOW_SURROGATE; c2 < Character.MAX_LOW_SURROGATE; c2++) {
        checkSample(new String(new char[]{c1, c2}), true);
      }
    }
  }

  @Test
  public void testParser1() {
    for (int c1 = Character.MIN_VALUE; c1 <= Character.MAX_VALUE; c1++) {
      checkSample(new String(new char[]{(char) c1}), false);
    }
  }

  @Test // (expected = IllegalArgumentException.class)
  public void veryLargeCodePoint() {
    // TODO (test) Implement
  }

  @Test // (expected = IllegalArgumentException.class)
  public void exceptionAfterFailure() {
    // TODO (test) Implement
  }

  @Test
  public void internalCountAndVal() {
    final Utf8Parser p = new Utf8Parser(character -> {
    });
    p.updateCountAndVal((byte) 0b0100_0010);
    assertEquals(0, p._requiredCount);
    assertEquals(0b0100_0010, p._currentVal);

    p.updateCountAndVal((byte) 0b1010_0010);
    assertEquals(1, p._requiredCount);
    assertEquals(0b0010_0010, p._currentVal);

    p.updateCountAndVal((byte) 0b1101_0010);
    assertEquals(2, p._requiredCount);
    assertEquals(0b0001_0010, p._currentVal);

    p.updateCountAndVal((byte) 0b1110_1010);
    assertEquals(3, p._requiredCount);
    assertEquals(0b0000_1010, p._currentVal);

    p.updateCountAndVal((byte) 0b1111_0110);
    assertEquals(4, p._requiredCount);
    assertEquals(0b0000_0110, p._currentVal);

    p.updateCountAndVal((byte) 0b1111_1010);
    assertEquals(5, p._requiredCount);
    assertEquals(0b0000_0010, p._currentVal);

    p.updateCountAndVal((byte) 0b1111_1101);
    assertEquals(6, p._requiredCount);
    assertEquals(0b0000_0001, p._currentVal);
  }

  @Test(expected = IllegalArgumentException.class)
  public void internalCountAndValFail() {
    final Utf8Parser p = new Utf8Parser(character -> {
    });
    p.updateCountAndVal((byte) 0xff);
  }

  @Test
  public void microBenchmark() {
    final Random rand = new Random(0xDEADBEEF);
    final int n = 1024 * 1024 * 10;
    final StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      sb.append(rand.nextInt(Character.MAX_CODE_POINT));
    }
    final byte[] utf8Encoded = sb.toString().getBytes(StandardCharsets.UTF_8);
    for (int ll = 1; ll < 10; ll++) {
      final long t1 = System.currentTimeMillis();
      assertEquals(sb.length(), new String(utf8Encoded, StandardCharsets.UTF_8).length());
      System.out.println("Elapsed " + ((System.currentTimeMillis() - t1) / 10.0));
      System.gc();
    }
    System.out.println();
    for (int ll = 1; ll < 10; ll++) {
      final long t1 = System.currentTimeMillis();
      final StringBuilder parsed = new StringBuilder();
      final Utf8Parser parser = new Utf8Parser(parsed::append);
      for (byte b : utf8Encoded) {
        parser.put(b);
      }
      parser.end();
      assertEquals(sb.length(), parsed.length());
      System.out.println("Elapsed " + ((System.currentTimeMillis() - t1) / 10.0));
      System.gc();
    }
  }
}
