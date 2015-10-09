/*******************************************************************************
 * Copyright (c) 2012 Luaj.org. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.ResourceFinder;

/**
 * Global environment used by luaj. Contains global variables referenced by executing lua.
 * <p>
 * 
 * <h3>Constructing and Initializing Instances</h3>
 * Typically, this is constructed indirectly by a call to {@link JsePlatform.standardGlobasl()} or {@link JmePlatform.standardGlobals()}, and then used to load lua scripts for execution as in the following example.
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = JsePlatform.standardGlobals();
 * 	globals.load(new StringReader(&quot;print 'hello'&quot;), &quot;main.lua&quot;).call();
 * }
 * </pre>
 * 
 * The creates a complete global environment with the standard libraries loaded.
 * <p>
 * For specialized circumstances, the Globals may be constructed directly and loaded with only those libraries that are needed, for example.
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = new Globals();
 * 	globals.load(new BaseLib());
 * }
 * </pre>
 * 
 * <h3>Loading and Executing Lua Code</h3>
 * Globals contains convenience functions to load and execute lua source code given a Reader. A simple example is:
 * 
 * <pre>
 * {@code
 * globals.load( new StringReader("print 'hello'"), "main.lua" ).call(); 
 * }
 * </pre>
 * 
 * <h3>Fine-Grained Control of Compiling and Loading Lua</h3>
 * Executable LuaFunctions are created from lua code in several steps
 * <ul>
 * <li>find the resource using the platform's {@link ResourceFinder}
 * <li>compile lua to lua bytecode using {@link Compiler}
 * <li>load lua bytecode to a {@link LuaPrototpye} using {@link Undumper}
 * <li>construct {@link LuaClosure} from {@link Prototype} with {@link Globals} using {@link Loader}
 * </ul>
 * <p>
 * There are alternate flows when the direct lua-to-Java bytecode compiling {@link LuaJC} is used.
 * <ul>
 * <li>compile lua to lua bytecode using {@link Compiler} or load precompiled code using {@link Undumper}
 * <li>convert lua bytecode to equivalent Java bytecode using {@link LuaJC} that implements {@link Loader} directly
 * </ul>
 * 
 * <h3>Java Field</h3>
 * Certain public fields are provided that contain the current values of important global state:
 * <ul>
 * <li>{@link STDIN} Current value for standard input in the laaded IoLib, if any.
 * <li>{@link STDOUT} Current value for standard output in the loaded IoLib, if any.
 * <li>{@link STDERR} Current value for standard error in the loaded IoLib, if any.
 * <li>{@link finder} Current loaded {@link ResourceFinder}, if any.
 * <li>{@link compiler} Current loaded {@link Compiler}, if any.
 * <li>{@link undumper} Current loaded {@link Undumper}, if any.
 * <li>{@link loader} Current loaded {@link Loader}, if any.
 * </ul>
 * 
 * <h3>Lua Environment Variables</h3>
 * When using {@link JsePlatform} or {@link JmePlatform}, these environment variables are created within the Globals.
 * <ul>
 * <li>"_G" Pointer to this Globals.
 * <li>"_VERSION" String containing the version of luaj.
 * </ul>
 * 
 * <h3>Use in Multithreaded Environments</h3> In a multi-threaded server environment, each server thread should create one Globals instance, which will be logically distinct and not interfere with each other, but share certain static immutable
 * resources such as class data and string data.
 * <p>
 * 
 * @see org.luaj.vm2.lib.jse.JsePlatform
 * @see org.luaj.vm2.lib.jme.JmePlatform
 * @see LuaValue
 * @see Compiler
 * @see Loader
 * @see Undumper
 * @see ResourceFinder
 * @see LuaC
 * @see LuaJC
 */
public class Globals extends LuaTable {

	public BlockingQueue<String>	consoleQueue	= new LinkedBlockingQueue<String>(32);

	/** The installed ResourceFinder for looking files by name. */
	public ResourceFinder			finder;

	/** The currently running thread. Should not be changed by non-library code. */
	public LuaThread				running			= new LuaThread(this);

	/** The BaseLib instance loaded into this Globals */
	public BaseLib					baselib;

	/** The PackageLib instance loaded into this Globals */
	public PackageLib				package_;

