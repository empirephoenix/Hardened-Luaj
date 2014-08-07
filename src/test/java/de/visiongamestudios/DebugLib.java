package de.visiongamestudios;
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

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Subclass of {@link LibFunction} which implements the standard lua {@code os} library.
 * <p>
 * It is a usable base with simplified stub functions for library functions that cannot be implemented uniformly on Jse and Jme.
 * <p>
 * This can be installed as-is on either platform, or extended and refined to be used in a complete Jse implementation.
 * <p>
 * Because the nature of the {@code os} library is to encapsulate os-specific features, the behavior of these functions varies considerably from their counterparts in the C platform.
 * <p>
 * The following functions have limited implementations of features that are not supported well on Jme:
 * <ul>
 * <li>{@code execute()}</li>
 * <li>{@code remove()}</li>
 * <li>{@code rename()}</li>
 * <li>{@code tmpname()}</li>
 * </ul>
 * <p>
 * Typically, this library is included as part of a call to either {@link JsePlatform#standardGlobals()} or {@link JmePlatform#standardGlobals()}
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = JsePlatform.standardGlobals();
 * 	System.out.println(globals.get(&quot;os&quot;).get(&quot;time&quot;).call());
 * }
 * </pre>
 * 
 * In this example the platform-specific {@link JseOsLib} library will be loaded, which will include the base functionality provided by this class.
 * <p>
 * To instantiate and use it directly, link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = new Globals();
 * 	globals.load(new JseBaseLib());
 * 	globals.load(new PackageLib());
 * 	globals.load(new OsLib());
 * 	System.out.println(globals.get(&quot;os&quot;).get(&quot;time&quot;).call());
 * }
 * </pre>
 * <p>
 * 
 * @see LibFunction
 * @see JseOsLib
 * @see JsePlatform
 * @see JmePlatform
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.8">http://www.lua.org/manual/5.1/manual.html#5.8</a>
 */
public class DebugLib extends TwoArgFunction {
	protected Globals	globals;

	/**
	 * Create and OsLib instance.
	 */
	public DebugLib() {
	}

	@Override
	public LuaValue call(final LuaValue modname, final LuaValue env) {
		this.globals = env.checkglobals();
		final LuaTable door = new LuaTable();
		door.set("printTable", new PrintTable());
		env.set("debug", door);
		env.get("package").get("loaded").set("debug", door);
		return door;
	}

	class PrintTable extends VarArgFunction {
		public PrintTable() {
			this.name = "printTable";
		}

		@Override
		public Varargs invoke(final Varargs args) {
			System.out.println("Content{ " + args + " }");
			return LuaValue.NONE;
		};
	}

	/**
	 * Returns the value of the process environment variable varname, or null if the variable is not defined.
	 * 
	 * @param varname
	 * @return String value, or null if not defined
	 */
	protected String getenv(final String varname) {
		System.out.println("Get Env " + varname);
		return null;
	}
}
