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

import org.luaj.vm2.Buffer;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code string} library.
 * <p>
 * Typically, this library is included as part of a call to either {@link JsePlatform#standardGlobals()} or {@link JmePlatform#standardGlobals()}
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	Globals globals = JsePlatform.standardGlobals();
 * 	System.out.println(globals.get(&quot;string&quot;).get(&quot;upper&quot;).call(LuaValue.valueOf(&quot;abcde&quot;)));
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
 * 	globals.load(new StringLib());
 * 	System.out.println(globals.get(&quot;string&quot;).get(&quot;upper&quot;).call(LuaValue.valueOf(&quot;abcde&quot;)));
 * }
 * </pre>
 * <p>
 * This is a direct port of the corresponding library in C.
 * 
 * @see LibFunction
 * @see JsePlatform
 * @see JmePlatform
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.4">Lua 5.2 String Lib Reference</a>
 */
public class StringLib extends TwoArgFunction {

	public static LuaTable	instance;

	public StringLib() {
	}

	@Override
	public LuaValue call(final LuaValue modname, final LuaValue env) {
		final LuaTable t = new LuaTable();
		this.bind(t, StringLib1.class, new String[] { "len", "lower", "reverse", "upper", });
		this.bind(t, StringLibV.class, new String[] { "find", "format", "gsub", "match", "sub" });
		env.set("string", t);

		t.set("split", new split());

		StringLib.instance = t;
		if (LuaString.s_metatable == null) {
			LuaString.s_metatable = LuaValue.tableOf(new LuaValue[] { LuaValue.INDEX, t });
		}
		env.get("package").get("loaded").set("string", t);
		return t;
	}

	private class split extends TwoArgFunction {

		@Override
		public LuaValue call(final LuaValue arg1, final LuaValue arg2) {
			final String string = arg1.checkstring().tojstring();
			final String regex = arg2.checkstring().tojstring();
			final String[] parts = string.split(regex);
			final LuaTable rv = new LuaTable();
			for (int i = 0; i < parts.length; i++) {
				rv.set(LuaValue.valueOf(i + 1), LuaValue.valueOf(parts[i]));
			}
			return rv;
		}
	}

	static final class StringLib1 extends OneArgFunction {
		@Override
		public LuaValue call(final LuaValue arg) {
			switch (this.opcode) {
			case 0:
				return StringLib.len(arg); // len (function)
			case 1:
				return StringLib.lower(arg); // lower (function)
			case 2:
				return StringLib.reverse(arg); // reverse (function)
			case 3:
				return StringLib.upper(arg); // upper (function)
			}
			return LuaValue.NIL;
		}
	}

	static final class StringLibV extends VarArgFunction {
		@Override
		public Varargs invoke(final Varargs args) {
			switch (this.opcode) {
			case 0:
				return StringLib.find(args);
			case 1:
				return StringLib.format(args);
			case 2:
				return StringLib.gsub(args);
			case 3:
				return StringLib.match(args);
			case 4:
				return StringLib.sub(args);
			}
			return LuaValue.NONE;
		}
	}

	/**
	 * string.find (s, pattern [, init [, plain]])
	 * 
	 * Looks for the first match of pattern in the string s. If it finds a match, then find returns the indices of s where this occurrence starts and ends; otherwise, it returns nil. A third, optional numerical argument init specifies where to start the search; its default value is 1 and may be negative. A value of true as a fourth, optional argument plain turns off the pattern matching facilities, so the function does a plain "find substring" operation, with no characters in pattern being considered "magic". Note that if plain is given, then init must be given as well.
	 * 
	 * If the pattern has captures, then in a successful match the captured values are also returned, after the two indices.
	 */
	static Varargs find(final Varargs args) {
		return StringLib.str_find_aux(args, true);
	}