	/** The DebugLib instance loaded into this Globals, or null if debugging is not enabled */
	public DebugLib					debuglib;

	/** Interface for module that converts a Prototype into a LuaFunction with an environment. */
	public interface Loader {
		/** Convert the prototype into a LuaFunction with the supplied environment. */
		LuaFunction load(Prototype prototype, String chunkname, LuaValue env) throws IOException;
	}

	/** Interface for module that converts lua source text into a prototype. */
	public interface Compiler {
		/** Compile lua source into a Prototype. The InputStream is assumed to be in UTF-8. */
		Prototype compile(InputStream stream, String chunkname) throws IOException;
	}

	/** Interface for module that loads lua binary chunk into a prototype. */
	public interface Undumper {
		/** Load the supplied input stream into a prototype. */
		Prototype undump(InputStream stream, String chunkname) throws IOException;
	}

	/** Check that this object is a Globals object, and return it, otherwise throw an error. */
	@Override
	public Globals checkglobals() {
		return this;
	}

	/**
	 * The installed loader.
	 * 
	 * @see Loader
	 */
	public Loader	loader;

	/**
	 * The installed compiler.
	 * 
	 * @see Compiler
	 */
	public Compiler	compiler;

	/**
	 * The installed undumper.
	 * 
	 * @see Undumper
	 */
	public Undumper	undumper;

	/**
	 * Convenience function for loading a file that is lua source.
	 * 
	 * @param filename
	 *            Name of the file to load.
	 * @return LuaValue that can be call()'ed or invoke()'ed.
	 * @throws LuaError
	 *             if the file could not be loaded.
	 */
	public LuaValue loadfile(final String filename) {
		try {
			return this.load(this.finder.findResource(filename), "@" + filename, "t", this);
		} catch (final Exception e) {
			final LuaError repeat = LuaClosure.logException(e);
			throw new LuaError("load " + filename + ": ", repeat);
		}
	}

	/**
	 * Convenience function to load a string value as a script. Must be lua source.
	 * 
	 * @param script
	 *            Contents of a lua script, such as "print 'hello, world.'"
	 * @param chunkname
	 *            Name that will be used within the chunk as the source.
	 * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
	 * @throws LuaError
	 *             if the script could not be compiled.
	 */
	public LuaValue load(final String script, final String chunkname) {
		return this.load(new StrReader(script), chunkname);
	}

	/**
	 * Convenience function to load a string value as a script. Must be lua source.
	 * 
	 * @param script
	 *            Contents of a lua script, such as "print 'hello, world.'"
	 * @return LuaValue that may be executed via .call(), .invoke(), or .method() calls.
	 * @throws LuaError
	 *             if the script could not be compiled.
	 */
	public LuaValue load(final String script, final int limit) {
		if (script.length() > limit) {
			throw new RuntimeException("Script to long " + script.length() + "/" + limit);
		}
		System.out.println("Size " + script.length() + "/" + limit);
		return this.load(new StrReader(script), script);
	}

	/**
	 * Load the content form a reader as a text file. Must be lua source. The source is converted to UTF-8, so any characters appearing in quoted literals above the range 128 will be converted into multiple bytes.
	 */
	private LuaValue load(final Reader reader, final String chunkname) {
		return this.load(new UTF8Stream(reader), chunkname, "t", this);
	}

	/** Load the content form an input stream as a binary chunk or text file. */
	private LuaValue load(final InputStream is, final String chunkname, final String mode, final LuaValue env) {
		try {
			final Prototype p = this.loadPrototype(is, chunkname, mode);
			return this.loader.load(p, chunkname, env);
		} catch (final LuaError l) {
			throw l;
		} catch (final Exception e) {
			final LuaError repeat = LuaClosure.logException(e);
			throw new LuaError("load " + chunkname + ": ", repeat);
		}
	}

