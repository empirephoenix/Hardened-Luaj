/*******************************************************************************
 * Copyright (c) 2009-2011 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ******************************************************************************/
package org.luaj.vm2;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.StringLib;

/**
 * Subclass of {@link LuaValue} for representing lua strings. 
 * <p>
 * Because lua string values are more nearly sequences of bytes than 
 * sequences of characters or unicode code points, the {@link LuaString}
 * implementation holds the string value in an internal byte array.  
 * <p>
 * {@link LuaString} values are generally not mutable once constructed, 
 * so multiple {@link LuaString} values can chare a single byte array.
 * <p>
 * Currently {@link LuaString}s are pooled via a centrally managed weak table.
 * To ensure that as many string values as possible take advantage of this, 
 * Constructors are not exposed directly.  As with number, booleans, and nil, 
 * instance construction should be via {@link LuaValue#valueOf(byte[])} or similar API.
 * <p>
 * When Java Strings are used to initialize {@link LuaString} data, the UTF8 encoding is assumed. 
 * The functions 
 * {@link LuaString#lengthAsUtf8(char[]),
 * {@link LuaString#encodeToUtf8(char[], int, byte[], int)}, and 
 * {@link LuaString#decodeAsUtf8(byte[], int, int) 
 * are used to convert back and forth between UTF8 byte arrays and character arrays.
 * 
 * @see LuaValue
 * @see LuaValue#valueOf(String)
 * @see LuaValue#valueOf(byte[])
 */
public class LuaString extends LuaValue {

	/**
	 * Size of cache of recent short strings. This is the maximum number of LuaStrings that will be retained in the cache of recent short strings.
	 */
	public static final int	RECENT_STRINGS_CACHE_SIZE	= 128;

	/**
	 * Maximum length of a string to be considered for recent short strings caching. This effectively limits the total memory that can be spent on the recent strings cache, ecause no LuaString whose backing exceeds this length will be put into the
	 * cache.
	 */
	public static final int	RECENT_STRINGS_MAX_LENGTH	= 32;

	/** The singleton instance representing lua {@code true} */
	public static LuaValue	s_metatable;

	/** The bytes for the string */
	public final byte[]		m_bytes;

	/** The offset into the byte array, 0 means start at the first byte */
	public final int		m_offset;

	/** The number of bytes that comprise this string */
	public final int		m_length;

	private static class Cache {
		/**
		 * Simple cache of recently created strings that are short. This is simply a list of strings, indexed by their hash codes modulo the cache size that have been recently constructed. If a string is being constructed frequently from different
		 * contexts, it will generally may show up as a cache hit and resolve to the same value.
		 */
		public final LuaString	recent_short_strings[]	= new LuaString[LuaString.RECENT_STRINGS_CACHE_SIZE];

		public LuaString get(final LuaString s) {
			final int index = s.hashCode() & (LuaString.RECENT_STRINGS_CACHE_SIZE - 1);
			final LuaString cached = this.recent_short_strings[index];
			if (cached != null && s.raweq(cached))
				return cached;
			this.recent_short_strings[index] = s;
			return s;
		}

		static final Cache	instance	= new Cache();
	}

	/**
	 * Get a {@link LuaString} instance whose bytes match the supplied Java String using the UTF8 encoding.
	 * 
	 * @param string
	 *            Java String containing characters to encode as UTF8
	 * @return {@link LuaString} with UTF8 bytes corresponding to the supplied String
	 */
	public static LuaString valueOf(final String string) {
		final char[] c = string.toCharArray();
		final byte[] b = new byte[LuaString.lengthAsUtf8(c)];
		LuaString.encodeToUtf8(c, c.length, b, 0);
		return LuaString.valueOf(b, 0, b.length);
	}