	/**
	 * string.format (formatstring, ...)
	 * 
	 * Returns a formatted version of its variable number of arguments following the description given in its first argument (which must be a string). The format string follows the same rules as the printf family of standard C functions. The only differences are that the options/modifiers *, l, L, n, p, and h are not supported and that there is an extra option, q. The q option formats a string in a form suitable to be safely read back by the Lua interpreter: the string is written between double quotes, and all double quotes, newlines, embedded zeros, and backslashes in the string are correctly escaped when written. For instance, the call string.format('%q', 'a string with "quotes" and \n new line')
	 *
	 * will produce the string: "a string with \"quotes\" and \ new line"
	 * 
	 * The options c, d, E, e, f, g, G, i, o, u, X, and x all expect a number as argument, whereas q and s expect a string.
	 * 
	 * This function does not accept string values containing embedded zeros, except as arguments to the q option.
	 */
	static Varargs format(final Varargs args) {
		final LuaString fmt = args.checkstring(1);
		final int n = fmt.length();
		final Buffer result = new Buffer(n);
		int arg = 1;
		int c;

		for (int i = 0; i < n;) {
			switch (c = fmt.luaByte(i++)) {
			case '\n':
				result.append("\n");
				break;
			default:
				result.append((byte) c);
				break;
			case L_ESC:
				if (i < n) {
					if ((c = fmt.luaByte(i)) == StringLib.L_ESC) {
						++i;
						result.append((byte) StringLib.L_ESC);
					} else {
						arg++;
						final FormatDesc fdsc = new FormatDesc(args, fmt, i);
						i += fdsc.length;
						switch (fdsc.conversion) {
						case 'c':
							fdsc.format(result, (byte) args.checkint(arg));
							break;
						case 'i':
						case 'd':
							fdsc.format(result, args.checkint(arg));
							break;
						case 'o':
						case 'u':
						case 'x':
						case 'X':
							fdsc.format(result, args.checklong(arg));
							break;
						case 'e':
						case 'E':
						case 'f':
						case 'g':
						case 'G':
							fdsc.format(result, args.checkdouble(arg));
							break;
						case 'q':
							StringLib.addquoted(result, args.checkstring(arg));
							break;
						case 's': {
							final LuaString s = args.checkstring(arg);
							if (fdsc.precision == -1 && s.length() >= 100) {
								result.append(s);
							} else {
								fdsc.format(result, s);
							}
						}
							break;
						default:
							LuaValue.error("invalid option '%" + (char) fdsc.conversion + "' to 'format'");
							break;
						}
					}
				}
			}
		}

		return result.tostring();
	}

	private static void addquoted(final Buffer buf, final LuaString s) {
		int c;
		buf.append((byte) '"');
		for (int i = 0, n = s.length(); i < n; i++) {
			switch (c = s.luaByte(i)) {
			case '"':
			case '\\':
			case '\n':
				buf.append((byte) '\\');
				buf.append((byte) c);
				break;
			default:
				if (c <= 0x1F || c == 0x7F) {
					buf.append((byte) '\\');
					if (i + 1 == n || s.luaByte(i + 1) < '0' || s.luaByte(i + 1) > '9') {
						buf.append(Integer.toString(c));
					} else {
						buf.append((byte) '0');
						buf.append((byte) (char) ('0' + c / 10));
						buf.append((byte) (char) ('0' + c % 10));
					}
				} else {
					buf.append((byte) c);
				}
				break;
			}
		}
		buf.append((byte) '"');
	}

	private static final String	FLAGS	= "-+ #0";

	static class FormatDesc {

		private boolean				leftAdjust;
		private boolean				zeroPad;
		private boolean				explicitPlus;
		private boolean				space;
		private boolean				alternateForm;
		private static final int	MAX_FLAGS	= 5;

		private int					width;
		private int					precision;

		public final int			conversion;
		public final int			length;