	/**
	 * Load lua source or lua binary from an input stream into a Prototype. The InputStream is either a binary lua chunk starting with the lua binary chunk signature, or a text input file. If it is a text input file, it is interpreted as a UTF-8 byte
	 * sequence.
	 */
	public Prototype loadPrototype(InputStream is, final String chunkname, final String mode) throws IOException {
		if (mode.indexOf('b') >= 0) {
			if (this.undumper == null) {
				LuaValue.error("No undumper.");
			}
			if (!is.markSupported()) {
				is = new BufferedStream(is);
			}
			is.mark(4);
			final Prototype p = this.undumper.undump(is, chunkname);
			if (p != null) {
				return p;
			}
			is.reset();
		}
		if (mode.indexOf('t') >= 0) {
			return this.compilePrototype(is, chunkname);
		}
		LuaValue.error("Failed to load prototype " + chunkname + " using mode '" + mode + "'");
		return null;
	}

	/**
	 * Compile lua source from a Reader into a Prototype. The characters in the reader are converted to bytes using the UTF-8 encoding, so a string literal containing characters with codepoints 128 or above will be converted into multiple bytes.
	 */
	public Prototype compilePrototype(final Reader reader, final String chunkname) throws IOException {
		return this.compilePrototype(new UTF8Stream(reader), chunkname);
	}

	/**
	 * Compile lua source from an InputStream into a Prototype. The input is assumed to be UTf-8, but since bytes in the range 128-255 are passed along as literal bytes, any ASCII-compatible encoding such as ISO 8859-1 may also be used.
	 */
	public Prototype compilePrototype(final InputStream stream, final String chunkname) throws IOException {
		if (this.compiler == null) {
			LuaValue.error("No compiler.");
		}
		return this.compiler.compile(stream, chunkname);
	}

	/**
	 * Function which yields the current thread.
	 * 
	 * @param args
	 *            Arguments to supply as return values in the resume function of the resuming thread.
	 * @return Values supplied as arguments to the resume() call that reactivates this thread.
	 */
	public Varargs yield(final Varargs args) {
		if (this.running == null || this.running.isMainThread()) {
			throw new LuaError("cannot yield main thread");
		}
		final LuaThread.State s = this.running.state;
		return s.lua_yield(args);
	}

	/** Reader implementation to read chars from a String in JME or JSE. */
	static class StrReader extends Reader {
		final String	s;
		int				i	= 0;
		final int		n;

		StrReader(final String s) {
			this.s = s;
			this.n = s.length();
		}

		@Override
		public void close() throws IOException {
			this.i = this.n;
		}

		@Override
		public int read() throws IOException {
			return this.i < this.n ? this.s.charAt(this.i++) : -1;
		}

		@Override
		public int read(final char[] cbuf, final int off, final int len) throws IOException {
			int j = 0;
			for (; j < len && this.i < this.n; ++j, ++this.i) {
				cbuf[off + j] = this.s.charAt(this.i);
			}
			return j > 0 || len == 0 ? j : -1;
		}
	}

	/*
	 * Abstract base class to provide basic buffered input storage and delivery. This class may be moved to its own package in the future.
	 */
	abstract static class AbstractBufferedStream extends InputStream {
		protected byte[]	b;
		protected int		i	= 0, j = 0;

		protected AbstractBufferedStream(final int buflen) {
			this.b = new byte[buflen];
		}

		abstract protected int avail() throws IOException;

		@Override
		public int read() throws IOException {
			final int a = this.avail();
			return a <= 0 ? -1 : 0xff & this.b[this.i++];
		}

		@Override
		public int read(final byte[] b) throws IOException {
			return this.read(b, 0, b.length);
		}

		@Override
		public int read(final byte[] b, final int i0, final int n) throws IOException {
			final int a = this.avail();
			if (a <= 0) {
				return -1;
			}
			final int n_read = Math.min(a, n);
			System.arraycopy(this.b, this.i, b, i0, n_read);
			this.i += n_read;
			return n_read;
		}

		@Override
		public long skip(final long n) throws IOException {
			final long k = Math.min(n, this.j - this.i);
			this.i += k;
			return k;
		}

		@Override
		public int available() throws IOException {
			return this.j - this.i;
		}
	}

	/**
	 * Simple converter from Reader to InputStream using UTF8 encoding that will work on both JME and JSE. This class may be moved to its own package in the future.
	 */
	static class UTF8Stream extends AbstractBufferedStream {
		private final char[]	c	= new char[32];
		private final Reader	r;

		UTF8Stream(final Reader r) {
			super(96);
			this.r = r;
		}

