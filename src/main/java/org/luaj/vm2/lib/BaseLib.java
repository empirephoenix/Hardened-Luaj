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
package org.luaj.vm2.lib;

import java.io.IOException;
import java.io.InputStream;

import org.luaj.vm2.Globals;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JseBaseLib;

/**
 * Subclass of {@link LibFunction} which implements the lua basic library functions.
 * <p>
 * This contains all library functions listed as "basic functions" in the lua documentation for JME. The functions dofile and loadfile use the {@link #finder} instance to find resource files. Since JME has no file system by default, {@link BaseLib}
 * implements {@link ResourceFinder} using {@link Class#getResource(String)}, which is the closest equivalent on JME. The default loader chain in {@link PackageLib} will use these as well.
 * <p>
 * To use basic library functions that include a {@link ResourceFinder} based on directory lookup, use {@link JseBaseLib} instead.
 * <p>
 * Typically, this library is included as part of a call to either {@link JsePlatform#standardGlobals()} or {@link JmePlatform#standardGlobals()}
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = JsePlatform.standardGlobals();
 * 	globals.get(&quot;print&quot;).call(LuaValue.valueOf(&quot;hello, world&quot;));
 * }
 * </pre>
 * <p>
 * For special cases where the smallest possible footprint is desired, a minimal set of libraries could be loaded directly via {@link Globals#load(LuaValue)} using code such as:
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = new Globals();
 * 	globals.load(new JseBaseLib());
 * 	globals.get(&quot;print&quot;).call(LuaValue.valueOf(&quot;hello, world&quot;));
 * }
 * </pre>
 * 
 * Doing so will ensure the library is properly initialized and loaded into the globals table.
 * <p>
 * This is a direct port of the corresponding library in C.
 * 
 * @see JseBaseLib
 * @see ResourceFinder
 * @see #finder
 * @see LibFunction
 * @see JsePlatform
 * @see JmePlatform
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.1">Lua 5.2 Base Lib Reference</a>
 */
public class BaseLib extends TwoArgFunction implements ResourceFinder {

	Globals	globals;

	@Override
	public LuaValue call(final LuaValue modname, final LuaValue env) {
		this.globals = env.checkglobals();
		this.globals.finder = this;
		this.globals.baselib = this;
		env.set("_G", env);
		env.set("_VERSION", Lua._VERSION);
		env.set("assert", new _assert());
		env.set("collectgarbage", new collectgarbage());
		env.set("error", new error());
		env.set("getmetatable", new getmetatable());
		env.set("pcall", new pcall());
		env.set("print", new print(this));
		env.set("rawequal", new rawequal());
		env.set("rawget", new rawget());
		env.set("rawlen", new rawlen());
		env.set("rawset", new rawset());
		env.set("select", new select());
		env.set("setmetatable", new setmetatable());
		env.set("tonumber", new tonumber());
		env.set("tostring", new tostring());
		env.set("type", new type());
		env.set("xpcall", new xpcall());

		next next;
		env.set("next", next = new next());
		env.set("pairs", new pairs(next));
		env.set("ipairs", new ipairs());

		return env;
	}

	/**
	 * ResourceFinder implementation
	 * 
	 * Tries to open the file as a resource, which can work for JSE and JME.
	 */
	@Override
	public InputStream findResource(final String filename) {
		return this.getClass().getResourceAsStream(filename.startsWith("/") ? filename : "/" + filename);
	}

