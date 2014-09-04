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

import org.luaj.vm2.lib.MathLib;

/**
 * Extension of {@link LuaNumber} which can hold a Java double as its value.
 * <p>
 * These instance are not instantiated directly by clients, but indirectly via the static functions {@link LuaValue#valueOf(int)} or {@link LuaValue#valueOf(double)} functions. This ensures that values which can be represented as int are wrapped in
 * {@link LuaInteger} instead of {@link LuaDouble}.
 * <p>
 * Almost all API's implemented in LuaDouble are defined and documented in {@link LuaValue}.
 * <p>
 * However the constants {@link #NAN}, {@link #POSINF}, {@link #NEGINF}, {@link #JSTR_NAN}, {@link #JSTR_POSINF}, and {@link #JSTR_NEGINF} may be useful when dealing with Nan or Infinite values.
 * <p>
 * LuaDouble also defines functions for handling the unique math rules of lua devision and modulo in
 * <ul>
 * <li>{@link #ddiv(double, double)}</li>
 * <li>{@link #ddiv_d(double, double)}</li>
 * <li>{@link #dmod(double, double)}</li>
 * <li>{@link #dmod_d(double, double)}</li>
 * </ul>
 * <p>
 * 
 * @see LuaValue
 * @see LuaNumber
 * @see LuaInteger
 * @see LuaValue#valueOf(int)
 * @see LuaValue#valueOf(double)
 */
public class LuaDouble extends LuaNumber {

	/** Constant LuaDouble representing NaN (not a number) */
	public static final LuaDouble	NAN			= new LuaDouble(Double.NaN);

	/** Constant LuaDouble representing positive infinity */
	public static final LuaDouble	POSINF		= new LuaDouble(Double.POSITIVE_INFINITY);

	/** Constant LuaDouble representing negative infinity */
	public static final LuaDouble	NEGINF		= new LuaDouble(Double.NEGATIVE_INFINITY);

	/** Constant String representation for NaN (not a number), "nan" */
	public static final String		JSTR_NAN	= "nan";

	/** Constant String representation for positive infinity, "inf" */
	public static final String		JSTR_POSINF	= "inf";

	/** Constant String representation for negative infinity, "-inf" */
	public static final String		JSTR_NEGINF	= "-inf";

	/** The value being held by this instance. */
	final double					v;

	public static LuaNumber valueOf(final double d) {
		final int id = (int) d;
		return d == id ? (LuaNumber) LuaInteger.valueOf(id) : (LuaNumber) new LuaDouble(d);
	}

	/** Don't allow ints to be boxed by DoubleValues */
	private LuaDouble(final double d) {
		this.v = d;
	}

	@Override
	public int hashCode() {
		final long l = Double.doubleToLongBits(this.v + 1);
		return ((int) (l >> 32)) + (int) l;
	}

	@Override
	public boolean islong() {
		return this.v == (long) this.v;
	}

	@Override
	public byte tobyte() {
		return (byte) (long) this.v;
	}

	@Override
	public char tochar() {
		return (char) (long) this.v;
	}

	@Override
	public double todouble() {
		return this.v;
	}

	@Override
	public float tofloat() {
		return (float) this.v;
	}

	@Override
	public int toint() {
		return (int) (long) this.v;
	}

	@Override
	public long tolong() {
		return (long) this.v;
	}

	@Override
	public short toshort() {
		return (short) (long) this.v;
	}

	@Override
	public double optdouble(final double defval) {
		return this.v;
	}

	@Override
	public int optint(final int defval) {
		return (int) (long) this.v;
	}

	@Override
	public LuaInteger optinteger(final LuaInteger defval) {
		return LuaInteger.valueOf((int) (long) this.v);
	}

	@Override
	public long optlong(final long defval) {
		return (long) this.v;
	}

	@Override
	public LuaInteger checkinteger() {
		return LuaInteger.valueOf((int) (long) this.v);
	}

	// unary operators
	@Override
	public LuaValue neg() {
		return LuaDouble.valueOf(-this.v);
	}