		public FormatDesc(final Varargs args, final LuaString strfrmt, final int start) {
			int p = start;
			final int n = strfrmt.length();
			int c = 0;

			boolean moreFlags = true;
			while (moreFlags) {
				switch (c = p < n ? strfrmt.luaByte(p++) : 0) {
				case '-':
					this.leftAdjust = true;
					break;
				case '+':
					this.explicitPlus = true;
					break;
				case ' ':
					this.space = true;
					break;
				case '#':
					this.alternateForm = true;
					break;
				case '0':
					this.zeroPad = true;
					break;
				default:
					moreFlags = false;
					break;
				}
			}
			if (p - start > FormatDesc.MAX_FLAGS) {
				LuaValue.error("invalid format (repeated flags)");
			}

			this.width = -1;
			if (Character.isDigit((char) c)) {
				this.width = c - '0';
				c = p < n ? strfrmt.luaByte(p++) : 0;
				if (Character.isDigit((char) c)) {
					this.width = this.width * 10 + c - '0';
					c = p < n ? strfrmt.luaByte(p++) : 0;
				}
			}

			this.precision = -1;
			if (c == '.') {
				c = p < n ? strfrmt.luaByte(p++) : 0;
				if (Character.isDigit((char) c)) {
					this.precision = c - '0';
					c = p < n ? strfrmt.luaByte(p++) : 0;
					if (Character.isDigit((char) c)) {
						this.precision = this.precision * 10 + c - '0';
						c = p < n ? strfrmt.luaByte(p++) : 0;
					}
				}
			}

			if (Character.isDigit((char) c)) {
				LuaValue.error("invalid format (width or precision too long)");
			}

			this.zeroPad &= !this.leftAdjust; // '-' overrides '0'
			this.conversion = c;
			this.length = p - start;
		}

		public void format(final Buffer buf, final byte c) {
			// TODO: not clear that any of width, precision, or flags apply here.
			buf.append(c);
		}

		public void format(final Buffer buf, final long number) {
			String digits;

			if (number == 0 && this.precision == 0) {
				digits = "";
			} else {
				int radix;
				switch (this.conversion) {
				case 'x':
				case 'X':
					radix = 16;
					break;
				case 'o':
					radix = 8;
					break;
				default:
					radix = 10;
					break;
				}
				digits = Long.toString(number, radix);
				if (this.conversion == 'X') {
					digits = digits.toUpperCase();
				}
			}

			int minwidth = digits.length();
			int ndigits = minwidth;
			int nzeros;

			if (number < 0) {
				ndigits--;
			} else if (this.explicitPlus || this.space) {
				minwidth++;
			}

			if (this.precision > ndigits) {
				nzeros = this.precision - ndigits;
			} else if (this.precision == -1 && this.zeroPad && this.width > minwidth) {
				nzeros = this.width - minwidth;
			} else {
				nzeros = 0;
			}

			minwidth += nzeros;
			final int nspaces = this.width > minwidth ? this.width - minwidth : 0;

			if (!this.leftAdjust) {
				FormatDesc.pad(buf, ' ', nspaces);
			}

			if (number < 0) {
				if (nzeros > 0) {
					buf.append((byte) '-');
					digits = digits.substring(1);
				}
			} else if (this.explicitPlus) {
				buf.append((byte) '+');
			} else if (this.space) {
				buf.append((byte) ' ');
			}

			if (nzeros > 0) {
				FormatDesc.pad(buf, '0', nzeros);
			}

			buf.append(digits);

			if (this.leftAdjust) {
				FormatDesc.pad(buf, ' ', nspaces);
			}
		}

		public void format(final Buffer buf, final double x) {
			// TODO
			buf.append(String.valueOf(x));
		}

		public void format(final Buffer buf, LuaString s) {
			final int nullindex = s.indexOf((byte) '\0', 0);
			if (nullindex != -1) {
				s = s.substring(0, nullindex);
			}
			buf.append(s);
		}

		public static final void pad(final Buffer buf, final char c, int n) {
			final byte b = (byte) c;
			while (n-- > 0) {
				buf.append(b);
			}
		}
	}