	// TODO: should this be deprecated or made private?
	/**
	 * Construct a {@link LuaString} around a byte array that may be used directly as the backing.
	 * <p>
	 * The array may be used as the backing for this object, so clients must not change contents. If the supplied value for 'len' is more than half the length of the container, the supplied byte array will be used as the backing, otherwise the bytes
	 * will be copied to a new byte array, and cache lookup may be performed.
	 * <p>
	 * 
	 * @param bytes
	 *            byte buffer
	 * @param off
	 *            offset into the byte buffer
	 * @param len
	 *            length of the byte buffer
	 * @return {@link LuaString} wrapping the byte buffer
	 */
	public static LuaString valueOf(final byte[] bytes, final int off, final int len) {
		if (bytes.length < LuaString.RECENT_STRINGS_MAX_LENGTH) {
			// Short string. Reuse the backing and check the cache of recent strings before returning.
			final LuaString s = new LuaString(bytes, off, len);
			return Cache.instance.get(s);
		} else if (len >= bytes.length / 2) {
			// Reuse backing only when more than half the bytes are part of the result.
			return new LuaString(bytes, off, len);
		} else {
			// Short result relative to the source. Copy only the bytes that are actually to be used.
			final byte[] b = new byte[len];
			System.arraycopy(bytes, off, b, 0, len);
			return LuaString.valueOf(b, 0, len); // To possibly use cached version.
		}
	}

	/**
	 * Construct a {@link LuaString} using the supplied characters as byte values.
	 * <p>
	 * Only the low-order 8-bits of each character are used, the remainder is ignored.
	 * <p>
	 * This is most useful for constructing byte sequences that do not conform to UTF8.
	 * 
	 * @param bytes
	 *            array of char, whose values are truncated at 8-bits each and put into a byte array.
	 * @return {@link LuaString} wrapping a copy of the byte buffer
	 */
	public static LuaString valueOf(final char[] bytes) {
		return LuaString.valueOf(bytes, 0, bytes.length);
	}

	/**
	 * Construct a {@link LuaString} using the supplied characters as byte values.
	 * <p>
	 * Only the low-order 8-bits of each character are used, the remainder is ignored.
	 * <p>
	 * This is most useful for constructing byte sequences that do not conform to UTF8.
	 * 
	 * @param bytes
	 *            array of char, whose values are truncated at 8-bits each and put into a byte array.
	 * @return {@link LuaString} wrapping a copy of the byte buffer
	 */
	public static LuaString valueOf(final char[] bytes, final int off, final int len) {
		final byte[] b = new byte[len];
		for (int i = 0; i < len; i++)
			b[i] = (byte) bytes[i + off];
		return LuaString.valueOf(b, 0, len);
	}

	/**
	 * Construct a {@link LuaString} around a byte array without copying the contents.
	 * <p>
	 * The array may be used directly as the backing, so clients must not change contents.
	 * <p>
	 * 
	 * @param bytes
	 *            byte buffer
	 * @return {@link LuaString} wrapping the byte buffer
	 */
	public static LuaString valueOf(final byte[] bytes) {
		return LuaString.valueOf(bytes, 0, bytes.length);
	}

	/**
	 * Construct a {@link LuaString} around a byte array without copying the contents.
	 * <p>
	 * The array is used directly after this is called, so clients must not change contents.
	 * <p>
	 * 
	 * @param bytes
	 *            byte buffer
	 * @param offset
	 *            offset into the byte buffer
	 * @param length
	 *            length of the byte buffer
	 * @return {@link LuaString} wrapping the byte buffer
	 */
	private LuaString(final byte[] bytes, final int offset, final int length) {
		this.m_bytes = bytes;
		this.m_offset = offset;
		this.m_length = length;
	}

	@Override
	public boolean isstring() {
		return true;
	}

	@Override
	public LuaValue getmetatable() {
		return LuaString.s_metatable;
	}

	@Override
	public int type() {
		return LuaValue.TSTRING;
	}

	@Override
	public String typename() {
		return "string";
	}

	@Override
	public String tojstring() {
		return LuaString.decodeAsUtf8(this.m_bytes, this.m_offset, this.m_length);
	}

	// get is delegated to the string library
	@Override
	public LuaValue get(final LuaValue key) {
		return LuaString.s_metatable != null ? LuaValue.gettable(this, key) : StringLib.instance.get(key);
	}

