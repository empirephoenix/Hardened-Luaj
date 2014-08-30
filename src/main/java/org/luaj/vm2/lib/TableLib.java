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

import org.luaj.vm2.InstructionLimit;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code table} library.
 * 
 * <p>
 * Typically, this library is included as part of a call to either {@link JsePlatform#standardGlobals()} or {@link JmePlatform#standardGlobals()}
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = JsePlatform.standardGlobals();
 * 	System.out.println(globals.get(&quot;table&quot;).get(&quot;length&quot;).call(LuaValue.tableOf()));
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
 * 	globals.load(new TableLib());
 * 	System.out.println(globals.get(&quot;table&quot;).get(&quot;length&quot;).call(LuaValue.tableOf()));
 * }
 * </pre>
 * <p>
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * 
 * @see LibFunction
 * @see JsePlatform
 * @see JmePlatform
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.5">Lua 5.2 Table Lib Reference</a>
 */
public class TableLib extends TwoArgFunction {

	@Override
	public LuaValue call(final LuaValue modname, final LuaValue env) {
		final LuaTable table = new LuaTable();
		table.set("concat", new concat());
		table.set("insert", new insert());
		table.set("pack", new pack());
		table.set("remove", new remove());
		table.set("sort", new sort());
		table.set("unpack", new unpack());
		table.set("contains", new contains());
		env.set("table", table);
		env.get("package").get("loaded").set("table", table);
		return LuaValue.NIL;
	}

	static class contains extends TableLibFunction {
		@Override
		public LuaValue call(final LuaValue list, final LuaValue contains) {
			InstructionLimit.instructionLimit().increase(10);
			list.checktable();
			final LuaTable table = (LuaTable) list;
			for (final LuaValue key : table.keys()) {
				final LuaValue value = table.get(key);
				if (value.equals(contains)) {
					return LuaValue.TRUE;
				}
			}
			return LuaValue.FALSE;
		}
	}

	// function table.contains(table, element)
	// for _, value in pairs(table) do
	// if value == element then
	// return true
	// end
	// end
	// return false
	// end
	static class TableLibFunction extends LibFunction {
		@Override
		public LuaValue call() {
			return LuaValue.argerror(1, "table expected, got no value");
		}
	}

	// "concat" (table [, sep [, i [, j]]]) -> string
	static class concat extends TableLibFunction {
		@Override
		public LuaValue call(final LuaValue list) {
			return list.checktable().concat(LuaValue.EMPTYSTRING, 1, list.length());
		}

		@Override
		public LuaValue call(final LuaValue list, final LuaValue sep) {
			return list.checktable().concat(sep.checkstring(), 1, list.length());
		}

		@Override
		public LuaValue call(final LuaValue list, final LuaValue sep, final LuaValue i) {
			return list.checktable().concat(sep.checkstring(), i.checkint(), list.length());
		}

		@Override
		public LuaValue call(final LuaValue list, final LuaValue sep, final LuaValue i, final LuaValue j) {
			return list.checktable().concat(sep.checkstring(), i.checkint(), j.checkint());
		}
	}

	// "insert" (table, [pos,] value) -> prev-ele
	static class insert extends TableLibFunction {
		@Override
		public LuaValue call(final LuaValue list) {
			return LuaValue.argerror(2, "value expected");
		}

		@Override
		public LuaValue call(final LuaValue table, final LuaValue value) {
			table.checktable().insert(table.length() + 1, value);
			return LuaValue.NONE;
		}

		@Override
		public LuaValue call(final LuaValue table, final LuaValue pos, final LuaValue value) {
			table.checktable().insert(pos.checkint(), value);
			return LuaValue.NONE;
		}
	}

	// "pack" (...) -> table
	static class pack extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaValue t = LuaValue.tableOf(args, 1);
			t.set("n", args.narg());
			return t;
		}
	}

	// "remove" (table [, pos]) -> removed-ele
	static class remove extends TableLibFunction {
		@Override
		public LuaValue call(final LuaValue list) {
			return list.checktable().remove(0);
		}

		@Override
		public LuaValue call(final LuaValue list, final LuaValue pos) {
			return list.checktable().remove(pos.checkint());
		}
	}

	// "sort" (table [, comp])
	static class sort extends TwoArgFunction {
		@Override
		public LuaValue call(final LuaValue table, final LuaValue compare) {
			table.checktable().sort(compare.isnil() ? LuaValue.NIL : compare.checkfunction());
			return LuaValue.NONE;
		}
	}

	// "unpack", // (list [,i [,j]]) -> result1, ...
	static class unpack extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			final LuaTable t = args.checktable(1);
			switch (args.narg()) {
			case 1:
				return t.unpack();
			case 2:
				return t.unpack(args.checkint(2));
			default:
				return t.unpack(args.checkint(2), args.checkint(3));
			}
		}
	}
}