	/**
	 * string.gsub (s, pattern, repl [, n]) Returns a copy of s in which all (or the first n, if given) occurrences of the pattern have been replaced by a replacement string specified by repl, which may be a string, a table, or a function. gsub also returns, as its second value, the total number of matches that occurred.
	 * 
	 * If repl is a string, then its value is used for replacement. The character % works as an escape character: any sequence in repl of the form %n, with n between 1 and 9, stands for the value of the n-th captured substring (see below). The sequence %0 stands for the whole match. The sequence %% stands for a single %.
	 * 
	 * If repl is a table, then the table is queried for every match, using the first capture as the key; if the pattern specifies no captures, then the whole match is used as the key.
	 * 
	 * If repl is a function, then this function is called every time a match occurs, with all captured substrings passed as arguments, in order; if the pattern specifies no captures, then the whole match is passed as a sole argument.
	 * 
	 * If the value returned by the table query or by the function call is a string or a number, then it is used as the replacement string; otherwise, if it is false or nil, then there is no replacement (that is, the original match is kept in the string).
	 * 
	 * Here are some examples: x = string.gsub("hello world", "(%w+)", "%1 %1") --> x="hello hello world world"
	 * 
	 * x = string.gsub("hello world", "%w+", "%0 %0", 1) --> x="hello hello world"
	 *
	 * x = string.gsub("hello world from Lua", "(%w+)%s*(%w+)", "%2 %1") --> x="world hello Lua from"
	 *
	 * x = string.gsub("home = $HOME, user = $USER", "%$(%w+)", os.getenv) --> x="home = /home/roberto, user = roberto"
	 *
	 * x = string.gsub("4+5 = $return 4+5$", "%$(.-)%$", function (s) return loadstring(s)() end) --> x="4+5 = 9"
	 *
	 * local t = {name="lua", version="5.1"} x = string.gsub("$name-$version.tar.gz", "%$(%w+)", t) --> x="lua-5.1.tar.gz"
	 */
	static Varargs gsub(final Varargs args) {
		final LuaString src = args.checkstring(1);
		final int srclen = src.length();
		final LuaString p = args.checkstring(2);
		final LuaValue repl = args.arg(3);
		final int max_s = args.optint(4, srclen + 1);
		final boolean anchor = p.length() > 0 && p.charAt(0) == '^';

		final Buffer lbuf = new Buffer(srclen);
		final MatchState ms = new MatchState(args, src, p);

		int soffset = 0;
		int n = 0;
		while (n < max_s) {
			ms.reset();
			final int res = ms.match(soffset, anchor ? 1 : 0);
			if (res != -1) {
				n++;
				ms.add_value(lbuf, soffset, res, repl);
			}
			if (res != -1 && res > soffset) {
				soffset = res;
			} else if (soffset < srclen) {
				lbuf.append((byte) src.luaByte(soffset++));
			} else {
				break;
			}
			if (anchor) {
				break;
			}
		}
		lbuf.append(src.substring(soffset, srclen));
		return LuaValue.varargsOf(lbuf.tostring(), LuaValue.valueOf(n));
	}

	/**
	 * string.len (s)
	 * 
	 * Receives a string and returns its length. The empty string "" has length 0. Embedded zeros are counted, so "a\000bc\000" has length 5.
	 */
	static LuaValue len(final LuaValue arg) {
		return arg.checkstring().len();
	}

	/**
	 * string.lower (s)
	 * 
	 * Receives a string and returns a copy of this string with all uppercase letters changed to lowercase. All other characters are left unchanged. The definition of what an uppercase letter is depends on the current locale.
	 */
	static LuaValue lower(final LuaValue arg) {
		return LuaValue.valueOf(arg.checkjstring().toLowerCase());
	}

	/**
	 * string.match (s, pattern [, init])
	 * 
	 * Looks for the first match of pattern in the string s. If it finds one, then match returns the captures from the pattern; otherwise it returns nil. If pattern specifies no captures, then the whole match is returned. A third, optional numerical argument init specifies where to start the search; its default value is 1 and may be negative.
	 */
	static Varargs match(final Varargs args) {
		return StringLib.str_find_aux(args, false);
	}

	/**
	 * string.reverse (s)
	 * 
	 * Returns a string that is the string s reversed.
	 */
	static LuaValue reverse(final LuaValue arg) {
		final LuaString s = arg.checkstring();
		final int n = s.length();
		final byte[] b = new byte[n];
		for (int i = 0, j = n - 1; i < n; i++, j--) {
			b[j] = (byte) s.luaByte(i);
		}
		return LuaString.valueOf(b);
	}

	/**
	 * string.sub (s, i [, j])
	 * 
	 * Returns the substring of s that starts at i and continues until j; i and j may be negative. If j is absent, then it is assumed to be equal to -1 (which is the same as the string length). In particular, the call string.sub(s,1,j) returns a prefix of s with length j, and string.sub(s, -i) returns a suffix of s with length i.
	 */
	static Varargs sub(final Varargs args) {
		final LuaString s = args.checkstring(1);
		final int l = s.length();

		int start = StringLib.posrelat(args.checkint(2), l);
		int end = StringLib.posrelat(args.optint(3, -1), l);

		if (start < 1) {
			start = 1;
		}
		if (end > l) {
			end = l;
		}

		if (start <= end) {
			return s.substring(start - 1, end);
		} else {
			return LuaValue.EMPTYSTRING;
		}
	}