	// "assert", // ( v [,message] ) -> v, message | ERR
	static final class _assert extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			if (!args.arg1().toboolean())
				LuaValue.error(args.narg() > 1 ? args.optjstring(2, "assertion failed!") : "assertion failed!");
			return args;
		}
	}

	// "collectgarbage", // ( opt [,arg] ) -> value
	static final class collectgarbage extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final String s = args.checkjstring(1);
			if ("collect".equals(s)) {
				System.gc();
				return LuaValue.ZERO;
			} else if ("count".equals(s)) {
				final Runtime rt = Runtime.getRuntime();
				final long used = rt.totalMemory() - rt.freeMemory();
				return LuaValue.varargsOf(LuaValue.valueOf(used / 1024.), LuaValue.valueOf(used % 1024));
			} else if ("step".equals(s)) {
				System.gc();
				return LuaValue.TRUE;
			} else {
				this.argerror("gc op");
			}
			return LuaValue.NIL;
		}
	}

	// "error", // ( message [,level] ) -> ERR
	static final class error extends TwoArgFunction {
		@Override
		public LuaValue call(final LuaValue arg1, final LuaValue arg2) {
			throw new LuaError(arg1.isnil() ? null : arg1.tojstring(), arg2.optint(1));
		}
	}

	// "getmetatable", // ( object ) -> table
	static final class getmetatable extends LibFunction {
		@Override
		public LuaValue call() {
			return LuaValue.argerror(1, "value");
		}

		@Override
		public LuaValue call(final LuaValue arg) {
			final LuaValue mt = arg.getmetatable();
			return mt != null ? mt.rawget(LuaValue.METATABLE).optvalue(mt) : LuaValue.NIL;
		}
	}

	// "pcall", // (f, arg1, ...) -> status, result1, ...
	final class pcall extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaValue func = args.checkvalue(1);
			if (BaseLib.this.globals != null && BaseLib.this.globals.debuglib != null)
				BaseLib.this.globals.debuglib.onCall(this);
			try {
				return LuaValue.varargsOf(LuaValue.TRUE, func.invoke(args.subargs(2)));
			} catch (final LuaError le) {
				final String m = le.getMessage();
				return LuaValue.varargsOf(LuaValue.FALSE, m != null ? LuaValue.valueOf(m) : LuaValue.NIL);
			} catch (final Exception e) {
				final String m = e.getMessage();
				return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(m != null ? m : e.toString()));
			} finally {
				if (BaseLib.this.globals != null && BaseLib.this.globals.debuglib != null)
					BaseLib.this.globals.debuglib.onReturn();
			}
		}
	}

	// "print", // (...) -> void
	final class print extends VarArgFunction {
		final BaseLib	baselib;

		print(final BaseLib baselib) {
			this.baselib = baselib;
		}

		@Override
		public Varargs invoke(final Varargs args) {
			final LuaValue tostring = BaseLib.this.globals.get("tostring");
			for (int i = 1, n = args.narg(); i <= n; i++) {
				if (i > 1)
					BaseLib.this.globals.STDOUT.print('\t');
				final LuaString s = tostring.call(args.arg(i)).strvalue();
				BaseLib.this.globals.STDOUT.print(s.tojstring());
			}
			BaseLib.this.globals.STDOUT.println();
			return LuaValue.NONE;
		}
	}

	// "rawequal", // (v1, v2) -> boolean
	static final class rawequal extends LibFunction {
		@Override
		public LuaValue call() {
			return LuaValue.argerror(1, "value");
		}

		@Override
		public LuaValue call(final LuaValue arg) {
			return LuaValue.argerror(2, "value");
		}

		@Override
		public LuaValue call(final LuaValue arg1, final LuaValue arg2) {
			return LuaValue.valueOf(arg1.raweq(arg2));
		}
	}

	// "rawget", // (table, index) -> value
	static final class rawget extends LibFunction {
		@Override
		public LuaValue call() {
			return LuaValue.argerror(1, "value");
		}

		@Override
		public LuaValue call(final LuaValue arg) {
			return LuaValue.argerror(2, "value");
		}

		@Override
		public LuaValue call(final LuaValue arg1, final LuaValue arg2) {
			return arg1.checktable().rawget(arg2);
		}
	}

	// "rawlen", // (v) -> value
	static final class rawlen extends LibFunction {
		@Override
		public LuaValue call(final LuaValue arg) {
			return LuaValue.valueOf(arg.rawlen());
		}
	}

	// "rawset", // (table, index, value) -> table
	static final class rawset extends LibFunction {
		@Override
		public LuaValue call(final LuaValue table) {
			return LuaValue.argerror(2, "value");
		}

		@Override
		public LuaValue call(final LuaValue table, final LuaValue index) {
			return LuaValue.argerror(3, "value");
		}

		@Override
		public LuaValue call(final LuaValue table, final LuaValue index, final LuaValue value) {
			final LuaTable t = table.checktable();
			t.rawset(index.checknotnil(), value);
			return t;
		}
	}

	// "select", // (f, ...) -> value1, ...
	static final class select extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final int n = args.narg() - 1;
			if (args.arg1().equals(LuaValue.valueOf("#")))
				return LuaValue.valueOf(n);
			final int i = args.checkint(1);
			if (i == 0 || i < -n)
				LuaValue.argerror(1, "index out of range");
			return args.subargs(i < 0 ? n + i + 2 : i + 1);
		}
	}

	// "setmetatable", // (table, metatable) -> table
	static final class setmetatable extends LibFunction {
		@Override
		public LuaValue call(final LuaValue table) {
			return LuaValue.argerror(2, "value");
		}

		@Override
		public LuaValue call(final LuaValue table, final LuaValue metatable) {
			final LuaValue mt0 = table.getmetatable();
			if (mt0 != null && !mt0.rawget(LuaValue.METATABLE).isnil())
				LuaValue.error("cannot change a protected metatable");
			return table.setmetatable(metatable.isnil() ? null : metatable.checktable());
		}
	}

	// "tonumber", // (e [,base]) -> value
	static final class tonumber extends LibFunction {
		@Override
		public LuaValue call(final LuaValue e) {
			return e.tonumber();
		}

		@Override
		public LuaValue call(final LuaValue e, final LuaValue base) {
			if (base.isnil())
				return e.tonumber();
			final int b = base.checkint();
			if (b < 2 || b > 36)
				LuaValue.argerror(2, "base out of range");
			return e.checkstring().tonumber(b);
		}
	}

	// "tostring", // (e) -> value
	static final class tostring extends LibFunction {
		@Override
		public LuaValue call(final LuaValue arg) {
			final LuaValue h = arg.metatag(LuaValue.TOSTRING);
			if (!h.isnil())
				return h.call(arg);
			final LuaValue v = arg.tostring();
			if (!v.isnil())
				return v;
			return LuaValue.valueOf(arg.tojstring());
		}
	}

	// "type", // (v) -> value
	static final class type extends LibFunction {
		@Override
		public LuaValue call(final LuaValue arg) {
			return LuaValue.valueOf(arg.typename());
		}
	}

	// "xpcall", // (f, err) -> result1, ...
	final class xpcall extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaThread t = BaseLib.this.globals.running;
			final LuaValue preverror = t.errorfunc;
			t.errorfunc = args.checkvalue(2);
			try {
				if (BaseLib.this.globals != null && BaseLib.this.globals.debuglib != null)
					BaseLib.this.globals.debuglib.onCall(this);
				try {
					return LuaValue.varargsOf(LuaValue.TRUE, args.arg1().invoke(args.subargs(3)));
				} catch (final LuaError le) {
					final String m = le.getMessage();
					return LuaValue.varargsOf(LuaValue.FALSE, m != null ? LuaValue.valueOf(m) : LuaValue.NIL);
				} catch (final Exception e) {
					final String m = e.getMessage();
					return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(m != null ? m : e.toString()));
				} finally {
					if (BaseLib.this.globals != null && BaseLib.this.globals.debuglib != null)
						BaseLib.this.globals.debuglib.onReturn();
				}
			} finally {
				t.errorfunc = preverror;
			}
		}
	}

	// "pairs" (t) -> iter-func, t, nil
	static final class pairs extends VarArgFunction {
		final next	next;

		pairs(final next next) {
			this.next = next;
		}

		@Override
		public Varargs invoke(final Varargs args) {
			return LuaValue.varargsOf(this.next, args.checktable(1), LuaValue.NIL);
		}
	}

	// // "ipairs", // (t) -> iter-func, t, 0
	static final class ipairs extends VarArgFunction {
		inext	inext	= new inext();

		@Override
		public Varargs invoke(final Varargs args) {
			return LuaValue.varargsOf(this.inext, args.checktable(1), LuaValue.ZERO);
		}
	}

	// "next" ( table, [index] ) -> next-index, next-value
	static final class next extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			return args.checktable(1).next(args.arg(2));
		}
	}

	// "inext" ( table, [int-index] ) -> next-index, next-value
	static final class inext extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			return args.checktable(1).inext(args.arg(2));
		}
	}

	private static class StringInputStream extends InputStream {
		final LuaValue	func;
		byte[]			bytes;
		int				offset, remaining = 0;

		StringInputStream(final LuaValue func) {
			this.func = func;
		}

		@Override
		public int read() throws IOException {
			if (this.remaining <= 0) {
				final LuaValue s = this.func.call();
				if (s.isnil())
					return -1;
				final LuaString ls = s.strvalue();
				this.bytes = ls.m_bytes;
				this.offset = ls.m_offset;
				this.remaining = ls.m_length;
				if (this.remaining <= 0)
					return -1;
			}
			--this.remaining;
			return this.bytes[this.offset++];
		}
	}
}