		@Override
		protected int avail() throws IOException {
			if (this.i < this.j) {
				return this.j - this.i;
			}
			int n = this.r.read(this.c);
			if (n < 0) {
				return -1;
			}
			if (n == 0) {
				final int u = this.r.read();
				if (u < 0) {
					return -1;
				}
				this.c[0] = (char) u;
				n = 1;
			}
			this.j = LuaString.encodeToUtf8(this.c, n, this.b, this.i = 0);
			return this.j;
		}

		@Override
		public void close() throws IOException {
			this.r.close();
		}
	}

	/**
	 * Simple buffered InputStream that supports mark. Used to examine an InputStream for a 4-byte binary lua signature, and fall back to text input when the signature is not found, as well as speed up normal compilation and reading of lua scripts.
	 * This class may be moved to its own package in the future.
	 */
	static class BufferedStream extends AbstractBufferedStream {
		private final InputStream	s;

		public BufferedStream(final InputStream s) {
			this(128, s);
		}

		BufferedStream(final int buflen, final InputStream s) {
			super(buflen);
			this.s = s;
		}

		@Override
		protected int avail() throws IOException {
			if (this.i < this.j) {
				return this.j - this.i;
			}
			if (this.j >= this.b.length) {
				this.i = this.j = 0;
			}
			// leave previous bytes in place to implement mark()/reset().
			int n = this.s.read(this.b, this.j, this.b.length - this.j);
			if (n < 0) {
				return -1;
			}
			if (n == 0) {
				final int u = this.s.read();
				if (u < 0) {
					return -1;
				}
				this.b[this.j] = (byte) u;
				n = 1;
			}
			this.j += n;
			return n;
		}

		@Override
		public void close() throws IOException {
			this.s.close();
		}

		@Override
		public synchronized void mark(final int n) {
			if (this.i > 0 || n > this.b.length) {
				final byte[] dest = n > this.b.length ? new byte[n] : this.b;
				System.arraycopy(this.b, this.i, dest, 0, this.j - this.i);
				this.j -= this.i;
				this.i = 0;
				this.b = dest;
			}
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public synchronized void reset() throws IOException {
			this.i = 0;
		}
	}

	public int getUsedMemory() {
		final HashSet<LuaValue> visited = new HashSet<>();
		return this.getSizeOfObject(this, visited);
	}

	private int getSizeOfObject(final LuaValue value, final HashSet<LuaValue> visited) {
		if (value == null || visited.contains(value)) {
			return 0;
		}
		visited.add(value);
		if (value instanceof LuaInteger) {
			return 4;
		} else if (value instanceof LuaDouble) {
			return 8;
		} else if (value instanceof LuaBoolean) {
			return 1;
		} else if (value instanceof LuaNil) {
			return 0;
		} else if (value instanceof LuaString) {
			final LuaString casted = (LuaString) value;
			return casted.m_length;
		} else if (value instanceof LuaTable) {
			final LuaTable casted = (LuaTable) value;
			int size = 0;
			for (final LuaValue key : casted.keys()) {
				final LuaValue childValue = casted.get(key);
				size += this.getSizeOfObject(childValue, visited);
			}
			return size;
		} else if (value instanceof LuaClosure) {
			int size = 0;
			final LuaClosure casted = (LuaClosure) value;

			// upvalue size
			for (final UpValue uValue : casted.upValues) {
				for (final LuaValue uaValue : uValue.array) {
					size += this.getSizeOfObject(uaValue, visited);
				}
			}

			// global PK size
			size += this.getSizeOfObject(casted.globals, visited);
			for (final LuaValue kValue : casted.p.k) {
				size += this.getSizeOfObject(kValue, visited);
			}

			// instruction size
			size += casted.p.code.length * 4;

			// stackvalue size
			if (casted.getCurrentStack() != null) {
				for (final LuaValue stackValue : casted.getCurrentStack()) {
					size += this.getSizeOfObject(stackValue, visited);
				}
			}
			return size;
		} else if (value instanceof LibFunction) {
			return 10;
		} else {
			System.out.println("Unknown type " + value.getClass());
		}
		return 0;
	}

	public void console(final String toConsole) {
		while (!this.consoleQueue.offer(toConsole)) {
			this.yield(LuaValue.NIL);
		}
	}
}
