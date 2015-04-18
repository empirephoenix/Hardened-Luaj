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
package org.luaj.vm2.lib;

import org.luaj.vm2.Globals;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaNil;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Print;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.Varargs;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code debug} library.
 * <p>
 * The debug library in luaj tries to emulate the behavior of the corresponding C-based lua library. To do this, it must maintain a separate stack of calls to {@link LuaClosure} and {@link LibFunction} instances. Especially when lua-to-java bytecode
 * compiling is being used via a {@link LuaCompiler} such as {@link LuaJC}, this cannot be done in all cases.
 * <p>
 * Typically, this library is included as part of a call to either {@link JsePlatform#debugGlobals()} or {@link JmePlatform#debugGlobals()}
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = JsePlatform.debugGlobals();
 * 	System.out.println(globals.get(&quot;debug&quot;).get(&quot;traceback&quot;).call());
 * }
 * </pre>
 * <p>
 * To instantiate and use it directly, link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = new Globals();
 * 	globals.load(new JseBaseLib());
 * 	globals.load(new PackageLib());
 * 	globals.load(new DebugLib());
 * 	System.out.println(globals.get(&quot;debug&quot;).get(&quot;traceback&quot;).call());
 * }
 * </pre>
 * <p>
 * 
 * @see LibFunction
 * @see JsePlatform
 * @see JmePlatform
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.10">Lua 5.2 Debug Lib Reference</a>
 */
public class DebugLib extends TwoArgFunction {
	public static boolean			CALLS;
	public static boolean			TRACE;
	static {
		try {
			DebugLib.CALLS = (null != System.getProperty("CALLS"));
		} catch (final Exception e) {
		}
		try {
			DebugLib.TRACE = (null != System.getProperty("TRACE"));
		} catch (final Exception e) {
		}
	}

	private static final LuaString	LUA				= LuaValue.valueOf("Lua");
	private static final LuaString	QMARK			= LuaValue.valueOf("?");
	private static final LuaString	CALL			= LuaValue.valueOf("call");
	private static final LuaString	LINE			= LuaValue.valueOf("line");
	private static final LuaString	COUNT			= LuaValue.valueOf("count");
	private static final LuaString	RETURN			= LuaValue.valueOf("return");

	private static final LuaString	FUNC			= LuaValue.valueOf("func");
	private static final LuaString	ISTAILCALL		= LuaValue.valueOf("istailcall");
	private static final LuaString	ISVARARG		= LuaValue.valueOf("isvararg");
	private static final LuaString	NUPS			= LuaValue.valueOf("nups");
	private static final LuaString	NPARAMS			= LuaValue.valueOf("nparams");
	private static final LuaString	NAME			= LuaValue.valueOf("name");
	private static final LuaString	NAMEWHAT		= LuaValue.valueOf("namewhat");
	private static final LuaString	WHAT			= LuaValue.valueOf("what");
	private static final LuaString	SOURCE			= LuaValue.valueOf("source");
	private static final LuaString	SHORT_SRC		= LuaValue.valueOf("short_src");
	private static final LuaString	LINEDEFINED		= LuaValue.valueOf("linedefined");
	private static final LuaString	LASTLINEDEFINED	= LuaValue.valueOf("lastlinedefined");
	private static final LuaString	CURRENTLINE		= LuaValue.valueOf("currentline");
	private static final LuaString	ACTIVELINES		= LuaValue.valueOf("activelines");

	Globals							globals;