	/**
	 * string.upper (s)
	 * 
	 * Receives a string and returns a copy of this string with all lowercase letters changed to uppercase. All other characters are left unchanged. The definition of what a lowercase letter is depends on the current locale.
	 */
	static LuaValue upper(final LuaValue arg) {
		return LuaValue.valueOf(arg.checkjstring().toUpperCase());
	}

	/**
	 * This utility method implements both string.find and string.match.
	 */
	static Varargs str_find_aux(final Varargs args, final boolean find) {
		final LuaString s = args.checkstring(1);
		final LuaString pat = args.checkstring(2);
		int init = args.optint(3, 1);

		if (init > 0) {
			init = Math.min(init - 1, s.length());
		} else if (init < 0) {
			init = Math.max(0, s.length() + init);
		}

		final boolean fastMatch = find && (args.arg(4).toboolean() || pat.indexOfAny(StringLib.SPECIALS) == -1);

		if (fastMatch) {
			final int result = s.indexOf(pat, init);
			if (result != -1) {
				return LuaValue.varargsOf(LuaValue.valueOf(result + 1), LuaValue.valueOf(result + pat.length()));
			}
		} else {
			final MatchState ms = new MatchState(args, s, pat);

			boolean anchor = false;
			int poff = 0;
			if (pat.luaByte(0) == '^') {
				anchor = true;
				poff = 1;
			}

			int soff = init;
			do {
				int res;
				ms.reset();
				if ((res = ms.match(soff, poff)) != -1) {
					if (find) {
						return LuaValue.varargsOf(LuaValue.valueOf(soff + 1), LuaValue.valueOf(res), ms.push_captures(false, soff, res));
					} else {
						return ms.push_captures(true, soff, res);
					}
				}
			} while (soff++ < s.length() && !anchor);
		}
		return LuaValue.NIL;
	}

	private static int posrelat(final int pos, final int len) {
		return pos >= 0 ? pos : len + pos + 1;
	}

	// Pattern matching implementation

	private static final int		L_ESC			= '%';
	private static final LuaString	SPECIALS		= LuaValue.valueOf("^$*+?.([%-");
	private static final int		MAX_CAPTURES	= 32;

	private static final int		CAP_UNFINISHED	= -1;
	private static final int		CAP_POSITION	= -2;

	private static final byte		MASK_ALPHA		= 0x01;
	private static final byte		MASK_LOWERCASE	= 0x02;
	private static final byte		MASK_UPPERCASE	= 0x04;
	private static final byte		MASK_DIGIT		= 0x08;
	private static final byte		MASK_PUNCT		= 0x10;
	private static final byte		MASK_SPACE		= 0x20;
	private static final byte		MASK_CONTROL	= 0x40;
	private static final byte		MASK_HEXDIGIT	= (byte) 0x80;

	private static final byte[]		CHAR_TABLE;

	static {
		CHAR_TABLE = new byte[256];

		for (int i = 0; i < 256; ++i) {
			final char c = (char) i;
			StringLib.CHAR_TABLE[i] = (byte) ((Character.isDigit(c) ? StringLib.MASK_DIGIT : 0) | (Character.isLowerCase(c) ? StringLib.MASK_LOWERCASE : 0) | (Character.isUpperCase(c) ? StringLib.MASK_UPPERCASE : 0) | (c < ' ' || c == 0x7F ? StringLib.MASK_CONTROL
					: 0));
			if (c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c >= '0' && c <= '9') {
				StringLib.CHAR_TABLE[i] |= StringLib.MASK_HEXDIGIT;
			}
			if (c >= '!' && c <= '/' || c >= ':' && c <= '@') {
				StringLib.CHAR_TABLE[i] |= StringLib.MASK_PUNCT;
			}
			if ((StringLib.CHAR_TABLE[i] & (StringLib.MASK_LOWERCASE | StringLib.MASK_UPPERCASE)) != 0) {
				StringLib.CHAR_TABLE[i] |= StringLib.MASK_ALPHA;
			}
		}

		StringLib.CHAR_TABLE[' '] = StringLib.MASK_SPACE;
		StringLib.CHAR_TABLE['\r'] |= StringLib.MASK_SPACE;
		StringLib.CHAR_TABLE['\n'] |= StringLib.MASK_SPACE;
		StringLib.CHAR_TABLE['\t'] |= StringLib.MASK_SPACE;
		StringLib.CHAR_TABLE[0x0C /* '\v' */] |= StringLib.MASK_SPACE;
		StringLib.CHAR_TABLE['\f'] |= StringLib.MASK_SPACE;
	};

