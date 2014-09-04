/*******************************************************************************
 * Copyright (c) 2009 Luaj.org. All rights reserved.
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

import org.luaj.vm2.lib.MathLib;

/**
 * Extension of {@link LuaNumber} which can hold a Java int as its value.
 * <p>
 * These instance are not instantiated directly by clients, but indirectly via the static functions {@link LuaValue#valueOf(int)} or {@link LuaValue#valueOf(double)} functions. This ensures that policies regarding pooling of instances are
 * encapsulated.
 * <p>
 * There are no API's specific to LuaInteger that are useful beyond what is already exposed in {@link LuaValue}.
 * 
 * @see LuaValue
 * @see LuaNumber
 * @see LuaDouble
 * @see LuaValue#valueOf(int)
 * @see LuaValue#valueOf(double)
 */
public class LuaInteger extends LuaNumber {

	private static final LuaInteger[]	intValues	= new LuaInteger[512];
	static {
		for (int i = 0; i < 512; i++)
			LuaInteger.intValues[i] = new LuaInteger(i - 256);
	}

	public static LuaInteger valueOf(final int i) {
		return i <= 255 && i >= -256 ? LuaInteger.intValues[i + 256] : new LuaInteger(i);
	};

	// TODO consider moving this to LuaValue
	/**
	 * Return a LuaNumber that represents the value provided
	 * 
	 * @param l
	 *            long value to represent.
	 * @return LuaNumber that is eithe LuaInteger or LuaDouble representing l
	 * @see LuaValue#valueOf(int)
	 * @see LuaValue#valueOf(double)
	 */
	public static LuaNumber valueOf(final long l) {
		final int i = (int) l;
		return l == i ? (i <= 255 && i >= -256 ? LuaInteger.intValues[i + 256] : (LuaNumber) new LuaInteger(i)) : (LuaNumber) LuaDouble.valueOf(l);
	}

	/** The value being held by this instance. */
	public final int	v;

	/**
	 * Package protected constructor.
	 * 
	 * @see LuaValue#valueOf(int)
	 **/
	LuaInteger(final int i) {
		this.v = i;
	}

	@Override
	public boolean isint() {
		return true;
	}

	@Override
	public boolean isinttype() {
		return true;
	}

	@Override
	public boolean islong() {
		return true;
	}

	@Override
	public byte tobyte() {
		return (byte) this.v;
	}

	@Override
	public char tochar() {
		return (char) this.v;
	}

	@Override
	public double todouble() {
		return this.v;
	}

	@Override
	public float tofloat() {
		return this.v;
	}

	@Override
	public int toint() {
		return this.v;
	}

	@Override
	public long tolong() {
		return this.v;
	}

	@Override
	public short toshort() {
		return (short) this.v;
	}

	@Override
	public double optdouble(final double defval) {
		return this.v;
	}

	@Override
	public int optint(final int defval) {
		return this.v;
	}

	@Override
	public LuaInteger optinteger(final LuaInteger defval) {
		return this;
	}

	@Override
	public long optlong(final long defval) {
		return this.v;
	}

	@Override
	public String tojstring() {
		return Integer.toString(this.v);
	}

	@Override
	public LuaString strvalue() {
		return LuaString.valueOf(Integer.toString(this.v));
	}

	@Override
	public LuaString optstring(final LuaString defval) {
		return LuaString.valueOf(Integer.toString(this.v));
	}

	@Override
	public LuaValue tostring() {
		return LuaString.valueOf(Integer.toString(this.v));
	}

	@Override
	public String optjstring(final String defval) {
		return Integer.toString(this.v);
	}

	@Override
	public LuaInteger checkinteger() {
		return this;
	}

	@Override
	public boolean isstring() {
		return true;
	}

	@Override
	public int hashCode() {
		return this.v;
	}

	public static int hashCode(final int x) {
		return x;
	}

	// unary operators
	@Override
	public LuaValue neg() {
		return LuaInteger.valueOf(-(long) this.v);
	}

	// object equality, used for key comparison
	@Override
	public boolean equals(final Object o) {
		return o instanceof LuaInteger ? ((LuaInteger) o).v == this.v : false;
	}

