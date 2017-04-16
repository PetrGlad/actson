package de.undercouch.actson;

/**
 * Reactively parses incoming byte sequences.
 * Call {@link #put(byte)} method for each successive byte in input stream, when whole codepoint
 * is parsed consumer will be called with this value. When exception is thrown by this parser all subsequent
 * input will throw exceptions (parsing will not continue after error).
 * <p>
 * Unicode UTF-8
 * <pre>
 *   0x00000000 .. 0x0000007F: 0xxxxxxx
 *   0x00000080 .. 0x000007FF: 110xxxxx 10xxxxxx
 *   0x00000800 .. 0x0000FFFF: 1110xxxx 10xxxxxx 10xxxxxx
 *   0x00010000 .. 0x001FFFFF: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
 *   0x00200000 .. 0x03FFFFFF: 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
 *   0x04000000 .. 0x7FFFFFFF: 111111xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
 * </pre>
 */
public class Utf8Parser {

  public interface Consumer {
    void put(char ch);
  }

  private final Consumer out;
  private long inputPosition = 0;
  private int codePoint = -1;
  private static final int COUNT_INIT = -1;
  private static final int COUNT_ERR = -2;
  private static final int COUNT_MAX = 6;
  private int remainingCount = COUNT_INIT;

  int _requiredCount = -1; // is a class member for testing
  int _currentVal = -1; // is a class member for testing

  public Utf8Parser(Consumer out) {
    this.out = out;
  }

  void updateCountAndVal(final byte x) {
    int mask = 0x80;
    int count = 0;
    while (count <= COUNT_MAX && ((x & mask) != 0)) {
      mask >>= 1;
      count++;
    }
    if (count > COUNT_MAX) {
      remainingCount = COUNT_ERR;
      throw new IllegalArgumentException("UTF8 encoding error: Unexpected octet prefix."
                                           + " octet 0x" + Integer.toHexString(x) + " at " + inputPosition);
    }
    _requiredCount = count;
    _currentVal = x & (mask - 1);
  }

  /**
   * Call this with every successive input stream octet.
   * @param x next byte in input stream.
   * @see #end()
   */
  public void put(final byte x) {
    if (remainingCount == COUNT_ERR) {
      throw new IllegalArgumentException("UTF8 decoder is in inconsistent state"
                                           + " (see previous errors at position " + inputPosition + ").");
    }
    updateCountAndVal(x);
    if (remainingCount == COUNT_INIT) {
      if (_requiredCount == 0) {
        out.put((char) x); // ASCII subset
      } else if (_requiredCount == 1) {
        remainingCount = COUNT_ERR;
        throw new IllegalArgumentException("UTF8 encoding error: continuation octet in starting position."
                                             + " octet 0x" + Integer.toHexString(x) + " at " + inputPosition);
      } else {
        codePoint = _currentVal;
        remainingCount = _requiredCount - 1;
      }
    } else if (remainingCount > 0) {
      assert _requiredCount == 1;
      codePoint = (codePoint << 6) | _currentVal;
      remainingCount--;
      if (remainingCount == 0) {
        if (Character.isBmpCodePoint(codePoint)) {
          out.put((char) codePoint);
        } else if (Character.isValidCodePoint(codePoint)) {
          out.put(Character.highSurrogate(codePoint));
          out.put(Character.lowSurrogate(codePoint));
        } else {
          throw new IllegalArgumentException("UTF8 encoding error: invalid code point "
                                               + Integer.toHexString(codePoint) + " at " + inputPosition);
        }
        remainingCount = COUNT_INIT;
      }
    } else {
      remainingCount = COUNT_ERR;
      throw new IllegalStateException("UTF8 decoder error: Remaining count " + remainingCount);
    }
    inputPosition++;
  }

  /**
   * Call this when there's no more input data
   */
  public void end() {
    if (remainingCount > 0) {
      throw new IllegalArgumentException("UTF8 encoding error: premature end of input stream."
                                           + " Expecting " + remainingCount + " more octets.");
    }
  }
}
