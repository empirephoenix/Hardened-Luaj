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

/**
 * Class to encapsulate behavior of the singleton instance {@code nil}
 * <p>
 * There will be one instance of this class, {@link LuaValue#NIL}, per Java virtual machine. However, the {@link Varargs} instance {@link LuaValue#NONE} which is the empty list, is also considered treated as a nil value by default.
 * <p>
 * Although it is possible to test for nil using Java == operator, the recommended approach is to use the method {@link LuaValue#isnil()} instead. By using that any ambiguities between {@link LuaValue#NIL} and {@link LuaValue#NONE} are avoided.
 * 
 * @see LuaValue
 * @see LuaValue#NIL
 */
public class LuaNil extends LuaValue {

	static final LuaNil		_NIL	= new LuaNil();

	public static LuaValue	s_metatable;

	LuaNil() {
	}

	@Override
	public int type() {
		return LuaValue.TNIL;
	}

	@Override
	public String toString() {
		return "nil";
	}

	@Override
	public String typename() {
		return "nil";
	}

	@Override
	public String tojstring() {
		return "nil";
	}

	@Override
	public LuaValue not() {
		return LuaValue.TRUE;
	}

	@Override
	public boolean toboolean() {
		return false;
	}

	@Override
	public boolean isnil() {
		return true;
	}

	@Override
	public LuaValue getmetatable() {
		return LuaNil.s_metatable;
	}

	@Override
	public boolean equals(final Object o) {
		return o instanceof LuaNil;
	}

	@Override
	public LuaValue checknotnil() {
		return this.argerror("value");
	}

	@Override
	public boolean isvalidkey() {
		return false;
	}

	// optional argument conversions - nil alwas falls badk to default value
	@Override
	public boolean optboolean(final boolean defval) {
		return defval;
	}

	@Override
	public LuaClosure optclosure(final LuaClosure defval) {
		return defval;
	}

	@Override
	public double optdouble(final double defval) {
		return defval;
	}

	@Override
	public LuaFunction optfunction(final LuaFunction defval) {
		return defval;
	}

	@Override
	public int optint(final int defval) {
		return defval;
	}

	@Override
	public LuaInteger optinteger(final LuaInteger defval) {
		return defval;
	}

	@Override
	public long optlong(final long defval) {
		return defval;
	}

	@Override
	public LuaNumber optnumber(final LuaNumber defval) {
		return defval;
	}

	@Override
	public LuaTable opttable(final LuaTable defval) {
		return defval;
	}

	@Override
	public LuaThread optthread(final LuaThread defval) {
		return defval;
	}

	@Override
	public String optjstring(final String defval) {
		return defval;
	}

	@Override
	public LuaString optstring(final LuaString defval) {
		return defval;
	}

	@Override
	public Object optuserdata(final Object defval) {
		return defval;
	}

	@Override
	public Object optuserdata(final Class c, final Object defval) {
		return defval;
	}

	@Override
	public LuaValue optvalue(final LuaValue defval) {
		return defval;
	}
}