	@Override
	public LuaValue call(final LuaValue modname, final LuaValue env) {
		this.globals = env.checkglobals();
		this.globals.debuglib = this;
		final LuaTable debug = new LuaTable();
		debug.set("debug", new debug());
		debug.set("gethook", new gethook());
		debug.set("getinfo", new getinfo());
		debug.set("getlocal", new getlocal());
		debug.set("getmetatable", new getmetatable());
		debug.set("getregistry", new getregistry());
		debug.set("getupvalue", new getupvalue());
		debug.set("getuservalue", new getuservalue());
		debug.set("sethook", new sethook());
		debug.set("setlocal", new setlocal());
		debug.set("setmetatable", new setmetatable());
		debug.set("setupvalue", new setupvalue());
		debug.set("setuservalue", new setuservalue());
		debug.set("traceback", new traceback());
		debug.set("upvalueid", new upvalueid());
		debug.set("upvaluejoin", new upvaluejoin());
		env.set("debug", debug);
		env.get("package").get("loaded").set("debug", debug);
		return debug;
	}

	// debug.debug()
	static final class debug extends ZeroArgFunction {
		@Override
		public LuaValue call() {
			return LuaValue.NONE;
		}
	}

	// debug.gethook ([thread])
	final class gethook extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaThread t = args.narg() > 0 ? args.checkthread(1) : DebugLib.this.globals.running;
			return LuaValue.varargsOf(t.hookfunc != null ? t.hookfunc : LuaValue.NIL, LuaValue.valueOf((t.hookcall ? "c" : "") + (t.hookline ? "l" : "") + (t.hookrtrn ? "r" : "")), LuaValue.valueOf(t.hookcount));
		}
	}

	// debug.getinfo ([thread,] f [, what])
	final class getinfo extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			int a = 1;
			final LuaThread thread = args.isthread(a) ? args.checkthread(a++) : DebugLib.this.globals.running;
			LuaValue func = args.arg(a++);
			final String what = args.optjstring(a++, "flnStu");
			final DebugLib.CallStack callstack = DebugLib.this.callstack(thread);

			// find the stack info
			DebugLib.CallFrame frame;
			if (func.isnumber()) {
				frame = callstack.getCallFrame(func.toint());
				if (frame == null) {
					return LuaValue.NONE;
				}
				func = frame.f;
			} else if (func.isfunction()) {
				frame = callstack.findCallFrame(func);
			} else {
				return LuaValue.argerror(a - 2, "function or level");
			}

			// start a table
			final DebugInfo ar = callstack.auxgetinfo(what, (LuaFunction) func, frame);
			final LuaTable info = new LuaTable();
			if (what.indexOf('S') >= 0) {
				info.set(DebugLib.WHAT, DebugLib.LUA);
				info.set(DebugLib.SOURCE, LuaValue.valueOf(ar.source));
				info.set(DebugLib.SHORT_SRC, LuaValue.valueOf(ar.short_src));
				info.set(DebugLib.LINEDEFINED, LuaValue.valueOf(ar.linedefined));
				info.set(DebugLib.LASTLINEDEFINED, LuaValue.valueOf(ar.lastlinedefined));
			}
			if (what.indexOf('l') >= 0) {
				info.set(DebugLib.CURRENTLINE, LuaValue.valueOf(ar.currentline));
			}
			if (what.indexOf('u') >= 0) {
				info.set(DebugLib.NUPS, LuaValue.valueOf(ar.nups));
				info.set(DebugLib.NPARAMS, LuaValue.valueOf(ar.nparams));
				info.set(DebugLib.ISVARARG, ar.isvararg ? LuaValue.ONE : LuaValue.ZERO);
			}
			if (what.indexOf('n') >= 0) {
				info.set(DebugLib.NAME, LuaValue.valueOf(ar.name != null ? ar.name : "?"));
				info.set(DebugLib.NAMEWHAT, LuaValue.valueOf(ar.namewhat));
			}
			if (what.indexOf('t') >= 0) {
				info.set(DebugLib.ISTAILCALL, LuaValue.ZERO);
			}
			if (what.indexOf('L') >= 0) {
				final LuaTable lines = new LuaTable();
				info.set(DebugLib.ACTIVELINES, lines);
				DebugLib.CallFrame cf;
				for (int l = 1; (cf = callstack.getCallFrame(l)) != null; ++l) {
					if (cf.f == func) {
						lines.insert(-1, LuaValue.valueOf(cf.currentline()));
					}
				}
			}
			if (what.indexOf('f') >= 0) {
				if (func != null) {
					info.set(DebugLib.FUNC, func);
				}
			}
			return info;
		}
	}

	// debug.getlocal ([thread,] f, local)
	final class getlocal extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			int a = 1;
			final LuaThread thread = args.isthread(a) ? args.checkthread(a++) : DebugLib.this.globals.running;
			final int level = args.checkint(a++);
			final int local = args.checkint(a++);
			final CallFrame f = DebugLib.this.callstack(thread).getCallFrame(level);
			return f != null ? f.getLocal(local) : LuaValue.NONE;
		}
	}

	// debug.getmetatable (value)
	final class getmetatable extends LibFunction {
		@Override
		public LuaValue call(final LuaValue v) {
			final LuaValue mt = v.getmetatable();
			return mt != null ? mt : LuaValue.NIL;
		}
	}

	// debug.getregistry ()
	final class getregistry extends ZeroArgFunction {
		@Override
		public LuaValue call() {
			return DebugLib.this.globals;
		}
	}

	// debug.getupvalue (f, up)
	static final class getupvalue extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaValue func = args.checkfunction(1);
			final int up = args.checkint(2);
			if (func instanceof LuaClosure) {
				final LuaClosure c = (LuaClosure) func;
				final LuaString name = DebugLib.findupvalue(c, up);
				if (name != null) {
					return LuaValue.varargsOf(name, c.upValues[up - 1].getValue());
				}
			}
			return LuaValue.NIL;
		}
	}

	// debug.getuservalue (u)
	static final class getuservalue extends LibFunction {
		@Override
		public LuaValue call(final LuaValue u) {
			return u.isuserdata() ? u : LuaValue.NIL;
		}
	}

	// debug.sethook ([thread,] hook, mask [, count])
	final class sethook extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			int a = 1;
			final LuaThread t = args.isthread(a) ? args.checkthread(a++) : DebugLib.this.globals.running;
			final LuaValue func = args.optfunction(a++, null);
			final String str = args.optjstring(a++, "");
			final int count = args.optint(a++, 0);
			boolean call = false, line = false, rtrn = false;
			for (int i = 0; i < str.length(); i++) {
				switch (str.charAt(i)) {
				case 'c':
					call = true;
					break;
				case 'l':
					line = true;
					break;
				case 'r':
					rtrn = true;
					break;
				}
			}
			t.hookfunc = func;
			t.hookcall = call;
			t.hookline = line;
			t.hookcount = count;
			t.hookrtrn = rtrn;
			return LuaValue.NONE;
		}
	}

	// debug.setlocal ([thread,] level, local, value)
	final class setlocal extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			int a = 1;
			final LuaThread thread = args.isthread(a) ? args.checkthread(a++) : DebugLib.this.globals.running;
			final int level = args.checkint(a++);
			final int local = args.checkint(a++);
			final LuaValue value = args.arg(a++);
			final CallFrame f = DebugLib.this.callstack(thread).getCallFrame(level);
			return f != null ? f.setLocal(local, value) : LuaValue.NONE;
		}
	}

	// debug.setmetatable (value, table)
	final class setmetatable extends TwoArgFunction {
		@Override
		public LuaValue call(final LuaValue value, final LuaValue table) {
			final LuaValue mt = table.opttable(null);
			switch (value.type()) {
			case TNIL:
				LuaNil.s_metatable = mt;
				break;
			case TNUMBER:
				LuaNumber.s_metatable = mt;
				break;
			case TBOOLEAN:
				LuaBoolean.s_metatable = mt;
				break;
			case TSTRING:
				LuaString.s_metatable = mt;
				break;
			case TFUNCTION:
				LuaFunction.s_metatable = mt;
				break;
			case TTHREAD:
				LuaThread.s_metatable = mt;
				break;
			default:
				value.setmetatable(mt);
			}
			return value;
		}
	}

	// debug.setupvalue (f, up, value)
	final class setupvalue extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaValue func = args.checkfunction(1);
			final int up = args.checkint(2);
			final LuaValue value = args.arg(3);
			if (func instanceof LuaClosure) {
				final LuaClosure c = (LuaClosure) func;
				final LuaString name = DebugLib.findupvalue(c, up);
				if (name != null) {
					c.upValues[up - 1].setValue(value);
					return name;
				}
			}
			return LuaValue.NIL;
		}
	}

	// debug.setuservalue (udata, value)
	final class setuservalue extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final Object o = args.checkuserdata(1);
			final LuaValue v = args.checkvalue(2);
			final LuaUserdata u = (LuaUserdata) args.arg1();
			u.m_instance = v.checkuserdata();
			u.m_metatable = v.getmetatable();
			return LuaValue.NONE;
		}
	}

	// debug.traceback ([thread,] [message [, level]])
	final class traceback extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			int a = 1;
			final LuaThread thread = args.isthread(a) ? args.checkthread(a++) : DebugLib.this.globals.running;
			final String message = args.optjstring(a++, null);
			final int level = args.optint(a++, 1);
			final String tb = DebugLib.this.callstack(thread).traceback(level);
			return LuaValue.valueOf(message != null ? message + "\n" + tb : tb);
		}
	}

	// debug.upvalueid (f, n)
	final class upvalueid extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaValue func = args.checkfunction(1);
			final int up = args.checkint(2);
			if (func instanceof LuaClosure) {
				final LuaClosure c = (LuaClosure) func;
				if (c.upValues != null && up > 0 && up <= c.upValues.length) {
					return LuaValue.valueOf(c.upValues[up - 1].hashCode());
				}
			}
			return LuaValue.NIL;
		}
	}

	// debug.upvaluejoin (f1, n1, f2, n2)
	final class upvaluejoin extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaClosure f1 = args.checkclosure(1);
			final int n1 = args.checkint(2);
			final LuaClosure f2 = args.checkclosure(3);
			final int n2 = args.checkint(4);
			if (n1 < 1 || n1 > f1.upValues.length) {
				this.argerror("index out of range");
			}
			if (n2 < 1 || n2 > f2.upValues.length) {
				this.argerror("index out of range");
			}
			f1.upValues[n1 - 1] = f2.upValues[n2 - 1];
			return LuaValue.NONE;
		}
	}

	public void onCall(final LuaFunction f) {
		final LuaThread t = this.globals.running;
		if (t.inhook) {
			return;
		}
		this.callstack().onCall(f);
		if (t.hookcall && t.hookfunc != null) {
			this.callHook(DebugLib.CALL, LuaValue.NIL);
		}
	}

	public void onCall(final LuaClosure c, final Varargs varargs, final LuaValue[] stack) {
		final LuaThread t = this.globals.running;
		if (t.inhook) {
			return;
		}
		this.callstack().onCall(c, varargs, stack);
		if (t.hookcall && t.hookfunc != null) {
			this.callHook(DebugLib.CALL, LuaValue.NIL);
		}
	}

	public void onInstruction(final int pc, final Varargs v, final int top) {
		final LuaThread t = this.globals.running;
		if (t.inhook) {
			return;
		}
		this.callstack().onInstruction(pc, v, top);
		if (t.hookfunc == null) {
			return;
		}
		if (t.hookcount > 0) {
			if (++t.bytecodes % t.hookcount == 0) {
				this.callHook(DebugLib.COUNT, LuaValue.NIL);
			}
		}
		if (t.hookline) {
			final int newline = this.callstack().currentline();
			if (newline != t.lastline) {
				t.lastline = newline;
				this.callHook(DebugLib.LINE, LuaValue.valueOf(newline));
			}
		}
	}

	public void onReturn() {
		final LuaThread t = this.globals.running;
		if (t.inhook) {
			return;
		}
		this.callstack().onReturn();
		if (t.hookcall && t.hookfunc != null) {
			this.callHook(DebugLib.RETURN, LuaValue.NIL);
		}
	}

	public String traceback(final int level) {
		return this.callstack().traceback(level);
	}

	void callHook(final LuaValue type, final LuaValue arg) {
		final LuaThread t = this.globals.running;
		t.inhook = true;
		try {
			t.hookfunc.call(type, arg);
		} catch (final LuaError e) {
			throw e;
		} catch (final RuntimeException e) {
			final LuaError repeat = LuaClosure.logException(e);
			throw repeat;
		} finally {
			t.inhook = false;
		}
	}

	CallStack callstack() {
		return this.callstack(this.globals.running);
	}

	CallStack callstack(final LuaThread t) {
		if (t.callstack == null) {
			t.callstack = new CallStack();
		}
		return (CallStack) t.callstack;
	}

	static class DebugInfo {
		String		name;				/* (n) */
		String		namewhat;			/* (n) 'global', 'local', 'field', 'method' */
		String		what;				/* (S) 'Lua', 'C', 'main', 'tail' */
		String		source;			/* (S) */
		int			currentline;		/* (l) */
		int			linedefined;		/* (S) */
		int			lastlinedefined;	/* (S) */
		short		nups;				/* (u) number of upvalues */
		short		nparams;			/* (u) number of parameters */
		boolean		isvararg;			/* (u) */
		boolean		istailcall;		/* (t) */
		String		short_src;			/* (S) */
		CallFrame	cf;				/* active function */

		public void funcinfo(final LuaFunction f) {
			if (f.isclosure()) {
				final Prototype p = f.checkclosure().p;
				this.source = p.source != null ? p.source.tojstring() : "=?";
				this.linedefined = p.linedefined;
				this.lastlinedefined = p.lastlinedefined;
				this.what = (this.linedefined == 0) ? "main" : "Lua";
				this.short_src = p.shortsource();
			} else {
				this.source = "=[Java]";
				this.linedefined = -1;
				this.lastlinedefined = -1;
				this.what = "Java";
				this.short_src = f.name();
			}
		}
	}

	public static class CallStack {
		final static CallFrame[]	EMPTY	= {};
		CallFrame[]					frame	= CallStack.EMPTY;
		int							calls	= 0;

		CallStack() {
		}

		int currentline() {
			return this.calls > 0 ? this.frame[this.calls - 1].currentline() : -1;
		}

		private CallFrame pushcall() {
			if (this.calls >= this.frame.length) {
				final int n = Math.max(4, this.frame.length * 3 / 2);
				final CallFrame[] f = new CallFrame[n];
				System.arraycopy(this.frame, 0, f, 0, this.frame.length);
				for (int i = this.frame.length; i < n; ++i) {
					f[i] = new CallFrame();
				}
				this.frame = f;
				for (int i = 1; i < n; ++i) {
					f[i].previous = f[i - 1];
				}
			}
			return this.frame[this.calls++];
		}

		final void onCall(final LuaFunction function) {
			this.pushcall().set(function);
		}

		final void onCall(final LuaClosure function, final Varargs varargs, final LuaValue[] stack) {
			this.pushcall().set(function, varargs, stack);
		}

		final void onReturn() {
			if (this.calls > 0) {
				this.frame[--this.calls].reset();
			}
		}

		final void onInstruction(final int pc, final Varargs v, final int top) {
			this.frame[this.calls - 1].instr(pc, v, top);
		}

		/**
		 * Get the traceback starting at a specific level.
		 * 
		 * @param level
		 * @return String containing the traceback.
		 */
		String traceback(int level) {
			final StringBuffer sb = new StringBuffer();
			sb.append("stack traceback:");
			for (DebugLib.CallFrame c; (c = this.getCallFrame(level++)) != null;) {
				sb.append("\n\t");
				sb.append(c.shortsource());
				sb.append(':');
				if (c.currentline() > 0) {
					sb.append(c.currentline() + ":");
				}
				sb.append(" in ");
				final DebugInfo ar = this.auxgetinfo("n", c.f, c);
				if (c.linedefined() == 0) {
					sb.append("main chunk");
				} else if (ar.name != null) {
					sb.append("function '");
					sb.append(ar.name);
					sb.append('\'');
				} else {
					sb.append("function <" + c.shortsource() + ":" + c.linedefined() + ">");
				}
			}
			sb.append("\n\t[Java]: in ?");
			return sb.toString();
		}

		DebugLib.CallFrame getCallFrame(final int level) {
			if (level < 1 || level > this.calls) {
				return null;
			}
			return this.frame[this.calls - level];
		}

		DebugLib.CallFrame findCallFrame(final LuaValue func) {
			for (int i = 1; i <= this.calls; ++i) {
				if (this.frame[this.calls - i].f == func) {
					return this.frame[i];
				}
			}
			return null;
		}

		DebugInfo auxgetinfo(final String what, final LuaFunction f, final CallFrame ci) {
			final DebugInfo ar = new DebugInfo();
			for (int i = 0, n = what.length(); i < n; ++i) {
				switch (what.charAt(i)) {
				case 'S':
					ar.funcinfo(f);
					break;
				case 'l':
					ar.currentline = ci != null && ci.f.isclosure() ? ci.currentline() : -1;
					break;
				case 'u':
					if (f != null && f.isclosure()) {
						final Prototype p = f.checkclosure().p;
						ar.nups = (short) p.upvalues.length;
						ar.nparams = (short) p.numparams;
						ar.isvararg = p.is_vararg != 0;
					} else {
						ar.nups = 0;
						ar.isvararg = true;
						ar.nparams = 0;
					}
					break;
				case 't':
					ar.istailcall = false;
					break;
				case 'n': {
					/* calling function is a known Lua function? */
					if (ci != null && ci.previous != null) {
						if (ci.previous.f.isclosure()) {
							final NameWhat nw = DebugLib.getfuncname(ci.previous);
							if (nw != null) {
								ar.name = nw.name;
								ar.namewhat = nw.namewhat;
							}
						}
					}
					if (ar.namewhat == null) {
						ar.namewhat = ""; /* not found */
						ar.name = null;
					}
					break;
				}
				case 'L':
				case 'f':
					break;
				default:
					// TODO: return bad status.
					break;
				}
			}
			return ar;
		}

	}

	static class CallFrame {
		LuaFunction	f;
		int			pc;
		int			top;
		Varargs		v;
		LuaValue[]	stack;
		CallFrame	previous;

		void set(final LuaClosure function, final Varargs varargs, final LuaValue[] stack) {
			this.f = function;
			this.v = varargs;
			this.stack = stack;
		}

		public String shortsource() {
			return this.f.isclosure() ? this.f.checkclosure().p.shortsource() : "[Java]";
		}

		void set(final LuaFunction function) {
			this.f = function;
		}

		void reset() {
			this.f = null;
			this.v = null;
			this.stack = null;
		}

		void instr(final int pc, final Varargs v, final int top) {
			this.pc = pc;
			this.v = v;
			this.top = top;
			if (DebugLib.TRACE) {
				Print.printState(this.f.checkclosure(), pc, this.stack, top, v);
			}
		}

		Varargs getLocal(final int i) {
			final LuaString name = this.getlocalname(i);
			if (name != null) {
				return LuaValue.varargsOf(name, this.stack[i - 1]);
			} else {
				return LuaValue.NIL;
			}
		}

		Varargs setLocal(final int i, final LuaValue value) {
			final LuaString name = this.getlocalname(i);
			if (name != null) {
				this.stack[i - 1] = value;
				return name;
			} else {
				return LuaValue.NIL;
			}
		}

		int currentline() {
			if (!this.f.isclosure()) {
				return -1;
			}
			final int[] li = this.f.checkclosure().p.lineinfo;
			return li == null || this.pc < 0 || this.pc >= li.length ? -1 : li[this.pc];
		}

		String sourceline() {
			if (!this.f.isclosure()) {
				return this.f.tojstring();
			}
			return this.f.checkclosure().p.shortsource() + ":" + this.currentline();
		}

		private int linedefined() {
			return this.f.isclosure() ? this.f.checkclosure().p.linedefined : -1;
		}

		LuaString getlocalname(final int index) {
			if (!this.f.isclosure()) {
				return null;
			}
			return this.f.checkclosure().p.getlocalname(index, this.pc);
		}
	}

	static LuaString findupvalue(final LuaClosure c, final int up) {
		if (c.upValues != null && up > 0 && up <= c.upValues.length) {
			if (c.p.upvalues != null && up <= c.p.upvalues.length) {
				return c.p.upvalues[up - 1].name;
			} else {
				return LuaString.valueOf("." + up);
			}
		}
		return null;
	}

	static void lua_assert(final boolean x) {
		if (!x) {
			throw new RuntimeException("lua_assert failed");
		}
	}

	static class NameWhat {
		final String	name;
		final String	namewhat;

		NameWhat(final String name, final String namewhat) {
			this.name = name;
			this.namewhat = namewhat;
		}
	}

	// Return the name info if found, or null if no useful information could be found.
	static NameWhat getfuncname(final DebugLib.CallFrame frame) {
		if (!frame.f.isclosure()) {
			return new NameWhat(frame.f.classnamestub(), "Java");
		}
		final Prototype p = frame.f.checkclosure().p;
		final int pc = frame.pc;
		final int i = p.code[pc]; /* calling instruction */
		LuaString tm;
		switch (Lua.GET_OPCODE(i)) {
		case Lua.OP_CALL:
		case Lua.OP_TAILCALL: /* get function name */
			return DebugLib.getobjname(p, pc, Lua.GETARG_A(i));
		case Lua.OP_TFORCALL: /* for iterator */
			return new NameWhat("(for iterator)", "(for iterator");
			/* all other instructions can call only through metamethods */
		case Lua.OP_SELF:
		case Lua.OP_GETTABUP:
		case Lua.OP_GETTABLE:
			tm = LuaValue.INDEX;
			break;
		case Lua.OP_SETTABUP:
		case Lua.OP_SETTABLE:
			tm = LuaValue.NEWINDEX;
			break;
		case Lua.OP_EQ:
			tm = LuaValue.EQ;
			break;
		case Lua.OP_ADD:
			tm = LuaValue.ADD;
			break;
		case Lua.OP_SUB:
			tm = LuaValue.SUB;
			break;
		case Lua.OP_MUL:
			tm = LuaValue.MUL;
			break;
		case Lua.OP_DIV:
			tm = LuaValue.DIV;
			break;
		case Lua.OP_MOD:
			tm = LuaValue.MOD;
			break;
		case Lua.OP_POW:
			tm = LuaValue.POW;
			break;
		case Lua.OP_UNM:
			tm = LuaValue.UNM;
			break;
		case Lua.OP_LEN:
			tm = LuaValue.LEN;
			break;
		case Lua.OP_LT:
			tm = LuaValue.LT;
			break;
		case Lua.OP_LE:
			tm = LuaValue.LE;
			break;
		case Lua.OP_CONCAT:
			tm = LuaValue.CONCAT;
			break;
		default:
			return null; /* else no useful name can be found */
		}
		return new NameWhat(tm.tojstring(), "metamethod");
	}

	// return NameWhat if found, null if not
	public static NameWhat getobjname(final Prototype p, final int lastpc, final int reg) {
		int pc = lastpc; // currentpc(L, ci);
		LuaString name = p.getlocalname(reg + 1, pc);
		if (name != null) {
			return new NameWhat(name.tojstring(), "local");
		}

		/* else try symbolic execution */
		pc = DebugLib.findsetreg(p, lastpc, reg);
		if (pc != -1) { /* could find instruction? */
			final int i = p.code[pc];
			switch (Lua.GET_OPCODE(i)) {
			case Lua.OP_MOVE: {
				final int a = Lua.GETARG_A(i);
				final int b = Lua.GETARG_B(i); /* move from `b' to `a' */
				if (b < a) {
					return DebugLib.getobjname(p, pc, b); /* get name for `b' */
				}
				break;
			}
			case Lua.OP_GETTABUP:
			case Lua.OP_GETTABLE: {
				final int k = Lua.GETARG_C(i); /* key index */
				final int t = Lua.GETARG_B(i); /* table index */
				final LuaString vn = (Lua.GET_OPCODE(i) == Lua.OP_GETTABLE) /* name of indexed variable */
				? p.getlocalname(t + 1, pc) : (t < p.upvalues.length ? p.upvalues[t].name : DebugLib.QMARK);
						name = DebugLib.kname(p, k);
						return new NameWhat(name.tojstring(), vn != null && vn.eq_b(LuaValue.ENV) ? "global" : "field");
			}
			case Lua.OP_GETUPVAL: {
				final int u = Lua.GETARG_B(i); /* upvalue index */
				name = u < p.upvalues.length ? p.upvalues[u].name : DebugLib.QMARK;
				return new NameWhat(name.tojstring(), "upvalue");
			}
			case Lua.OP_LOADK:
			case Lua.OP_LOADKX: {
				final int b = (Lua.GET_OPCODE(i) == Lua.OP_LOADK) ? Lua.GETARG_Bx(i) : Lua.GETARG_Ax(p.code[pc + 1]);
				if (p.k[b].isstring()) {
					name = p.k[b].strvalue();
					return new NameWhat(name.tojstring(), "constant");
				}
				break;
			}
			case Lua.OP_SELF: {
				final int k = Lua.GETARG_C(i); /* key index */
				name = DebugLib.kname(p, k);
				return new NameWhat(name.tojstring(), "method");
			}
			default:
				break;
			}
		}
		return null; /* no useful name found */
	}

	static LuaString kname(final Prototype p, final int c) {
		if (Lua.ISK(c) && p.k[Lua.INDEXK(c)].isstring()) {
			return p.k[Lua.INDEXK(c)].strvalue();
		} else {
			return DebugLib.QMARK;
		}
	}

	/*
	 * * try to find last instruction before 'lastpc' that modified register 'reg'
	 */
	static int findsetreg(final Prototype p, final int lastpc, final int reg) {
		int pc;
		int setreg = -1; /* keep last instruction that changed 'reg' */
		for (pc = 0; pc < lastpc; pc++) {
			final int i = p.code[pc];
			final int op = Lua.GET_OPCODE(i);
			final int a = Lua.GETARG_A(i);
			switch (op) {
			case Lua.OP_LOADNIL: {
				final int b = Lua.GETARG_B(i);
				if (a <= reg && reg <= a + b) {
					setreg = pc;
				}
				break;
			}
			case Lua.OP_TFORCALL: {
				if (reg >= a + 2) {
					setreg = pc; /* affect all regs above its base */
				}
				break;
			}
			case Lua.OP_CALL:
			case Lua.OP_TAILCALL: {
				if (reg >= a) {
					setreg = pc; /* affect all registers above base */
				}
				break;
			}
			case Lua.OP_JMP: {
				final int b = Lua.GETARG_sBx(i);
				final int dest = pc + 1 + b;
				/* jump is forward and do not skip `lastpc'? */
				if (pc < dest && dest <= lastpc) {
					pc += b; /* do the jump */
				}
				break;
			}
			case Lua.OP_TEST: {
				if (reg == a) {
					setreg = pc; /* jumped code can change 'a' */
				}
				break;
			}
			default:
				if (Lua.testAMode(op) && reg == a) {
					setreg = pc;
				}
				break;
			}
		}
		return setreg;
	}
}
