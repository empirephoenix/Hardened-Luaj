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

public class LuaUserdata extends LuaValue {

	public Object	m_instance;
	public LuaValue	m_metatable;

	public LuaUserdata(final Object obj) {
		this.m_instance = obj;
	}

	public LuaUserdata(final Object obj, final LuaValue metatable) {
		this.m_instance = obj;
		this.m_metatable = metatable;
	}

	@Override
	public String tojstring() {
		return String.valueOf(this.m_instance);
	}

	@Override
	public int type() {
		return LuaValue.TUSERDATA;
	}

	@Override
	public String typename() {
		return "userdata";
	}

	@Override
	public int hashCode() {
		return this.m_instance.hashCode();
	}

	public Object userdata() {
		return this.m_instance;
	}

	@Override
	public boolean isuserdata() {
		return true;
	}

	@Override
	public boolean isuserdata(final Class c) {
		return c.isAssignableFrom(this.m_instance.getClass());
	}

	@Override
	public Object touserdata() {
		return this.m_instance;
	}

	@Override
	public Object touserdata(final Class c) {
		return c.isAssignableFrom(this.m_instance.getClass()) ? this.m_instance : null;
	}

	@Override
	public Object optuserdata(final Object defval) {
		return this.m_instance;
	}

	@Override
	public Object optuserdata(final Class c, final Object defval) {
		if (!c.isAssignableFrom(this.m_instance.getClass()))
			this.typerror(c.getName());
		return this.m_instance;
	}

	@Override
	public LuaValue getmetatable() {
		return this.m_metatable;
	}

	@Override
	public LuaValue setmetatable(final LuaValue metatable) {
		this.m_metatable = metatable;
		return this;
	}

	@Override
	public Object checkuserdata() {
		return this.m_instance;
	}

	@Override
	public Object checkuserdata(final Class c) {
		if (c.isAssignableFrom(this.m_instance.getClass()))
			return this.m_instance;
		return this.typerror(c.getName());
	}

	@Override
	public LuaValue get(final LuaValue key) {
		return this.m_metatable != null ? LuaValue.gettable(this, key) : LuaValue.NIL;
	}

	@Override
	public void set(final LuaValue key, final LuaValue value) {
		if (this.m_metatable == null || !LuaValue.settable(this, key, value))
			LuaValue.error("cannot set " + key + " for userdata");
	}

	@Override
	public boolean equals(final Object val) {
		if (this == val)
			return true;
		if (!(val instanceof LuaUserdata))
			return false;
		final LuaUserdata u = (LuaUserdata) val;
		return this.m_instance.equals(u.m_instance);
	}

	// equality w/ metatable processing
	@Override
	public LuaValue eq(final LuaValue val) {
		return this.eq_b(val) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean eq_b(final LuaValue val) {
		if (val.raweq(this))
			return true;
		if (this.m_metatable == null || !val.isuserdata())
			return false;
		final LuaValue valmt = val.getmetatable();
		return valmt != null && LuaValue.eqmtcall(this, this.m_metatable, val, valmt);
	}

	// equality w/o metatable processing
	@Override
	public boolean raweq(final LuaValue val) {
		return val.raweq(this);
	}

	@Override
	public boolean raweq(final LuaUserdata val) {
		return this == val || (this.m_metatable == val.m_metatable && this.m_instance.equals(val.m_instance));
	}

	// __eq metatag processing
	public boolean eqmt(final LuaValue val) {
		return this.m_metatable != null && val.isuserdata() ? LuaValue.eqmtcall(this, this.m_metatable, val, val.getmetatable()) : false;
	}
}