	static class MatchState {
		final LuaString	s;
		final LuaString	p;
		final Varargs	args;
		int				level;
		int[]			cinit;
		int[]			clen;

		MatchState(final Varargs args, final LuaString s, final LuaString pattern) {
			this.s = s;
			this.p = pattern;
			this.args = args;
			this.level = 0;
			this.cinit = new int[StringLib.MAX_CAPTURES];
			this.clen = new int[StringLib.MAX_CAPTURES];
		}

		void reset() {
			this.level = 0;
		}

		private void add_s(final Buffer lbuf, final LuaString news, final int soff, final int e) {
			final int l = news.length();
			for (int i = 0; i < l; ++i) {
				byte b = (byte) news.luaByte(i);
				if (b != StringLib.L_ESC) {
					lbuf.append(b);
				} else {
					++i; // skip ESC
					b = (byte) news.luaByte(i);
					if (!Character.isDigit((char) b)) {
						lbuf.append(b);
					} else if (b == '0') {
						lbuf.append(this.s.substring(soff, e));
					} else {
						lbuf.append(this.push_onecapture(b - '1', soff, e).strvalue());
					}
				}
			}
		}

		public void add_value(final Buffer lbuf, final int soffset, final int end, LuaValue repl) {
			switch (repl.type()) {
			case LuaValue.TSTRING:
			case LuaValue.TNUMBER:
				this.add_s(lbuf, repl.strvalue(), soffset, end);
				return;

			case LuaValue.TFUNCTION:
				repl = repl.invoke(this.push_captures(true, soffset, end)).arg1();
				break;

			case LuaValue.TTABLE:
				// Need to call push_onecapture here for the error checking
				repl = repl.get(this.push_onecapture(0, soffset, end));
				break;

			default:
				LuaValue.error("bad argument: string/function/table expected");
				return;
			}

			if (!repl.toboolean()) {
				repl = this.s.substring(soffset, end);
			} else if (!repl.isstring()) {
				LuaValue.error("invalid replacement value (a " + repl.typename() + ")");
			}
			lbuf.append(repl.strvalue());
		}

		Varargs push_captures(final boolean wholeMatch, final int soff, final int end) {
			final int nlevels = this.level == 0 && wholeMatch ? 1 : this.level;
			switch (nlevels) {
			case 0:
				return LuaValue.NONE;
			case 1:
				return this.push_onecapture(0, soff, end);
			}
			final LuaValue[] v = new LuaValue[nlevels];
			for (int i = 0; i < nlevels; ++i) {
				v[i] = this.push_onecapture(i, soff, end);
			}
			return LuaValue.varargsOf(v);
		}

		private LuaValue push_onecapture(final int i, final int soff, final int end) {
			if (i >= this.level) {
				if (i == 0) {
					return this.s.substring(soff, end);
				} else {
					return LuaValue.error("invalid capture index");
				}
			} else {
				final int l = this.clen[i];
				if (l == StringLib.CAP_UNFINISHED) {
					return LuaValue.error("unfinished capture");
				}
				if (l == StringLib.CAP_POSITION) {
					return LuaValue.valueOf(this.cinit[i] + 1);
				} else {
					final int begin = this.cinit[i];
					return this.s.substring(begin, begin + l);
				}
			}
		}

		private int check_capture(int l) {
			l -= '1';
			if (l < 0 || l >= this.level || this.clen[l] == StringLib.CAP_UNFINISHED) {
				LuaValue.error("invalid capture index");
			}
			return l;
		}

		private int capture_to_close() {
			int level = this.level;
			for (level--; level >= 0; level--) {
				if (this.clen[level] == StringLib.CAP_UNFINISHED) {
					return level;
				}
			}
			LuaValue.error("invalid pattern capture");
			return 0;
		}