	// object equality, used for key comparison
	@Override
	public boolean equals(final Object o) {
		return o instanceof LuaDouble ? ((LuaDouble) o).v == this.v : false;
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

	// basic binary arithmetic
	@Override
	public LuaValue add(final LuaValue rhs) {
		return rhs.add(this.v);
	}

	@Override
	public LuaValue add(final double lhs) {
		return LuaDouble.valueOf(lhs + this.v);
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
		return LuaDouble.valueOf(this.v - rhs);
	}

	@Override
	public LuaValue subFrom(final double lhs) {
		return LuaDouble.valueOf(lhs - this.v);
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
		return LuaDouble.valueOf(lhs * this.v);
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

	/**
	 * Divide two double numbers according to lua math, and return a {@link LuaValue} result.
	 * 
	 * @param lhs
	 *            Left-hand-side of the division.
	 * @param rhs
	 *            Right-hand-side of the division.
	 * @return {@link LuaValue} for the result of the division, taking into account positive and negiative infinity, and Nan
	 * @see #ddiv_d(double, double)
	 */
	public static LuaValue ddiv(final double lhs, final double rhs) {
		return rhs != 0 ? LuaDouble.valueOf(lhs / rhs) : lhs > 0 ? LuaDouble.POSINF : lhs == 0 ? LuaDouble.NAN : LuaDouble.NEGINF;
	}

	/**
	 * Divide two double numbers according to lua math, and return a double result.
	 * 
	 * @param lhs
	 *            Left-hand-side of the division.
	 * @param rhs
	 *            Right-hand-side of the division.
	 * @return Value of the division, taking into account positive and negative infinity, and Nan
	 * @see #ddiv(double, double)
	 */
	public static double ddiv_d(final double lhs, final double rhs) {
		return rhs != 0 ? lhs / rhs : lhs > 0 ? Double.POSITIVE_INFINITY : lhs == 0 ? Double.NaN : Double.NEGATIVE_INFINITY;
	}

	/**
	 * Take modulo double numbers according to lua math, and return a {@link LuaValue} result.
	 * 
	 * @param lhs
	 *            Left-hand-side of the modulo.
	 * @param rhs
	 *            Right-hand-side of the modulo.
	 * @return {@link LuaValue} for the result of the modulo, using lua's rules for modulo
	 * @see #dmod_d(double, double)
	 */
	public static LuaValue dmod(final double lhs, final double rhs) {
		return rhs != 0 ? LuaDouble.valueOf(lhs - rhs * Math.floor(lhs / rhs)) : LuaDouble.NAN;
	}

	/**
	 * Take modulo for double numbers according to lua math, and return a double result.
	 * 
	 * @param lhs
	 *            Left-hand-side of the modulo.
	 * @param rhs
	 *            Right-hand-side of the modulo.
	 * @return double value for the result of the modulo, using lua's rules for modulo
	 * @see #dmod(double, double)
	 */
	public static double dmod_d(final double lhs, final double rhs) {
		return rhs != 0 ? lhs - rhs * Math.floor(lhs / rhs) : Double.NaN;
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
	public String tojstring() {
		/*
		 * if ( v == 0.0 ) { // never occurs in J2me long bits = Double.doubleToLongBits( v ); return ( bits >> 63 == 0 ) ? "0" : "-0"; }
		 */
		final long l = (long) this.v;
		if (l == this.v)
			return Long.toString(l);
		if (Double.isNaN(this.v))
			return LuaDouble.JSTR_NAN;
		if (Double.isInfinite(this.v))
			return (this.v < 0 ? LuaDouble.JSTR_NEGINF : LuaDouble.JSTR_POSINF);
		return Float.toString((float) this.v);
	}

	@Override
	public LuaString strvalue() {
		return LuaString.valueOf(this.tojstring());
	}

	@Override
	public LuaString optstring(final LuaString defval) {
		return LuaString.valueOf(this.tojstring());
	}

	@Override
	public LuaValue tostring() {
		return LuaString.valueOf(this.tojstring());
	}

	@Override
	public String optjstring(final String defval) {
		return this.tojstring();
	}

	@Override
	public LuaNumber optnumber(final LuaNumber defval) {
		return this;
	}

	@Override
	public boolean isnumber() {
		return true;
	}

	@Override
	public boolean isstring() {
		return true;
	}

	@Override
	public LuaValue tonumber() {
		return this;
	}

	@Override
	public int checkint() {
		return (int) (long) this.v;
	}

	@Override
	public long checklong() {
		return (long) this.v;
	}

	@Override
	public LuaNumber checknumber() {
		return this;
	}

	@Override
	public double checkdouble() {
		return this.v;
	}

	@Override
	public String checkjstring() {
		return this.tojstring();
	}

	@Override
	public LuaString checkstring() {
		return LuaString.valueOf(this.tojstring());
	}

	@Override
	public boolean isvalidkey() {
		return !Double.isNaN(this.v);
	}
}