	// unary operators
	@Override
	public LuaValue neg() {
		final double d = this.scannumber();
		return Double.isNaN(d) ? super.neg() : LuaValue.valueOf(-d);
	}

	// basic binary arithmetic
	@Override
	public LuaValue add(final LuaValue rhs) {
		final double d = this.scannumber();
		return Double.isNaN(d) ? this.arithmt(LuaValue.ADD, rhs) : rhs.add(d);
	}

	@Override
	public LuaValue add(final double rhs) {
		return LuaValue.valueOf(this.checkarith() + rhs);
	}

	@Override
	public LuaValue add(final int rhs) {
		return LuaValue.valueOf(this.checkarith() + rhs);
	}

	@Override
	public LuaValue sub(final LuaValue rhs) {
		final double d = this.scannumber();
		return Double.isNaN(d) ? this.arithmt(LuaValue.SUB, rhs) : rhs.subFrom(d);
	}

	@Override
	public LuaValue sub(final double rhs) {
		return LuaValue.valueOf(this.checkarith() - rhs);
	}

	@Override
	public LuaValue sub(final int rhs) {
		return LuaValue.valueOf(this.checkarith() - rhs);
	}

	@Override
	public LuaValue subFrom(final double lhs) {
		return LuaValue.valueOf(lhs - this.checkarith());
	}

	@Override
	public LuaValue mul(final LuaValue rhs) {
		final double d = this.scannumber();
		return Double.isNaN(d) ? this.arithmt(LuaValue.MUL, rhs) : rhs.mul(d);
	}

	@Override
	public LuaValue mul(final double rhs) {
		return LuaValue.valueOf(this.checkarith() * rhs);
	}

	@Override
	public LuaValue mul(final int rhs) {
		return LuaValue.valueOf(this.checkarith() * rhs);
	}

	@Override
	public LuaValue pow(final LuaValue rhs) {
		final double d = this.scannumber();
		return Double.isNaN(d) ? this.arithmt(LuaValue.POW, rhs) : rhs.powWith(d);
	}

	@Override
	public LuaValue pow(final double rhs) {
		return MathLib.dpow(this.checkarith(), rhs);
	}

	@Override
	public LuaValue pow(final int rhs) {
		return MathLib.dpow(this.checkarith(), rhs);
	}

	@Override
	public LuaValue powWith(final double lhs) {
		return MathLib.dpow(lhs, this.checkarith());
	}

	@Override
	public LuaValue powWith(final int lhs) {
		return MathLib.dpow(lhs, this.checkarith());
	}

	@Override
	public LuaValue div(final LuaValue rhs) {
		final double d = this.scannumber();
		return Double.isNaN(d) ? this.arithmt(LuaValue.DIV, rhs) : rhs.divInto(d);
	}

	@Override
	public LuaValue div(final double rhs) {
		return LuaDouble.ddiv(this.checkarith(), rhs);
	}

	@Override
	public LuaValue div(final int rhs) {
		return LuaDouble.ddiv(this.checkarith(), rhs);
	}

	@Override
	public LuaValue divInto(final double lhs) {
		return LuaDouble.ddiv(lhs, this.checkarith());
	}

	@Override
	public LuaValue mod(final LuaValue rhs) {
		final double d = this.scannumber();
		return Double.isNaN(d) ? this.arithmt(LuaValue.MOD, rhs) : rhs.modFrom(d);
	}

	@Override
	public LuaValue mod(final double rhs) {
		return LuaDouble.dmod(this.checkarith(), rhs);
	}

	@Override
	public LuaValue mod(final int rhs) {
		return LuaDouble.dmod(this.checkarith(), rhs);
	}

	@Override
	public LuaValue modFrom(final double lhs) {
		return LuaDouble.dmod(lhs, this.checkarith());
	}