		int classend(int poffset) {
			switch (this.p.luaByte(poffset++)) {
			case L_ESC:
				if (poffset == this.p.length()) {
					LuaValue.error("malformed pattern (ends with %)");
				}
				return poffset + 1;

			case '[':
				if (this.p.luaByte(poffset) == '^') {
					poffset++;
				}
				do {
					if (poffset == this.p.length()) {
						LuaValue.error("malformed pattern (missing ])");
					}
					if (this.p.luaByte(poffset++) == StringLib.L_ESC && poffset != this.p.length()) {
						poffset++;
					}
				} while (this.p.luaByte(poffset) != ']');
				return poffset + 1;
			default:
				return poffset;
			}
		}

		static boolean match_class(final int c, final int cl) {
			final char lcl = Character.toLowerCase((char) cl);
			final int cdata = StringLib.CHAR_TABLE[c];

			boolean res;
			switch (lcl) {
			case 'a':
				res = (cdata & StringLib.MASK_ALPHA) != 0;
				break;
			case 'd':
				res = (cdata & StringLib.MASK_DIGIT) != 0;
				break;
			case 'l':
				res = (cdata & StringLib.MASK_LOWERCASE) != 0;
				break;
			case 'u':
				res = (cdata & StringLib.MASK_UPPERCASE) != 0;
				break;
			case 'c':
				res = (cdata & StringLib.MASK_CONTROL) != 0;
				break;
			case 'p':
				res = (cdata & StringLib.MASK_PUNCT) != 0;
				break;
			case 's':
				res = (cdata & StringLib.MASK_SPACE) != 0;
				break;
			case 'w':
				res = (cdata & (StringLib.MASK_ALPHA | StringLib.MASK_DIGIT)) != 0;
				break;
			case 'x':
				res = (cdata & StringLib.MASK_HEXDIGIT) != 0;
				break;
			case 'z':
				res = c == 0;
				break;
			default:
				return cl == c;
			}
			return lcl == cl ? res : !res;
		}

		boolean matchbracketclass(final int c, int poff, final int ec) {
			boolean sig = true;
			if (this.p.luaByte(poff + 1) == '^') {
				sig = false;
				poff++;
			}
			while (++poff < ec) {
				if (this.p.luaByte(poff) == StringLib.L_ESC) {
					poff++;
					if (MatchState.match_class(c, this.p.luaByte(poff))) {
						return sig;
					}
				} else if (this.p.luaByte(poff + 1) == '-' && poff + 2 < ec) {
					poff += 2;
					if (this.p.luaByte(poff - 2) <= c && c <= this.p.luaByte(poff)) {
						return sig;
					}
				} else if (this.p.luaByte(poff) == c) {
					return sig;
				}
			}
			return !sig;
		}

		boolean singlematch(final int c, final int poff, final int ep) {
			switch (this.p.luaByte(poff)) {
			case '.':
				return true;
			case L_ESC:
				return MatchState.match_class(c, this.p.luaByte(poff + 1));
			case '[':
				return this.matchbracketclass(c, poff, ep - 1);
			default:
				return this.p.luaByte(poff) == c;
			}
		}

