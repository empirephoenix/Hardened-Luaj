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

import java.lang.ref.WeakReference;
import java.util.Vector;

/**
 * Subclass of {@link LuaValue} for representing lua tables.
 * <p>
 * Almost all API's implemented in {@link LuaTable} are defined and documented in {@link LuaValue}.
 * <p>
 * If a table is needed, the one of the type-checking functions can be used such as {@link #istable()}, {@link #checktable()}, or {@link #opttable(LuaTable)}
 * <p>
 * The main table operations are defined on {@link LuaValue} for getting and setting values with and without metatag processing:
 * <ul>
 * <li>{@link #get(LuaValue)}</li>
 * <li>{@link #set(LuaValue,LuaValue)}</li>
 * <li>{@link #rawget(LuaValue)}</li>
 * <li>{@link #rawset(LuaValue,LuaValue)}</li>
 * <li>plus overloads such as {@link #get(String)}, {@link #get(int)}, and so on</li>
 * </ul>
 * <p>
 * To iterate over key-value pairs from Java, use
 * 
 * <pre>
 * {@code
 * LuaValue k = LuaValue.NIL;
 * while ( true ) {
 *    Varargs n = table.next(k);
 *    if ( (k = n.arg1()).isnil() )
 *       break;
 *    LuaValue v = n.arg(2)
 *    process( k, v )
 * }}
 * </pre>
 * 
 * <p>
 * As with other types, {@link LuaTable} instances should be constructed via one of the table constructor methods on {@link LuaValue}:
 * <ul>
 * <li>{@link LuaValue#tableOf()} empty table</li>
 * <li>{@link LuaValue#tableOf(int, int)} table with capacity</li>
 * <li>{@link LuaValue#listOf(LuaValue[])} initialize array part</li>
 * <li>{@link LuaValue#listOf(LuaValue[], Varargs)} initialize array part</li>
 * <li>{@link LuaValue#tableOf(LuaValue[])} initialize named hash part</li>
 * <li>{@link LuaValue#tableOf(Varargs, int)} initialize named hash part</li>
 * <li>{@link LuaValue#tableOf(LuaValue[], LuaValue[])} initialize array and named parts</li>
 * <li>{@link LuaValue#tableOf(LuaValue[], LuaValue[], Varargs)} initialize array and named parts</li>
 * </ul>
 * 
 * @see LuaValue
 */
public class LuaTable extends LuaValue implements Metatable {
	private static final int		MIN_HASH_CAPACITY	= 2;
	private static final LuaString	N					= LuaValue.valueOf("n");

	/** the array values */
	protected LuaValue[]			array;

	/** the hash part */
	protected Slot[]				hash;

	/** the number of hash entries */
	protected int					hashEntries;

	/** metatable for this table, or null */
	protected Metatable				m_metatable;

	/** Construct empty table */
	public LuaTable() {
		this.array = LuaValue.NOVALS;
		this.hash = LuaTable.NOBUCKETS;
	}

