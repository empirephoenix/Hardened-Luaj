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

/**
 * Special table, to make proxying of userdata Logic objects simpler
 * 
 * @author empire
 *
 * @param <Type>
 */
public class LuaProxy<Type> extends LuaTable {
	private Type	userData;

	/** Construct empty table */
	public LuaProxy(final Type userdata) {
		super();
		this.userData = userdata;
	}

	/**
	 * Construct table with preset capacity.
	 * 
	 * @param narray
	 *            capacity of array part
	 * @param nhash
	 *            capacity of hash part
	 */
	public LuaProxy(final int narray, final int nhash, final Type userdata) {
		super(narray, nhash);
		this.userData = userdata;
	}

	/**
	 * Construct table with named and unnamed parts.
	 * 
	 * @param named
	 *            Named elements in order {@code key-a, value-a, key-b, value-b, ... }
	 * @param unnamed
	 *            Unnamed elements in order {@code value-1, value-2, ... }
	 * @param lastarg
	 *            Additional unnamed values beyond {@code unnamed.length}
	 */
	public LuaProxy(final LuaValue[] named, final LuaValue[] unnamed, final Varargs lastarg, final Type userdata) {
		super(named, unnamed, lastarg);
		this.userData = userdata;
	}

	/**
	 * Construct table of unnamed elements.
	 * 
	 * @param varargs
	 *            Unnamed elements in order {@code value-1, value-2, ... }
	 * @param firstarg
	 *            the index in varargs of the first argument to include in the table
	 */
	public LuaProxy(final Varargs varargs, final int firstarg, final Type userdata) {
		super(varargs, firstarg);
		this.userData = userdata;
	}

	/**
	 * Construct table of unnamed elements.
	 * 
	 * @param varargs
	 *            Unnamed elements in order {@code value-1, value-2, ... }
	 */
	public LuaProxy(final Varargs varargs, final Type userdata) {
		super(varargs);
		this.userData = userdata;
	}

	@Override
	public boolean isuserdata() {
		return true;
	}

	@Override
	public boolean isuserdata(final int i) {
		return super.isuserdata(i);
	}

	public Type getUserData() {
		return this.userData;
	}

	public void setUserData(final Type userData) {
		this.userData = userData;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object checkuserdata(final Class c) {
		if (c.isAssignableFrom(this.userData.getClass()))
			return this.userData;
		return this.typerror(c.getName());
	}

	@Override
	public Object checkuserdata() {
		return this.userData;
	}

	@Override
	public Object checkuserdata(final int i) {
		return super.checkuserdata(i);
	}

	@Override
	public Object checkuserdata(final int i, final Class c) {
		return super.checkuserdata(i, c);
	}

	@Override
	public int type() {
		return LuaValue.TTABLE;
	}
}