	// relational operators, these only work with other strings
	@Override
	public LuaValue lt(final LuaValue rhs) {
		return rhs.strcmp(this) > 0 ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean lt_b(final LuaValue rhs) {
		return rhs.strcmp(this) > 0;
	}

	@Override
	public boolean lt_b(final int rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean lt_b(final double rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public LuaValue lteq(final LuaValue rhs) {
		return rhs.strcmp(this) >= 0 ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean lteq_b(final LuaValue rhs) {
		return rhs.strcmp(this) >= 0;
	}

	@Override
	public boolean lteq_b(final int rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean lteq_b(final double rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public LuaValue gt(final LuaValue rhs) {
		return rhs.strcmp(this) < 0 ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean gt_b(final LuaValue rhs) {
		return rhs.strcmp(this) < 0;
	}

	@Override
	public boolean gt_b(final int rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean gt_b(final double rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public LuaValue gteq(final LuaValue rhs) {
		return rhs.strcmp(this) <= 0 ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean gteq_b(final LuaValue rhs) {
		return rhs.strcmp(this) <= 0;
	}

	@Override
	public boolean gteq_b(final int rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean gteq_b(final double rhs) {
		this.typerror("attempt to compare string with number");
		return false;
	}

	// concatenation
	@Override
	public LuaValue concat(final LuaValue rhs) {
		return rhs.concatTo(this);
	}

	@Override
	public Buffer concat(final Buffer rhs) {
		return rhs.concatTo(this);
	}

	@Override
	public LuaValue concatTo(final LuaNumber lhs) {
		return this.concatTo(lhs.strvalue());
	}

	@Override
	public LuaValue concatTo(final LuaString lhs) {
		final byte[] b = new byte[lhs.m_length + this.m_length];
		System.arraycopy(lhs.m_bytes, lhs.m_offset, b, 0, lhs.m_length);
		System.arraycopy(this.m_bytes, this.m_offset, b, lhs.m_length, this.m_length);
		return LuaString.valueOf(b, 0, b.length);
	}

	// string comparison
	@Override
	public int strcmp(final LuaValue lhs) {
		return -lhs.strcmp(this);
	}

	@Override
	public int strcmp(final LuaString rhs) {
		for (int i = 0, j = 0; i < this.m_length && j < rhs.m_length; ++i, ++j) {
			if (this.m_bytes[this.m_offset + i] != rhs.m_bytes[rhs.m_offset + j]) {
				return (this.m_bytes[this.m_offset + i]) - (rhs.m_bytes[rhs.m_offset + j]);
			}
		}
		return this.m_length - rhs.m_length;
	}

	/** Check for number in arithmetic, or throw aritherror */
	private double checkarith() {
		final double d = this.scannumber();
		if (Double.isNaN(d))
			this.aritherror();
		return d;
	}

	@Override
	public int checkint() {
		return (int) (long) this.checkdouble();
	}

	@Override
	public LuaInteger checkinteger() {
		return LuaValue.valueOf(this.checkint());
	}

	@Override
	public long checklong() {
		return (long) this.checkdouble();
	}

	@Override
	public double checkdouble() {
		final double d = this.scannumber();
		if (Double.isNaN(d))
			this.argerror("number");
		return d;
	}

	@Override
	public LuaNumber checknumber() {
		return LuaValue.valueOf(this.checkdouble());
	}

	@Override
	public LuaNumber checknumber(final String msg) {
		final double d = this.scannumber();
		if (Double.isNaN(d))
			LuaValue.error(msg);
		return LuaValue.valueOf(d);
	}

	@Override
	public boolean isnumber() {
		final double d = this.scannumber();
		return !Double.isNaN(d);
	}

	@Override
	public boolean isint() {
		final double d = this.scannumber();
		if (Double.isNaN(d))
			return false;
		final int i = (int) d;
		return i == d;
	}

	@Override
	public boolean islong() {
		final double d = this.scannumber();
		if (Double.isNaN(d))
			return false;
		final long l = (long) d;
		return l == d;
	}

	@Override
	public byte tobyte() {
		return (byte) this.toint();
	}

	@Override
	public char tochar() {
		return (char) this.toint();
	}

	@Override
	public double todouble() {
		final double d = this.scannumber();
		return Double.isNaN(d) ? 0 : d;
	}

	@Override
	public float tofloat() {
		return (float) this.todouble();
	}

	@Override
	public int toint() {
		return (int) this.tolong();
	}

	@Override
	public long tolong() {
		return (long) this.todouble();
	}

	@Override
	public short toshort() {
		return (short) this.toint();
	}

	@Override
	public double optdouble(final double defval) {
		return this.checknumber().checkdouble();
	}

	@Override
	public int optint(final int defval) {
		return this.checknumber().checkint();
	}

	@Override
	public LuaInteger optinteger(final LuaInteger defval) {
		return this.checknumber().checkinteger();
	}

	@Override
	public long optlong(final long defval) {
		return this.checknumber().checklong();
	}

	@Override
	public LuaNumber optnumber(final LuaNumber defval) {
		return this.checknumber().checknumber();
	}

	@Override
	public LuaString optstring(final LuaString defval) {
		return this;
	}

	@Override
	public LuaValue tostring() {
		return this;
	}

	@Override
	public String optjstring(final String defval) {
		return this.tojstring();
	}

	@Override
	public LuaString strvalue() {
		return this;
	}

	/**
	 * Take a substring using Java zero-based indexes for begin and end or range.
	 * 
	 * @param beginIndex
	 *            The zero-based index of the first character to include.
	 * @param endIndex
	 *            The zero-based index of position after the last character.
	 * @return LuaString which is a substring whose first character is at offset beginIndex and extending for (endIndex - beginIndex ) characters.
	 */
	public LuaString substring(final int beginIndex, final int endIndex) {
		return LuaString.valueOf(this.m_bytes, this.m_offset + beginIndex, endIndex - beginIndex);
	}

	@Override
	public int hashCode() {
		int h = this.m_length; /* seed */
		final int step = (this.m_length >> 5) + 1; /* if string is too long, don't hash all its chars */
		for (int l1 = this.m_length; l1 >= step; l1 -= step)
			/* compute hash */
			h = h ^ ((h << 5) + (h >> 2) + ((this.m_bytes[this.m_offset + l1 - 1]) & 0x0FF));
		return h;
	}

	// object comparison, used in key comparison
	@Override
	public boolean equals(final Object o) {
		if (o instanceof LuaString) {
			return this.raweq((LuaString) o);
		}
		return false;
	}

	// equality w/ metatable processing
	@Override
	public LuaValue eq(final LuaValue val) {
		return val.raweq(this) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean eq_b(final LuaValue val) {
		return val.raweq(this);
	}

	// equality w/o metatable processing
	@Override
	public boolean raweq(final LuaValue val) {
		return val.raweq(this);
	}

	@Override
	public boolean raweq(final LuaString s) {
		if (this == s)
			return true;
		if (s.m_length != this.m_length)
			return false;
		if (s.m_bytes == this.m_bytes && s.m_offset == this.m_offset)
			return true;
		if (s.hashCode() != this.hashCode())
			return false;
		for (int i = 0; i < this.m_length; i++)
			if (s.m_bytes[s.m_offset + i] != this.m_bytes[this.m_offset + i])
				return false;
		return true;
	}

	public static boolean equals(final LuaString a, final int i, final LuaString b, final int j, final int n) {
		return LuaString.equals(a.m_bytes, a.m_offset + i, b.m_bytes, b.m_offset + j, n);
	}

	public static boolean equals(final byte[] a, int i, final byte[] b, int j, int n) {
		if (a.length < i + n || b.length < j + n)
			return false;
		while (--n >= 0)
			if (a[i++] != b[j++])
				return false;
		return true;
	}

	public void write(final DataOutputStream writer, final int i, final int len) throws IOException {
		writer.write(this.m_bytes, this.m_offset + i, len);
	}

	@Override
	public LuaValue len() {
		return LuaInteger.valueOf(this.m_length);
	}

	@Override
	public int length() {
		return this.m_length;
	}

	@Override
	public int rawlen() {
		return this.m_length;
	}

	public int luaByte(final int index) {
		return this.m_bytes[this.m_offset + index] & 0x0FF;
	}

	public int charAt(final int index) {
		if (index < 0 || index >= this.m_length)
			throw new IndexOutOfBoundsException();
		return this.luaByte(index);
	}

	@Override
	public String checkjstring() {
		return this.tojstring();
	}

	@Override
	public LuaString checkstring() {
		return this;
	}

	/**
	 * Convert value to an input stream.
	 * 
	 * @return {@link InputStream} whose data matches the bytes in this {@link LuaString}
	 */
	public InputStream toInputStream() {
		return new ByteArrayInputStream(this.m_bytes, this.m_offset, this.m_length);
	}

	/**
	 * Copy the bytes of the string into the given byte array.
	 * 
	 * @param strOffset
	 *            offset from which to copy
	 * @param bytes
	 *            destination byte array
	 * @param arrayOffset
	 *            offset in destination
	 * @param len
	 *            number of bytes to copy
	 */
	public void copyInto(final int strOffset, final byte[] bytes, final int arrayOffset, final int len) {
		System.arraycopy(this.m_bytes, this.m_offset + strOffset, bytes, arrayOffset, len);
	}

	/**
	 * Java version of strpbrk - find index of any byte that in an accept string.
	 * 
	 * @param accept
	 *            {@link LuaString} containing characters to look for.
	 * @return index of first match in the {@code accept} string, or -1 if not found.
	 */
	public int indexOfAny(final LuaString accept) {
		final int ilimit = this.m_offset + this.m_length;
		final int jlimit = accept.m_offset + accept.m_length;
		for (int i = this.m_offset; i < ilimit; ++i) {
			for (int j = accept.m_offset; j < jlimit; ++j) {
				if (this.m_bytes[i] == accept.m_bytes[j]) {
					return i - this.m_offset;
				}
			}
		}
		return -1;
	}

	/**
	 * Find the index of a byte starting at a point in this string
	 * 
	 * @param b
	 *            the byte to look for
	 * @param start
	 *            the first index in the string
	 * @return index of first match found, or -1 if not found.
	 */
	public int indexOf(final byte b, final int start) {
		for (int i = start; i < this.m_length; ++i) {
			if (this.m_bytes[this.m_offset + i] == b)
				return i;
		}
		return -1;
	}

	/**
	 * Find the index of a string starting at a point in this string
	 * 
	 * @param s
	 *            the string to search for
	 * @param start
	 *            the first index in the string
	 * @return index of first match found, or -1 if not found.
	 */
	public int indexOf(final LuaString s, final int start) {
		final int slen = s.length();
		final int limit = this.m_length - slen;
		for (int i = start; i <= limit; ++i) {
			if (LuaString.equals(this.m_bytes, this.m_offset + i, s.m_bytes, s.m_offset, slen))
				return i;
		}
		return -1;
	}

	/**
	 * Find the last index of a string in this string
	 * 
	 * @param s
	 *            the string to search for
	 * @return index of last match found, or -1 if not found.
	 */
	public int lastIndexOf(final LuaString s) {
		final int slen = s.length();
		final int limit = this.m_length - slen;
		for (int i = limit; i >= 0; --i) {
			if (LuaString.equals(this.m_bytes, this.m_offset + i, s.m_bytes, s.m_offset, slen))
				return i;
		}
		return -1;
	}

	/**
	 * Convert to Java String interpreting as utf8 characters.
	 * 
	 * @param bytes
	 *            byte array in UTF8 encoding to convert
	 * @param offset
	 *            starting index in byte array
	 * @param length
	 *            number of bytes to convert
	 * @return Java String corresponding to the value of bytes interpreted using UTF8
	 * @see #lengthAsUtf8(char[])
	 * @see #encodeToUtf8(char[], int, byte[], int)
	 * @see #isValidUtf8()
	 */
	public static String decodeAsUtf8(final byte[] bytes, final int offset, final int length) {
		int i, j, n, b;
		for (i = offset, j = offset + length, n = 0; i < j; ++n) {
			switch (0xE0 & bytes[i++]) {
			case 0xE0:
				++i;
			case 0xC0:
				++i;
			}
		}
		final char[] chars = new char[n];
		for (i = offset, j = offset + length, n = 0; i < j;) {
			chars[n++] = (char) (((b = bytes[i++]) >= 0 || i >= j) ? b : (b < -32 || i + 1 >= j) ? (((b & 0x3f) << 6) | (bytes[i++] & 0x3f)) : (((b & 0xf) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f)));
		}
		return new String(chars);
	}

	/**
	 * Count the number of bytes required to encode the string as UTF-8.
	 * 
	 * @param chars
	 *            Array of unicode characters to be encoded as UTF-8
	 * @return count of bytes needed to encode using UTF-8
	 * @see #encodeToUtf8(char[], int, byte[], int)
	 * @see #decodeAsUtf8(byte[], int, int)
	 * @see #isValidUtf8()
	 */
	public static int lengthAsUtf8(final char[] chars) {
		int i, b;
		char c;
		for (i = b = chars.length; --i >= 0;)
			if ((c = chars[i]) >= 0x80)
				b += (c >= 0x800) ? 2 : 1;
		return b;
	}

	/**
	 * Encode the given Java string as UTF-8 bytes, writing the result to bytes starting at offset.
	 * <p>
	 * The string should be measured first with lengthAsUtf8 to make sure the given byte array is large enough.
	 * 
	 * @param chars
	 *            Array of unicode characters to be encoded as UTF-8
	 * @param nchars
	 *            Number of characters in the array to convert.
	 * @param bytes
	 *            byte array to hold the result
	 * @param off
	 *            offset into the byte array to start writing
	 * @return number of bytes converted.
	 * @see #lengthAsUtf8(char[])
	 * @see #decodeAsUtf8(byte[], int, int)
	 * @see #isValidUtf8()
	 */
	public static int encodeToUtf8(final char[] chars, final int nchars, final byte[] bytes, final int off) {
		char c;
		int j = off;
		for (int i = 0; i < nchars; i++) {
			if ((c = chars[i]) < 0x80) {
				bytes[j++] = (byte) c;
			} else if (c < 0x800) {
				bytes[j++] = (byte) (0xC0 | ((c >> 6) & 0x1f));
				bytes[j++] = (byte) (0x80 | (c & 0x3f));
			} else {
				bytes[j++] = (byte) (0xE0 | ((c >> 12) & 0x0f));
				bytes[j++] = (byte) (0x80 | ((c >> 6) & 0x3f));
				bytes[j++] = (byte) (0x80 | (c & 0x3f));
			}
		}
		return j - off;
	}

	/**
	 * Check that a byte sequence is valid UTF-8
	 * 
	 * @return true if it is valid UTF-8, otherwise false
	 * @see #lengthAsUtf8(char[])
	 * @see #encodeToUtf8(char[], int, byte[], int)
	 * @see #decodeAsUtf8(byte[], int, int)
	 */
	public boolean isValidUtf8() {
		int i, j, n;
		final int b, e = 0;
		for (i = this.m_offset, j = this.m_offset + this.m_length, n = 0; i < j; ++n) {
			final int c = this.m_bytes[i++];
			if (c >= 0)
				continue;
			if (((c & 0xE0) == 0xC0) && i < j && (this.m_bytes[i++] & 0xC0) == 0x80)
				continue;
			if (((c & 0xF0) == 0xE0) && i + 1 < j && (this.m_bytes[i++] & 0xC0) == 0x80 && (this.m_bytes[i++] & 0xC0) == 0x80)
				continue;
			return false;
		}
		return true;
	}

	// --------------------- number conversion -----------------------

	/**
	 * convert to a number using baee 10 or base 16 if it starts with '0x', or NIL if it can't be converted
	 * 
	 * @return IntValue, DoubleValue, or NIL depending on the content of the string.
	 * @see LuaValue#tonumber()
	 */
	@Override
	public LuaValue tonumber() {
		final double d = this.scannumber();
		return Double.isNaN(d) ? LuaValue.NIL : LuaValue.valueOf(d);
	}

	/**
	 * convert to a number using a supplied base, or NIL if it can't be converted
	 * 
	 * @param base
	 *            the base to use, such as 10
	 * @return IntValue, DoubleValue, or NIL depending on the content of the string.
	 * @see LuaValue#tonumber()
	 */
	public LuaValue tonumber(final int base) {
		final double d = this.scannumber(base);
		return Double.isNaN(d) ? LuaValue.NIL : LuaValue.valueOf(d);
	}

	/**
	 * Convert to a number in base 10, or base 16 if the string starts with '0x', or return Double.NaN if it cannot be converted to a number.
	 * 
	 * @return double value if conversion is valid, or Double.NaN if not
	 */
	public double scannumber() {
		int i = this.m_offset, j = this.m_offset + this.m_length;
		while (i < j && this.m_bytes[i] == ' ')
			++i;
		while (i < j && this.m_bytes[j - 1] == ' ')
			--j;
		if (i >= j)
			return Double.NaN;
		if (this.m_bytes[i] == '0' && i + 1 < j && (this.m_bytes[i + 1] == 'x' || this.m_bytes[i + 1] == 'X'))
			return this.scanlong(16, i + 2, j);
		final double l = this.scanlong(10, i, j);
		return Double.isNaN(l) ? this.scandouble(i, j) : l;
	}

	/**
	 * Convert to a number in a base, or return Double.NaN if not a number.
	 * 
	 * @param base
	 *            the base to use between 2 and 36
	 * @return double value if conversion is valid, or Double.NaN if not
	 */
	public double scannumber(final int base) {
		if (base < 2 || base > 36)
			return Double.NaN;
		int i = this.m_offset, j = this.m_offset + this.m_length;
		while (i < j && this.m_bytes[i] == ' ')
			++i;
		while (i < j && this.m_bytes[j - 1] == ' ')
			--j;
		if (i >= j)
			return Double.NaN;
		return this.scanlong(base, i, j);
	}

	/**
	 * Scan and convert a long value, or return Double.NaN if not found.
	 * 
	 * @param base
	 *            the base to use, such as 10
	 * @param start
	 *            the index to start searching from
	 * @param end
	 *            the first index beyond the search range
	 * @return double value if conversion is valid, or Double.NaN if not
	 */
	private double scanlong(final int base, final int start, final int end) {
		long x = 0;
		final boolean neg = (this.m_bytes[start] == '-');
		for (int i = (neg ? start + 1 : start); i < end; i++) {
			final int digit = this.m_bytes[i] - (base <= 10 || (this.m_bytes[i] >= '0' && this.m_bytes[i] <= '9') ? '0' : this.m_bytes[i] >= 'A' && this.m_bytes[i] <= 'Z' ? ('A' - 10) : ('a' - 10));
			if (digit < 0 || digit >= base)
				return Double.NaN;
			x = x * base + digit;
			if (x < 0)
				return Double.NaN; // overflow
		}
		return neg ? -x : x;
	}

	/**
	 * Scan and convert a double value, or return Double.NaN if not a double.
	 * 
	 * @param start
	 *            the index to start searching from
	 * @param end
	 *            the first index beyond the search range
	 * @return double value if conversion is valid, or Double.NaN if not
	 */
	private double scandouble(final int start, int end) {
		if (end > start + 64)
			end = start + 64;
		for (int i = start; i < end; i++) {
			switch (this.m_bytes[i]) {
			case '-':
			case '+':
			case '.':
			case 'e':
			case 'E':
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				break;
			default:
				return Double.NaN;
			}
		}
		final char[] c = new char[end - start];
		for (int i = start; i < end; i++)
			c[i - start] = (char) this.m_bytes[i];
		try {
			return Double.parseDouble(new String(c));
		} catch (final Exception e) {
			return Double.NaN;
		}
	}

	/**
	 * Print the bytes of the LuaString to a PrintStream as if it were an ASCII string, quoting and escaping control characters.
	 * 
	 * @param ps
	 *            PrintStream to print to.
	 */
	public void printToStream(final PrintStream ps) {
		for (int i = 0, n = this.m_length; i < n; i++) {
			final int c = this.m_bytes[this.m_offset + i];
			ps.print((char) c);
		}
	}
}