	/**
	 * Construct table with preset capacity.
	 * 
	 * @param narray
	 *            capacity of array part
	 * @param nhash
	 *            capacity of hash part
	 */
	public LuaTable(final int narray, final int nhash) {
		this.presize(narray, nhash);
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
	public LuaTable(final LuaValue[] named, final LuaValue[] unnamed, final Varargs lastarg) {
		final int nn = (named != null ? named.length : 0);
		final int nu = (unnamed != null ? unnamed.length : 0);
		final int nl = (lastarg != null ? lastarg.narg() : 0);
		this.presize(nu + nl, nn >> 1);
		for (int i = 0; i < nu; i++)
			this.rawset(i + 1, unnamed[i]);
		if (lastarg != null)
			for (int i = 1, n = lastarg.narg(); i <= n; ++i)
				this.rawset(nu + i, lastarg.arg(i));
		for (int i = 0; i < nn; i += 2)
			if (!named[i + 1].isnil())
				this.rawset(named[i], named[i + 1]);

	}

	/**
	 * Construct table of unnamed elements.
	 * 
	 * @param varargs
	 *            Unnamed elements in order {@code value-1, value-2, ... }
	 */
	public LuaTable(final Varargs varargs) {
		this(varargs, 1);
	}

	/**
	 * Construct table of unnamed elements.
	 * 
	 * @param varargs
	 *            Unnamed elements in order {@code value-1, value-2, ... }
	 * @param firstarg
	 *            the index in varargs of the first argument to include in the table
	 */
	public LuaTable(final Varargs varargs, final int firstarg) {
		final int nskip = firstarg - 1;
		final int n = Math.max(varargs.narg() - nskip, 0);
		this.presize(n, 1);
		this.set(LuaTable.N, LuaValue.valueOf(n));
		for (int i = 1; i <= n; i++)
			this.set(i, varargs.arg(i + nskip));
	}

	@Override
	public int type() {
		return LuaValue.TTABLE;
	}

	@Override
	public String typename() {
		return "table";
	}

	@Override
	public boolean istable() {
		return true;
	}

	@Override
	public LuaTable checktable() {
		return this;
	}

	@Override
	public LuaTable opttable(final LuaTable defval) {
		return this;
	}

	@Override
	public void presize(final int narray) {
		if (narray > this.array.length)
			this.array = LuaTable.resize(this.array, 1 << LuaTable.log2(narray));
	}

	public void presize(final int narray, int nhash) {
		if (nhash > 0 && nhash < LuaTable.MIN_HASH_CAPACITY)
			nhash = LuaTable.MIN_HASH_CAPACITY;
		// Size of both parts must be a power of two.
		this.array = (narray > 0 ? new LuaValue[1 << LuaTable.log2(narray)] : LuaValue.NOVALS);
		this.hash = (nhash > 0 ? new Slot[1 << LuaTable.log2(nhash)] : LuaTable.NOBUCKETS);
		this.hashEntries = 0;
	}

	/** Resize the table */
	private static LuaValue[] resize(final LuaValue[] old, final int n) {
		final LuaValue[] v = new LuaValue[n];
		System.arraycopy(old, 0, v, 0, old.length);
		return v;
	}

	/**
	 * Get the length of the array part of the table.
	 * 
	 * @return length of the array part, does not relate to count of objects in the table.
	 */
	protected int getArrayLength() {
		return this.array.length;
	}

	/**
	 * Get the length of the hash part of the table.
	 * 
	 * @return length of the hash part, does not relate to count of objects in the table.
	 */
	protected int getHashLength() {
		return this.hash.length;
	}

	@Override
	public LuaValue getmetatable() {
		return (this.m_metatable != null) ? this.m_metatable.toLuaValue() : null;
	}

	@Override
	public LuaValue setmetatable(final LuaValue metatable) {
		final boolean hadWeakKeys = this.m_metatable != null && this.m_metatable.useWeakKeys();
		final boolean hadWeakValues = this.m_metatable != null && this.m_metatable.useWeakValues();
		this.m_metatable = LuaValue.metatableOf(metatable);
		if ((hadWeakKeys != (this.m_metatable != null && this.m_metatable.useWeakKeys())) || (hadWeakValues != (this.m_metatable != null && this.m_metatable.useWeakValues()))) {
			// force a rehash
			this.rehash(0);
		}
		return this;
	}

	@Override
	public LuaValue get(final int key) {
		final LuaValue v = this.rawget(key);
		return v.isnil() && this.m_metatable != null ? LuaValue.gettable(this, LuaValue.valueOf(key)) : v;
	}

	@Override
	public LuaValue get(final LuaValue key) {
		final LuaValue v = this.rawget(key);
		return v.isnil() && this.m_metatable != null ? LuaValue.gettable(this, key) : v;
	}

	@Override
	public LuaValue rawget(final int key) {
		if (key > 0 && key <= this.array.length) {
			final LuaValue v = this.m_metatable == null ? this.array[key - 1] : this.m_metatable.arrayget(this.array, key - 1);
			return v != null ? v : LuaValue.NIL;
		}
		return this.hashget(LuaInteger.valueOf(key));
	}

	@Override
	public LuaValue rawget(final LuaValue key) {
		if (key.isinttype()) {
			final int ikey = key.toint();
			if (ikey > 0 && ikey <= this.array.length) {
				final LuaValue v = this.m_metatable == null ? this.array[ikey - 1] : this.m_metatable.arrayget(this.array, ikey - 1);
				return v != null ? v : LuaValue.NIL;
			}
		}
		return this.hashget(key);
	}

	protected LuaValue hashget(final LuaValue key) {
		if (this.hashEntries > 0) {
			for (Slot slot = this.hash[this.hashSlot(key)]; slot != null; slot = slot.rest()) {
				StrongSlot foundSlot;
				if ((foundSlot = slot.find(key)) != null) {
					return foundSlot.value();
				}
			}
		}
		return LuaValue.NIL;
	}

	@Override
	public void set(final int key, final LuaValue value) {
		if (this.m_metatable == null || !this.rawget(key).isnil() || !LuaValue.settable(this, LuaInteger.valueOf(key), value))
			this.rawset(key, value);
	}

	/** caller must ensure key is not nil */
	@Override
	public void set(final LuaValue key, final LuaValue value) {
		if (!key.isvalidkey() && !this.metatag(LuaValue.NEWINDEX).isfunction())
			this.typerror("table index");
		if (this.m_metatable == null || !this.rawget(key).isnil() || !LuaValue.settable(this, key, value))
			this.rawset(key, value);
	}

	@Override
	public void rawset(final int key, final LuaValue value) {
		if (!this.arrayset(key, value))
			this.hashset(LuaInteger.valueOf(key), value);
	}

	/** caller must ensure key is not nil */
	@Override
	public void rawset(final LuaValue key, final LuaValue value) {
		if (!key.isinttype() || !this.arrayset(key.toint(), value))
			this.hashset(key, value);
	}

	/** Set an array element */
	private boolean arrayset(final int key, final LuaValue value) {
		if (key > 0 && key <= this.array.length) {
			this.array[key - 1] = value.isnil() ? null : (this.m_metatable != null ? this.m_metatable.wrap(value) : value);
			return true;
		}
		return false;
	}

	/**
	 * Remove the element at a position in a list-table
	 * 
	 * @param pos
	 *            the position to remove
	 * @return The removed item, or {@link #NONE} if not removed
	 */
	public LuaValue remove(int pos) {
		final int n = this.length();
		if (pos == 0)
			pos = n;
		else if (pos > n)
			return LuaValue.NONE;
		final LuaValue v = this.rawget(pos);
		for (LuaValue r = v; !r.isnil();) {
			r = this.rawget(pos + 1);
			this.rawset(pos++, r);
		}
		return v.isnil() ? LuaValue.NONE : v;
	}

	/**
	 * Insert an element at a position in a list-table
	 * 
	 * @param pos
	 *            the position to remove
	 * @param value
	 *            The value to insert
	 */
	public void insert(int pos, LuaValue value) {
		if (pos == 0)
			pos = this.length() + 1;
		while (!value.isnil()) {
			final LuaValue v = this.rawget(pos);
			this.rawset(pos++, value);
			value = v;
		}
	}

	/**
	 * Concatenate the contents of a table efficiently, using {@link Buffer}
	 * 
	 * @param sep
	 *            {@link LuaString} separater to apply between elements
	 * @param i
	 *            the first element index
	 * @param j
	 *            the last element index, inclusive
	 * @return {@link LuaString} value of the concatenation
	 */
	public LuaValue concat(final LuaString sep, int i, final int j) {
		final Buffer sb = new Buffer();
		if (i <= j) {
			sb.append(this.get(i).checkstring());
			while (++i <= j) {
				sb.append(sep);
				sb.append(this.get(i).checkstring());
			}
		}
		return sb.tostring();
	}

	@Override
	public int length() {
		final int a = this.getArrayLength();
		int n = a + 1, m = 0;
		while (!this.rawget(n).isnil()) {
			m = n;
			n += a + this.getHashLength() + 1;
		}
		while (n > m + 1) {
			final int k = (n + m) / 2;
			if (!this.rawget(k).isnil())
				m = k;
			else
				n = k;
		}
		return m;
	}

	@Override
	public LuaValue len() {
		return LuaInteger.valueOf(this.length());
	}

	@Override
	public int rawlen() {
		return this.length();
	}

	/**
	 * Get the next element after a particular key in the table
	 * 
	 * @return key,value or nil
	 */
	@Override
	public Varargs next(final LuaValue key) {
		int i = 0;
		do {
			// find current key index
			if (!key.isnil()) {
				if (key.isinttype()) {
					i = key.toint();
					if (i > 0 && i <= this.array.length) {
						break;
					}
				}
				if (this.hash.length == 0)
					LuaValue.error("invalid key to 'next'");
				i = this.hashSlot(key);
				boolean found = false;
				for (Slot slot = this.hash[i]; slot != null; slot = slot.rest()) {
					if (found) {
						final StrongSlot nextEntry = slot.first();
						if (nextEntry != null) {
							return nextEntry.toVarargs();
						}
					} else if (slot.keyeq(key)) {
						found = true;
					}
				}
				if (!found) {
					LuaValue.error("invalid key to 'next'");
				}
				i += 1 + this.array.length;
			}
		} while (false);

		// check array part
		for (; i < this.array.length; ++i) {
			if (this.array[i] != null) {
				final LuaValue value = this.m_metatable == null ? this.array[i] : this.m_metatable.arrayget(this.array, i);
				if (value != null) {
					return LuaValue.varargsOf(LuaInteger.valueOf(i + 1), value);
				}
			}
		}

		// check hash part
		for (i -= this.array.length; i < this.hash.length; ++i) {
			Slot slot = this.hash[i];
			while (slot != null) {
				final StrongSlot first = slot.first();
				if (first != null)
					return first.toVarargs();
				slot = slot.rest();
			}
		}

		// nothing found, push nil, return nil.
		return LuaValue.NIL;
	}

	/**
	 * Get the next element after a particular key in the contiguous array part of a table
	 * 
	 * @return key,value or none
	 */
	@Override
	public Varargs inext(final LuaValue key) {
		final int k = key.checkint() + 1;
		final LuaValue v = this.rawget(k);
		return v.isnil() ? LuaValue.NONE : LuaValue.varargsOf(LuaInteger.valueOf(k), v);
	}

	/**
	 * Set a hashtable value
	 * 
	 * @param key
	 *            key to set
	 * @param value
	 *            value to set
	 */
	public void hashset(final LuaValue key, final LuaValue value) {
		if (value.isnil())
			this.hashRemove(key);
		else {
			int index = 0;
			if (this.hash.length > 0) {
				index = this.hashSlot(key);
				for (Slot slot = this.hash[index]; slot != null; slot = slot.rest()) {
					StrongSlot foundSlot;
					if ((foundSlot = slot.find(key)) != null) {
						this.hash[index] = this.hash[index].set(foundSlot, value);
						return;
					}
				}
			}
			if (this.checkLoadFactor()) {
				if (key.isinttype() && key.toint() > 0) {
					// a rehash might make room in the array portion for this key.
					this.rehash(key.toint());
					if (this.arrayset(key.toint(), value))
						return;
				} else {
					this.rehash(-1);
				}
				index = this.hashSlot(key);
			}
			final Slot entry = (this.m_metatable != null) ? this.m_metatable.entry(key, value) : LuaTable.defaultEntry(key, value);
			this.hash[index] = (this.hash[index] != null) ? this.hash[index].add(entry) : entry;
			++this.hashEntries;
		}
	}

	public static int hashpow2(final int hashCode, final int mask) {
		return hashCode & mask;
	}

	public static int hashmod(final int hashCode, final int mask) {
		return (hashCode & 0x7FFFFFFF) % mask;
	}

	/**
	 * Find the hashtable slot index to use.
	 * 
	 * @param key
	 *            the key to look for
	 * @param hashMask
	 *            N-1 where N is the number of hash slots (must be power of 2)
	 * @return the slot index
	 */
	public static int hashSlot(final LuaValue key, final int hashMask) {
		switch (key.type()) {
		case TNUMBER:
		case TTABLE:
		case TTHREAD:
		case TLIGHTUSERDATA:
		case TUSERDATA:
			return LuaTable.hashmod(key.hashCode(), hashMask);
		default:
			return LuaTable.hashpow2(key.hashCode(), hashMask);
		}
	}

	/**
	 * Find the hashtable slot to use
	 * 
	 * @param key
	 *            key to look for
	 * @return slot to use
	 */
	private int hashSlot(final LuaValue key) {
		return LuaTable.hashSlot(key, this.hash.length - 1);
	}

	private void hashRemove(final LuaValue key) {
		if (this.hash.length > 0) {
			final int index = this.hashSlot(key);
			for (Slot slot = this.hash[index]; slot != null; slot = slot.rest()) {
				StrongSlot foundSlot;
				if ((foundSlot = slot.find(key)) != null) {
					this.hash[index] = this.hash[index].remove(foundSlot);
					--this.hashEntries;
					return;
				}
			}
		}
	}

	private boolean checkLoadFactor() {
		return this.hashEntries >= this.hash.length;
	}

	private int countHashKeys() {
		int keys = 0;
		for (int i = 0; i < this.hash.length; ++i) {
			for (Slot slot = this.hash[i]; slot != null; slot = slot.rest()) {
				if (slot.first() != null)
					keys++;
			}
		}
		return keys;
	}

	private void dropWeakArrayValues() {
		for (int i = 0; i < this.array.length; ++i) {
			this.m_metatable.arrayget(this.array, i);
		}
	}

	private int countIntKeys(final int[] nums) {
		int total = 0;
		int i = 1;

		// Count integer keys in array part
		for (int bit = 0; bit < 31; ++bit) {
			if (i > this.array.length)
				break;
			final int j = Math.min(this.array.length, 1 << bit);
			int c = 0;
			while (i <= j) {
				if (this.array[i++ - 1] != null)
					c++;
			}
			nums[bit] = c;
			total += c;
		}

		// Count integer keys in hash part
		for (i = 0; i < this.hash.length; ++i) {
			for (Slot s = this.hash[i]; s != null; s = s.rest()) {
				int k;
				if ((k = s.arraykey(Integer.MAX_VALUE)) > 0) {
					nums[LuaTable.log2(k)]++;
					total++;
				}
			}
		}

		return total;
	}

	// Compute ceil(log2(x))
	static int log2(int x) {
		int lg = 0;
		x -= 1;
		if (x < 0)
			// 2^(-(2^31)) is approximately 0
			return Integer.MIN_VALUE;
		if ((x & 0xFFFF0000) != 0) {
			lg = 16;
			x >>>= 16;
		}
		if ((x & 0xFF00) != 0) {
			lg += 8;
			x >>>= 8;
		}
		if ((x & 0xF0) != 0) {
			lg += 4;
			x >>>= 4;
		}
		switch (x) {
		case 0x0:
			return 0;
		case 0x1:
			lg += 1;
			break;
		case 0x2:
			lg += 2;
			break;
		case 0x3:
			lg += 2;
			break;
		case 0x4:
			lg += 3;
			break;
		case 0x5:
			lg += 3;
			break;
		case 0x6:
			lg += 3;
			break;
		case 0x7:
			lg += 3;
			break;
		case 0x8:
			lg += 4;
			break;
		case 0x9:
			lg += 4;
			break;
		case 0xA:
			lg += 4;
			break;
		case 0xB:
			lg += 4;
			break;
		case 0xC:
			lg += 4;
			break;
		case 0xD:
			lg += 4;
			break;
		case 0xE:
			lg += 4;
			break;
		case 0xF:
			lg += 4;
			break;
		}
		return lg;
	}

	/*
	 * newKey > 0 is next key to insert newKey == 0 means number of keys not changing (__mode changed) newKey < 0 next key will go in hash part
	 */
	private void rehash(final int newKey) {
		if (this.m_metatable != null && (this.m_metatable.useWeakKeys() || this.m_metatable.useWeakValues())) {
			// If this table has weak entries, hashEntries is just an upper bound.
			this.hashEntries = this.countHashKeys();
			if (this.m_metatable.useWeakValues()) {
				this.dropWeakArrayValues();
			}
		}
		final int[] nums = new int[32];
		int total = this.countIntKeys(nums);
		if (newKey > 0) {
			total++;
			nums[LuaTable.log2(newKey)]++;
		}

		// Choose N such that N <= sum(nums[0..log(N)]) < 2N
		int keys = nums[0];
		int newArraySize = 0;
		for (int log = 1; log < 32; ++log) {
			keys += nums[log];
			if (total * 2 < 1 << log) {
				// Not enough integer keys.
				break;
			} else if (keys >= (1 << (log - 1))) {
				newArraySize = 1 << log;
			}
		}

		final LuaValue[] oldArray = this.array;
		final Slot[] oldHash = this.hash;
		final LuaValue[] newArray;
		final Slot[] newHash;

		// Copy existing array entries and compute number of moving entries.
		int movingToArray = 0;
		if (newKey > 0 && newKey <= newArraySize) {
			movingToArray--;
		}
		if (newArraySize != oldArray.length) {
			newArray = new LuaValue[newArraySize];
			if (newArraySize > oldArray.length) {
				for (int i = LuaTable.log2(oldArray.length + 1), j = LuaTable.log2(newArraySize) + 1; i < j; ++i) {
					movingToArray += nums[i];
				}
			} else if (oldArray.length > newArraySize) {
				for (int i = LuaTable.log2(newArraySize + 1), j = LuaTable.log2(oldArray.length) + 1; i < j; ++i) {
					movingToArray -= nums[i];
				}
			}
			System.arraycopy(oldArray, 0, newArray, 0, Math.min(oldArray.length, newArraySize));
		} else {
			newArray = this.array;
		}

		final int newHashSize = this.hashEntries - movingToArray + ((newKey < 0 || newKey > newArraySize) ? 1 : 0); // Make room for the new entry
		final int oldCapacity = oldHash.length;
		final int newCapacity;
		final int newHashMask;

		if (newHashSize > 0) {
			// round up to next power of 2.
			newCapacity = (newHashSize < LuaTable.MIN_HASH_CAPACITY) ? LuaTable.MIN_HASH_CAPACITY : 1 << LuaTable.log2(newHashSize);
			newHashMask = newCapacity - 1;
			newHash = new Slot[newCapacity];
		} else {
			newCapacity = 0;
			newHashMask = 0;
			newHash = LuaTable.NOBUCKETS;
		}

		// Move hash buckets
		for (int i = 0; i < oldCapacity; ++i) {
			for (Slot slot = oldHash[i]; slot != null; slot = slot.rest()) {
				int k;
				if ((k = slot.arraykey(newArraySize)) > 0) {
					final StrongSlot entry = slot.first();
					if (entry != null)
						newArray[k - 1] = entry.value();
				} else {
					final int j = slot.keyindex(newHashMask);
					newHash[j] = slot.relink(newHash[j]);
				}
			}
		}

		// Move array values into hash portion
		for (int i = newArraySize; i < oldArray.length;) {
			LuaValue v;
			if ((v = oldArray[i++]) != null) {
				final int slot = LuaTable.hashmod(LuaInteger.hashCode(i), newHashMask);
				Slot newEntry;
				if (this.m_metatable != null) {
					newEntry = this.m_metatable.entry(LuaValue.valueOf(i), v);
					if (newEntry == null)
						continue;
				} else {
					newEntry = LuaTable.defaultEntry(LuaValue.valueOf(i), v);
				}
				newHash[slot] = (newHash[slot] != null) ? newHash[slot].add(newEntry) : newEntry;
			}
		}

		this.hash = newHash;
		this.array = newArray;
		this.hashEntries -= movingToArray;
	}

	@Override
	public Slot entry(final LuaValue key, final LuaValue value) {
		return LuaTable.defaultEntry(key, value);
	}

	protected static boolean isLargeKey(final LuaValue key) {
		switch (key.type()) {
		case TSTRING:
			return key.rawlen() > LuaString.RECENT_STRINGS_MAX_LENGTH;
		case TNUMBER:
		case TBOOLEAN:
			return false;
		default:
			return true;
		}
	}

	protected static Entry defaultEntry(final LuaValue key, final LuaValue value) {
		if (key.isinttype()) {
			return new IntKeyEntry(key.toint(), value);
		} else if (value.type() == LuaValue.TNUMBER) {
			return new NumberValueEntry(key, value.todouble());
		} else {
			return new NormalEntry(key, value);
		}
	}

	// ----------------- sort support -----------------------------
	//
	// implemented heap sort from wikipedia
	//
	// Only sorts the contiguous array part.
	//
	/**
	 * Sort the table using a comparator.
	 * 
	 * @param comparator
	 *            {@link LuaValue} to be called to compare elements.
	 */
	public void sort(final LuaValue comparator) {
		if (this.m_metatable != null && this.m_metatable.useWeakValues()) {
			this.dropWeakArrayValues();
		}
		int n = this.array.length;
		while (n > 0 && this.array[n - 1] == null)
			--n;
		if (n > 1)
			this.heapSort(n, comparator);
	}

	private void heapSort(final int count, final LuaValue cmpfunc) {
		this.heapify(count, cmpfunc);
		for (int end = count - 1; end > 0;) {
			this.swap(end, 0);
			this.siftDown(0, --end, cmpfunc);
		}
	}

	private void heapify(final int count, final LuaValue cmpfunc) {
		for (int start = count / 2 - 1; start >= 0; --start)
			this.siftDown(start, count - 1, cmpfunc);
	}

	private void siftDown(final int start, final int end, final LuaValue cmpfunc) {
		for (int root = start; root * 2 + 1 <= end;) {
			int child = root * 2 + 1;
			if (child < end && this.compare(child, child + 1, cmpfunc))
				++child;
			if (this.compare(root, child, cmpfunc)) {
				this.swap(root, child);
				root = child;
			} else
				return;
		}
	}

	private boolean compare(final int i, final int j, final LuaValue cmpfunc) {
		LuaValue a, b;
		if (this.m_metatable == null) {
			a = this.array[i];
			b = this.array[j];
		} else {
			a = this.m_metatable.arrayget(this.array, i);
			b = this.m_metatable.arrayget(this.array, j);
		}
		if (a == null || b == null)
			return false;
		if (!cmpfunc.isnil()) {
			return cmpfunc.call(a, b).toboolean();
		} else {
			return a.lt_b(b);
		}
	}

	private void swap(final int i, final int j) {
		final LuaValue a = this.array[i];
		this.array[i] = this.array[j];
		this.array[j] = a;
	}

	/**
	 * This may be deprecated in a future release. It is recommended to count via iteration over next() instead
	 * 
	 * @return count of keys in the table
	 * */
	public int keyCount() {
		LuaValue k = LuaValue.NIL;
		for (int i = 0; true; i++) {
			final Varargs n = this.next(k);
			if ((k = n.arg1()).isnil())
				return i;
		}
	}

	/**
	 * This may be deprecated in a future release. It is recommended to use next() instead
	 * 
	 * @return array of keys in the table
	 * */
	public LuaValue[] keys() {
		final Vector l = new Vector();
		LuaValue k = LuaValue.NIL;
		while (true) {
			final Varargs n = this.next(k);
			if ((k = n.arg1()).isnil())
				break;
			l.addElement(k);
		}
		final LuaValue[] a = new LuaValue[l.size()];
		l.copyInto(a);
		return a;
	}

	// equality w/ metatable processing
	@Override
	public LuaValue eq(final LuaValue val) {
		return this.eq_b(val) ? LuaValue.TRUE : LuaValue.FALSE;
	}

	@Override
	public boolean eq_b(final LuaValue val) {
		if (this == val)
			return true;
		if (this.m_metatable == null || !val.istable())
			return false;
		final LuaValue valmt = val.getmetatable();
		return valmt != null && LuaValue.eqmtcall(this, this.m_metatable.toLuaValue(), val, valmt);
	}

	/** Unpack all the elements of this table */
	public Varargs unpack() {
		return this.unpack(1, this.length());
	}

	/** Unpack all the elements of this table from element i */
	public Varargs unpack(final int i) {
		return this.unpack(i, this.length());
	}

	/** Unpack the elements from i to j inclusive */
	public Varargs unpack(final int i, final int j) {
		int n = j + 1 - i;
		switch (n) {
		case 0:
			return LuaValue.NONE;
		case 1:
			return this.get(i);
		case 2:
			return LuaValue.varargsOf(this.get(i), this.get(i + 1));
		default:
			if (n < 0)
				return LuaValue.NONE;
			final LuaValue[] v = new LuaValue[n];
			while (--n >= 0)
				v[n] = this.get(i + n);
			return LuaValue.varargsOf(v);
		}
	}

	/**
	 * Represents a slot in the hash table.
	 */
	interface Slot {

		/** Return hash{pow2,mod}( first().key().hashCode(), sizeMask ) */
		int keyindex(int hashMask);

		/** Return first Entry, if still present, or null. */
		StrongSlot first();

		/** Compare given key with first()'s key; return first() if equal. */
		StrongSlot find(LuaValue key);

		/**
		 * Compare given key with first()'s key; return true if equal. May return true for keys no longer present in the table.
		 */
		boolean keyeq(LuaValue key);

		/** Return rest of elements */
		Slot rest();

		/**
		 * Return first entry's key, iff it is an integer between 1 and max, inclusive, or zero otherwise.
		 */
		int arraykey(int max);

		/**
		 * Set the value of this Slot's first Entry, if possible, or return a new Slot whose first entry has the given value.
		 */
		Slot set(StrongSlot target, LuaValue value);

		/**
		 * Link the given new entry to this slot.
		 */
		Slot add(Slot newEntry);

		/**
		 * Return a Slot with the given value set to nil; must not return null for next() to behave correctly.
		 */
		Slot remove(StrongSlot target);

		/**
		 * Return a Slot with the same first key and value (if still present) and rest() equal to rest.
		 */
		Slot relink(Slot rest);
	}

	/**
	 * Subclass of Slot guaranteed to have a strongly-referenced key and value, to support weak tables.
	 */
	interface StrongSlot extends Slot {
		/** Return first entry's key */
		LuaValue key();

		/** Return first entry's value */
		LuaValue value();

		/** Return varargsOf(key(), value()) or equivalent */
		Varargs toVarargs();
	}

	private static class LinkSlot implements StrongSlot {
		private Entry	entry;
		private Slot	next;

		LinkSlot(final Entry entry, final Slot next) {
			this.entry = entry;
			this.next = next;
		}

		@Override
		public LuaValue key() {
			return this.entry.key();
		}

		@Override
		public int keyindex(final int hashMask) {
			return this.entry.keyindex(hashMask);
		}

		@Override
		public LuaValue value() {
			return this.entry.value();
		}

		@Override
		public Varargs toVarargs() {
			return this.entry.toVarargs();
		}

		@Override
		public StrongSlot first() {
			return this.entry;
		}

		@Override
		public StrongSlot find(final LuaValue key) {
			return this.entry.keyeq(key) ? this : null;
		}

		@Override
		public boolean keyeq(final LuaValue key) {
			return this.entry.keyeq(key);
		}

		@Override
		public Slot rest() {
			return this.next;
		}

		@Override
		public int arraykey(final int max) {
			return this.entry.arraykey(max);
		}

		@Override
		public Slot set(final StrongSlot target, final LuaValue value) {
			if (target == this) {
				this.entry = this.entry.set(value);
				return this;
			} else {
				return this.setnext(this.next.set(target, value));
			}
		}

		@Override
		public Slot add(final Slot entry) {
			return this.setnext(this.next.add(entry));
		}

		@Override
		public Slot remove(final StrongSlot target) {
			if (this == target) {
				return new DeadSlot(this.key(), this.next);
			} else {
				this.next = this.next.remove(target);
			}
			return this;
		}

		@Override
		public Slot relink(final Slot rest) {
			// This method is (only) called during rehash, so it must not change this.next.
			return (rest != null) ? new LinkSlot(this.entry, rest) : (Slot) this.entry;
		}

		// this method ensures that this.next is never set to null.
		private Slot setnext(final Slot next) {
			if (next != null) {
				this.next = next;
				return this;
			} else {
				return this.entry;
			}
		}

		@Override
		public String toString() {
			return this.entry + "; " + this.next;
		}
	}

	/**
	 * Base class for regular entries.
	 * 
	 * <p>
	 * If the key may be an integer, the {@link #arraykey(int)} method must be overridden to handle that case.
	 */
	static abstract class Entry extends Varargs implements StrongSlot {
		@Override
		public abstract LuaValue key();

		@Override
		public abstract LuaValue value();

		abstract Entry set(LuaValue value);

		@Override
		public int arraykey(final int max) {
			return 0;
		}

		@Override
		public LuaValue arg(final int i) {
			switch (i) {
			case 1:
				return this.key();
			case 2:
				return this.value();
			}
			return LuaValue.NIL;
		}

		@Override
		public int narg() {
			return 2;
		}

		/**
		 * Subclasses should redefine as "return this;" whenever possible.
		 */
		@Override
		public Varargs toVarargs() {
			return LuaValue.varargsOf(this.key(), this.value());
		}

		@Override
		public LuaValue arg1() {
			return this.key();
		}

		@Override
		public Varargs subargs(final int start) {
			switch (start) {
			case 1:
				return this;
			case 2:
				return this.value();
			}
			return LuaValue.NONE;
		}

		@Override
		public StrongSlot first() {
			return this;
		}

		@Override
		public Slot rest() {
			return null;
		}

		@Override
		public StrongSlot find(final LuaValue key) {
			return this.keyeq(key) ? this : null;
		}

		@Override
		public Slot set(final StrongSlot target, final LuaValue value) {
			return this.set(value);
		}

		@Override
		public Slot add(final Slot entry) {
			return new LinkSlot(this, entry);
		}

		@Override
		public Slot remove(final StrongSlot target) {
			return new DeadSlot(this.key(), null);
		}

		@Override
		public Slot relink(final Slot rest) {
			return (rest != null) ? new LinkSlot(this, rest) : (Slot) this;
		}
	}

	static class NormalEntry extends Entry {
		private final LuaValue	key;
		private LuaValue		value;

		NormalEntry(final LuaValue key, final LuaValue value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public LuaValue key() {
			return this.key;
		}

		@Override
		public LuaValue value() {
			return this.value;
		}

		@Override
		public Entry set(final LuaValue value) {
			this.value = value;
			return this;
		}

		@Override
		public Varargs toVarargs() {
			return this;
		}

		@Override
		public int keyindex(final int hashMask) {
			return LuaTable.hashSlot(this.key, hashMask);
		}

		@Override
		public boolean keyeq(final LuaValue key) {
			return key.raweq(this.key);
		}
	}

	private static class IntKeyEntry extends Entry {
		private final int	key;
		private LuaValue	value;

		IntKeyEntry(final int key, final LuaValue value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public LuaValue key() {
			return LuaValue.valueOf(this.key);
		}

		@Override
		public int arraykey(final int max) {
			return (this.key >= 1 && this.key <= max) ? this.key : 0;
		}

		@Override
		public LuaValue value() {
			return this.value;
		}

		@Override
		public Entry set(final LuaValue value) {
			this.value = value;
			return this;
		}

		@Override
		public int keyindex(final int mask) {
			return LuaTable.hashmod(LuaInteger.hashCode(this.key), mask);
		}

		@Override
		public boolean keyeq(final LuaValue key) {
			return key.raweq(this.key);
		}
	}

	/**
	 * Entry class used with numeric values, but only when the key is not an integer.
	 */
	private static class NumberValueEntry extends Entry {
		private double			value;
		private final LuaValue	key;

		NumberValueEntry(final LuaValue key, final double value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public LuaValue key() {
			return this.key;
		}

		@Override
		public LuaValue value() {
			return LuaValue.valueOf(this.value);
		}

		@Override
		public Entry set(final LuaValue value) {
			final LuaValue n = value.tonumber();
			if (!n.isnil()) {
				this.value = n.todouble();
				return this;
			} else {
				return new NormalEntry(this.key, value);
			}
		}

		@Override
		public int keyindex(final int mask) {
			return LuaTable.hashSlot(this.key, mask);
		}

		@Override
		public boolean keyeq(final LuaValue key) {
			return key.raweq(this.key);
		}
	}

	/**
	 * A Slot whose value has been set to nil. The key is kept in a weak reference so that it can be found by next().
	 */
	private static class DeadSlot implements Slot {

		private final Object	key;
		private Slot			next;

		private DeadSlot(final LuaValue key, final Slot next) {
			this.key = LuaTable.isLargeKey(key) ? new WeakReference(key) : (Object) key;
			this.next = next;
		}

		private LuaValue key() {
			return (LuaValue) (this.key instanceof WeakReference ? ((WeakReference) this.key).get() : this.key);
		}

		@Override
		public int keyindex(final int hashMask) {
			// Not needed: this entry will be dropped during rehash.
			return 0;
		}

		@Override
		public StrongSlot first() {
			return null;
		}

		@Override
		public StrongSlot find(final LuaValue key) {
			return null;
		}

		@Override
		public boolean keyeq(final LuaValue key) {
			final LuaValue k = this.key();
			return k != null && key.raweq(k);
		}

		@Override
		public Slot rest() {
			return this.next;
		}

		@Override
		public int arraykey(final int max) {
			return -1;
		}

		@Override
		public Slot set(final StrongSlot target, final LuaValue value) {
			final Slot next = (this.next != null) ? this.next.set(target, value) : null;
			if (this.key() != null) {
				// if key hasn't been garbage collected, it is still potentially a valid argument
				// to next(), so we can't drop this entry yet.
				this.next = next;
				return this;
			} else {
				return next;
			}
		}

		@Override
		public Slot add(final Slot newEntry) {
			return (this.next != null) ? this.next.add(newEntry) : newEntry;
		}

		@Override
		public Slot remove(final StrongSlot target) {
			if (this.key() != null) {
				this.next = this.next.remove(target);
				return this;
			} else {
				return this.next;
			}
		}

		@Override
		public Slot relink(final Slot rest) {
			return rest;
		}

		@Override
		public String toString() {
			final StringBuffer buf = new StringBuffer();
			buf.append("<dead");
			final LuaValue k = this.key();
			if (k != null) {
				buf.append(": ");
				buf.append(k.toString());
			}
			buf.append('>');
			if (this.next != null) {
				buf.append("; ");
				buf.append(this.next.toString());
			}
			return buf.toString();
		}
	};

	private static final Slot[]	NOBUCKETS	= {};

	// Metatable operations

	@Override
	public boolean useWeakKeys() {
		return false;
	}

	@Override
	public boolean useWeakValues() {
		return false;
	}

	@Override
	public LuaValue toLuaValue() {
		return this;
	}

	@Override
	public LuaValue wrap(final LuaValue value) {
		return value;
	}

	@Override
	public LuaValue arrayget(final LuaValue[] array, final int index) {
		return array[index];
	}
}