	// equality w/ metatable processing
	@Override
	public LuaValue eq(final LuaValue val) {
		return val.raweq(this.v) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean eq_b(final LuaValue val) {
		return val.raweq(this.v);
	}

	// equality w/o metatable processing
	@Override
	public boolean raweq(final LuaValue val) {
		return val.raweq(this.v);
	}

	@Override
	public boolean raweq(final double val) {
		return this.v == val;
	}

	@Override
	public boolean raweq(final int val) {
		return this.v == val;
	}

	// arithmetic operators
	@Override
	public LuaValue add(final LuaValue rhs) {
		return rhs.add(this.v);
	}

	@Override
	public LuaValue add(final double lhs) {
		return LuaDouble.valueOf(lhs + this.v);
	}

	@Override
	public LuaValue add(final int lhs) {
		return LuaInteger.valueOf(lhs + (long) this.v);
	}

	@Override
	public LuaValue sub(final LuaValue rhs) {
		return rhs.subFrom(this.v);
	}

	@Override
	public LuaValue sub(final double rhs) {
		return LuaDouble.valueOf(this.v - rhs);
	}

	@Override
	public LuaValue sub(final int rhs) {
		return LuaValue.valueOf(this.v - rhs);
	}

	@Override
	public LuaValue subFrom(final double lhs) {
		return LuaDouble.valueOf(lhs - this.v);
	}

	@Override
	public LuaValue subFrom(final int lhs) {
		return LuaInteger.valueOf(lhs - (long) this.v);
	}

	@Override
	public LuaValue mul(final LuaValue rhs) {
		return rhs.mul(this.v);
	}

	@Override
	public LuaValue mul(final double lhs) {
		return LuaDouble.valueOf(lhs * this.v);
	}

	@Override
	public LuaValue mul(final int lhs) {
		return LuaInteger.valueOf(lhs * (long) this.v);
	}

	@Override
	public LuaValue pow(final LuaValue rhs) {
		return rhs.powWith(this.v);
	}

	@Override
	public LuaValue pow(final double rhs) {
		return MathLib.dpow(this.v, rhs);
	}

	@Override
	public LuaValue pow(final int rhs) {
		return MathLib.dpow(this.v, rhs);
	}

	@Override
	public LuaValue powWith(final double lhs) {
		return MathLib.dpow(lhs, this.v);
	}

	@Override
	public LuaValue powWith(final int lhs) {
		return MathLib.dpow(lhs, this.v);
	}

	@Override
	public LuaValue div(final LuaValue rhs) {
		return rhs.divInto(this.v);
	}

	@Override
	public LuaValue div(final double rhs) {
		return LuaDouble.ddiv(this.v, rhs);
	}

	@Override
	public LuaValue div(final int rhs) {
		return LuaDouble.ddiv(this.v, rhs);
	}

	@Override
	public LuaValue divInto(final double lhs) {
		return LuaDouble.ddiv(lhs, this.v);
	}

	@Override
	public LuaValue mod(final LuaValue rhs) {
		return rhs.modFrom(this.v);
	}

	@Override
	public LuaValue mod(final double rhs) {
		return LuaDouble.dmod(this.v, rhs);
	}

	@Override
	public LuaValue mod(final int rhs) {
		return LuaDouble.dmod(this.v, rhs);
	}

	@Override
	public LuaValue modFrom(final double lhs) {
		return LuaDouble.dmod(lhs, this.v);
	}

	// relational operators
	@Override
	public LuaValue lt(final LuaValue rhs) {
		return rhs.gt_b(this.v) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue lt(final double rhs) {
		return this.v < rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue lt(final int rhs) {
		return this.v < rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean lt_b(final LuaValue rhs) {
		return rhs.gt_b(this.v);
	}

	@Override
	public boolean lt_b(final int rhs) {
		return this.v < rhs;
	}

	@Override
	public boolean lt_b(final double rhs) {
		return this.v < rhs;
	}

	@Override
	public LuaValue lteq(final LuaValue rhs) {
		return rhs.gteq_b(this.v) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue lteq(final double rhs) {
		return this.v <= rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue lteq(final int rhs) {
		return this.v <= rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean lteq_b(final LuaValue rhs) {
		return rhs.gteq_b(this.v);
	}

	@Override
	public boolean lteq_b(final int rhs) {
		return this.v <= rhs;
	}

	@Override
	public boolean lteq_b(final double rhs) {
		return this.v <= rhs;
	}

	@Override
	public LuaValue gt(final LuaValue rhs) {
		return rhs.lt_b(this.v) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue gt(final double rhs) {
		return this.v > rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue gt(final int rhs) {
		return this.v > rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean gt_b(final LuaValue rhs) {
		return rhs.lt_b(this.v);
	}

	@Override
	public boolean gt_b(final int rhs) {
		return this.v > rhs;
	}

	@Override
	public boolean gt_b(final double rhs) {
		return this.v > rhs;
	}

	@Override
	public LuaValue gteq(final LuaValue rhs) {
		return rhs.lteq_b(this.v) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue gteq(final double rhs) {
		return this.v >= rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public LuaValue gteq(final int rhs) {
		return this.v >= rhs ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean gteq_b(final LuaValue rhs) {
		return rhs.lteq_b(this.v);
	}

	@Override
	public boolean gteq_b(final int rhs) {
		return this.v >= rhs;
	}

	@Override
	public boolean gteq_b(final double rhs) {
		return this.v >= rhs;
	}

	// string comparison
	@Override
	public int strcmp(final LuaString rhs) {
		this.typerror("attempt to compare number with string");
		return 0;
	}

	@Override
	public int checkint() {
		return this.v;
	}

	@Override
	public long checklong() {
		return this.v;
	}

	@Override
	public double checkdouble() {
		return this.v;
	}

	@Override
	public String checkjstring() {
		return String.valueOf(this.v);
	}

	@Override
	public LuaString checkstring() {
		return LuaValue.valueOf(String.valueOf(this.v));
	}

}