		/**
		 * Perform pattern matching. If there is a match, returns offset into s where match ends, otherwise returns -1.
		 */
		int match(int soffset, int poffset) {
			while (true) {
				// Check if we are at the end of the pattern -
				// equivalent to the '\0' case in the C version, but our pattern
				// string is not NUL-terminated.
				if (poffset == this.p.length()) {
					return soffset;
				}
				switch (this.p.luaByte(poffset)) {
				case '(':
					if (++poffset < this.p.length() && this.p.luaByte(poffset) == ')') {
						return this.start_capture(soffset, poffset + 1, StringLib.CAP_POSITION);
					} else {
						return this.start_capture(soffset, poffset, StringLib.CAP_UNFINISHED);
					}
				case ')':
					return this.end_capture(soffset, poffset + 1);
				case L_ESC:
					if (poffset + 1 == this.p.length()) {
						LuaValue.error("malformed pattern (ends with '%')");
					}
					switch (this.p.luaByte(poffset + 1)) {
					case 'b':
						soffset = this.matchbalance(soffset, poffset + 2);
						if (soffset == -1) {
							return -1;
						}
						poffset += 4;
						continue;
					case 'f': {
						poffset += 2;
						if (this.p.luaByte(poffset) != '[') {
							LuaValue.error("Missing [ after %f in pattern");
						}
						final int ep = this.classend(poffset);
						final int previous = soffset == 0 ? -1 : this.s.luaByte(soffset - 1);
						if (this.matchbracketclass(previous, poffset, ep - 1) || this.matchbracketclass(this.s.luaByte(soffset), poffset, ep - 1)) {
							return -1;
						}
						poffset = ep;
						continue;
					}
					default: {
						final int c = this.p.luaByte(poffset + 1);
						if (Character.isDigit((char) c)) {
							soffset = this.match_capture(soffset, c);
							if (soffset == -1) {
								return -1;
							}
							return this.match(soffset, poffset + 2);
						}
					}
					}
				case '$':
					if (poffset + 1 == this.p.length()) {
						return soffset == this.s.length() ? soffset : -1;
					}
				}
				final int ep = this.classend(poffset);
				final boolean m = soffset < this.s.length() && this.singlematch(this.s.luaByte(soffset), poffset, ep);
				final int pc = ep < this.p.length() ? this.p.luaByte(ep) : '\0';

				switch (pc) {
				case '?':
					int res;
					if (m && (res = this.match(soffset + 1, ep + 1)) != -1) {
						return res;
					}
					poffset = ep + 1;
					continue;
				case '*':
					return this.max_expand(soffset, poffset, ep);
				case '+':
					return m ? this.max_expand(soffset + 1, poffset, ep) : -1;
				case '-':
					return this.min_expand(soffset, poffset, ep);
				default:
					if (!m) {
						return -1;
					}
					soffset++;
					poffset = ep;
					continue;
				}
			}
		}

		int max_expand(final int soff, final int poff, final int ep) {
			int i = 0;
			while (soff + i < this.s.length() && this.singlematch(this.s.luaByte(soff + i), poff, ep)) {
				i++;
			}
			while (i >= 0) {
				final int res = this.match(soff + i, ep + 1);
				if (res != -1) {
					return res;
				}
				i--;
			}
			return -1;
		}

		int min_expand(int soff, final int poff, final int ep) {
			for (;;) {
				final int res = this.match(soff, ep + 1);
				if (res != -1) {
					return res;
				} else if (soff < this.s.length() && this.singlematch(this.s.luaByte(soff), poff, ep)) {
					soff++;
				} else {
					return -1;
				}
			}
		}

		int start_capture(final int soff, final int poff, final int what) {
			int res;
			final int level = this.level;
			if (level >= StringLib.MAX_CAPTURES) {
				LuaValue.error("too many captures");
			}
			this.cinit[level] = soff;
			this.clen[level] = what;
			this.level = level + 1;
			if ((res = this.match(soff, poff)) == -1) {
				this.level--;
			}
			return res;
		}

		int end_capture(final int soff, final int poff) {
			final int l = this.capture_to_close();
			int res;
			this.clen[l] = soff - this.cinit[l];
			if ((res = this.match(soff, poff)) == -1) {
				this.clen[l] = StringLib.CAP_UNFINISHED;
			}
			return res;
		}

		int match_capture(final int soff, int l) {
			l = this.check_capture(l);
			final int len = this.clen[l];
			if (this.s.length() - soff >= len && LuaString.equals(this.s, this.cinit[l], this.s, soff, len)) {
				return soff + len;
			} else {
				return -1;
			}
		}

		int matchbalance(int soff, final int poff) {
			final int plen = this.p.length();
			if (poff == plen || poff + 1 == plen) {
				LuaValue.error("unbalanced pattern");
			}
			final int slen = this.s.length();
			if (soff >= slen) {
				return -1;
			}
			final int b = this.p.luaByte(poff);
			if (this.s.luaByte(soff) != b) {
				return -1;
			}
			final int e = this.p.luaByte(poff + 1);
			int cont = 1;
			while (++soff < slen) {
				if (this.s.luaByte(soff) == e) {
					if (--cont == 0) {
						return soff + 1;
					}
				} else if (this.s.luaByte(soff) == b) {
					cont++;
				}
			}
			return -1;
		}
	}
}
