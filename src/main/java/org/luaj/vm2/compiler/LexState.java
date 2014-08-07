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
package org.luaj.vm2.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

import org.luaj.vm2.LocVars;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.FuncState.BlockCnt;
import org.luaj.vm2.lib.MathLib;

public class LexState {

	protected static final String	RESERVED_LOCAL_VAR_FOR_CONTROL		= "(for control)";
	protected static final String	RESERVED_LOCAL_VAR_FOR_STATE		= "(for state)";
	protected static final String	RESERVED_LOCAL_VAR_FOR_GENERATOR	= "(for generator)";
	protected static final String	RESERVED_LOCAL_VAR_FOR_STEP			= "(for step)";
	protected static final String	RESERVED_LOCAL_VAR_FOR_LIMIT		= "(for limit)";
	protected static final String	RESERVED_LOCAL_VAR_FOR_INDEX		= "(for index)";

	// keywords array
	protected static final String[]	RESERVED_LOCAL_VAR_KEYWORDS			= new String[] { LexState.RESERVED_LOCAL_VAR_FOR_CONTROL, LexState.RESERVED_LOCAL_VAR_FOR_GENERATOR, LexState.RESERVED_LOCAL_VAR_FOR_INDEX,
			LexState.RESERVED_LOCAL_VAR_FOR_LIMIT, LexState.RESERVED_LOCAL_VAR_FOR_STATE, LexState.RESERVED_LOCAL_VAR_FOR_STEP };
	private static final Hashtable	RESERVED_LOCAL_VAR_KEYWORDS_TABLE	= new Hashtable();
	static {
		for (int i = 0; i < LexState.RESERVED_LOCAL_VAR_KEYWORDS.length; i++)
			LexState.RESERVED_LOCAL_VAR_KEYWORDS_TABLE.put(LexState.RESERVED_LOCAL_VAR_KEYWORDS[i], Boolean.TRUE);
	}

	private static final int		EOZ									= (-1);
	private static final int		MAX_INT								= Integer.MAX_VALUE - 2;
	private static final int		UCHAR_MAX							= 255;														// TODO, convert to unicode CHAR_MAX?
	private static final int		LUAI_MAXCCALLS						= 200;

	private static final String LUA_QS(final String s) {
		return "'" + s + "'";
	}

	private static final String LUA_QL(final Object o) {
		return LexState.LUA_QS(String.valueOf(o));
	}

	private static final int		LUA_COMPAT_LSTR		= 1;	// 1 for compatibility, 2 for old behavior
			private static final boolean	LUA_COMPAT_VARARG	= true;

	public static boolean isReservedKeyword(final String varName) {
		return LexState.RESERVED_LOCAL_VAR_KEYWORDS_TABLE.containsKey(varName);
	}

	/*
	 * * Marks the end of a patch list. It is an invalid value both as an absolute* address, and as a list link (would link an element to itself).
	 */
			static final int	NO_JUMP	= (-1);

			/*
	 * * grep "ORDER OPR" if you change these enums
	 */
			static final int	OPR_ADD	= 0, OPR_SUB = 1, OPR_MUL = 2, OPR_DIV = 3, OPR_MOD = 4, OPR_POW = 5, OPR_CONCAT = 6, OPR_NE = 7, OPR_EQ = 8, OPR_LT = 9, OPR_LE = 10, OPR_GT = 11, OPR_GE = 12, OPR_AND = 13, OPR_OR = 14, OPR_NOBINOPR = 15;

			static final int	OPR_MINUS	= 0, OPR_NOT = 1, OPR_LEN = 2, OPR_NOUNOPR = 3;

			/* exp kind */
			static final int	VVOID		= 0, /* no value */
									VNIL = 1, VTRUE = 2, VFALSE = 3, VK = 4, /* info = index of constant in `k' */
									VKNUM = 5, /* nval = numerical value */
									VNONRELOC = 6, /* info = result register */
									VLOCAL = 7, /* info = local register */
									VUPVAL = 8, /* info = index of upvalue in `upvalues' */
									VINDEXED = 9, /* info = table register, aux = index register (or `k') */
									VJMP = 10, /* info = instruction pc */
									VRELOCABLE = 11, /* info = instruction pc */
									VCALL = 12, /* info = instruction pc */
									VVARARG = 13;									/* info = instruction pc */

	/* semantics information */
			private static class SemInfo {
				LuaValue	r;
				LuaString	ts;
			};

			private static class Token {
				int				token;
				final SemInfo	seminfo	= new SemInfo();

		public void set(final Token other) {
					this.token = other.token;
					this.seminfo.r = other.seminfo.r;
					this.seminfo.ts = other.seminfo.ts;
				}
			};

	int						current;														/* current character (charint) */
			int						linenumber;													/* input line counter */
			int						lastline;														/* line of last token `consumed' */
			final Token				t				= new Token();									/* current token */
			final Token				lookahead		= new Token();									/* look ahead token */
			FuncState				fs;															/* `FuncState' is private to the parser */
			LuaC					L;
			InputStream				z;																/* input stream */
			char[]					buff;															/* buffer for tokens */
			int						nbuff;															/* length of buffer */
			Dyndata					dyd				= new Dyndata();								/* dynamic structures used by the parser */
			LuaString				source;														/* current source name */
			LuaString				envn;															/* environment variable name */
			byte					decpoint;														/* locale decimal point */

			/* ORDER RESERVED */
			final static String		luaX_tokens[]	= { "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while", "..", "...", "==",
			">=", "<=", "~=", "::", "<eos>", "<number>", "<name>", "<string>", "<eof>", };

			final static int
							/* terminal symbols denoted by reserved words */
							TK_AND			= 257, TK_BREAK = 258, TK_DO = 259, TK_ELSE = 260, TK_ELSEIF = 261, TK_END = 262, TK_FALSE = 263, TK_FOR = 264, TK_FUNCTION = 265, TK_GOTO = 266, TK_IF = 267, TK_IN = 268, TK_LOCAL = 269, TK_NIL = 270,
			TK_NOT = 271, TK_OR = 272, TK_REPEAT = 273, TK_RETURN = 274, TK_THEN = 275, TK_TRUE = 276, TK_UNTIL = 277, TK_WHILE = 278,
			/* other terminal symbols */
			TK_CONCAT = 279, TK_DOTS = 280, TK_EQ = 281, TK_GE = 282, TK_LE = 283, TK_NE = 284, TK_DBCOLON = 285, TK_EOS = 286, TK_NUMBER = 287, TK_NAME = 288, TK_STRING = 289;

	final static int		FIRST_RESERVED	= LexState.TK_AND;
			final static int		NUM_RESERVED	= LexState.TK_WHILE + 1 - LexState.FIRST_RESERVED;

	final static Hashtable	RESERVED		= new Hashtable();
			static {
				for (int i = 0; i < LexState.NUM_RESERVED; i++) {
					final LuaString ts = LuaValue.valueOf(LexState.luaX_tokens[i]);
					LexState.RESERVED.put(ts, new Integer(LexState.FIRST_RESERVED + i));
				}
			}

			private boolean isalnum(final int c) {
				return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c == '_');
				// return Character.isLetterOrDigit(c);
			}

	private boolean isalpha(final int c) {
				return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
			}

	private boolean isdigit(final int c) {
				return (c >= '0' && c <= '9');
	}

	private boolean isxdigit(final int c) {
				return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	private boolean isspace(final int c) {
				return (c <= ' ');
			}

	public LexState(final LuaC state, final InputStream stream) {
				this.z = stream;
				this.buff = new char[32];
				this.L = state;
			}

			void nextChar() {
				try {
			this.current = this.z.read();
				} catch (final IOException e) {
					e.printStackTrace();
					this.current = LexState.EOZ;
				}
			}

			boolean currIsNewline() {
				return this.current == '\n' || this.current == '\r';
			}

			void save_and_next() {
				this.save(this.current);
				this.nextChar();
			}

			void save(final int c) {
				if (this.buff == null || this.nbuff + 1 > this.buff.length)
					this.buff = LuaC.realloc(this.buff, this.nbuff * 2 + 1);
				this.buff[this.nbuff++] = (char) c;
			}

	String token2str(final int token) {
				if (token < LexState.FIRST_RESERVED) {
					return LexState.iscntrl(token) ? this.L.pushfstring("char(" + (token) + ")") : this.L.pushfstring(String.valueOf((char) token));
				} else {
					return LexState.luaX_tokens[token - LexState.FIRST_RESERVED];
				}
			}

			private static boolean iscntrl(final int token) {
				return token < ' ';
			}

			String txtToken(final int token) {
				switch (token) {
				case TK_NAME:
				case TK_STRING:
				case TK_NUMBER:
					return new String(this.buff, 0, this.nbuff);
				default:
					return this.token2str(token);
				}
			}

			void lexerror(final String msg, final int token) {
				final String cid = Lua.chunkid(this.source.tojstring());
				this.L.pushfstring(cid + ":" + this.linenumber + ": " + msg);
				if (token != 0)
					this.L.pushfstring("syntax error: " + msg + " near " + this.txtToken(token));
				throw new LuaError(cid + ":" + this.linenumber + ": " + msg);
			}

			void syntaxerror(final String msg) {
				this.lexerror(msg, this.t.token);
			}

			// only called by new_localvarliteral() for var names.
			LuaString newstring(final String s) {
				return this.L.newTString(s);
			}

			LuaString newstring(final char[] chars, final int offset, final int len) {
				return this.L.newTString(new String(chars, offset, len));
			}

			void inclinenumber() {
				final int old = this.current;
				LuaC._assert(this.currIsNewline());
				this.nextChar(); /* skip '\n' or '\r' */
				if (this.currIsNewline() && this.current != old)
					this.nextChar(); /* skip '\n\r' or '\r\n' */
				if (++this.linenumber >= LexState.MAX_INT)
					this.syntaxerror("chunk has too many lines");
			}

			void setinput(final LuaC L, final int firstByte, final InputStream z, final LuaString source) {
				this.decpoint = '.';
				this.L = L;
				this.lookahead.token = LexState.TK_EOS; /* no look-ahead token */
				this.z = z;
				this.fs = null;
				this.linenumber = 1;
				this.lastline = 1;
				this.source = source;
				this.envn = LuaValue.ENV; /* environment variable name */
				this.nbuff = 0; /* initialize buffer */
				this.current = firstByte; /* read first char */
				this.skipShebang();
			}

	private void skipShebang() {
				if (this.current == '#')
					while (!this.currIsNewline() && this.current != LexState.EOZ)
						this.nextChar();
			}

	/*
	 * * =======================================================* LEXICAL ANALYZER* =======================================================
	 */

	boolean check_next(final String set) {
				if (set.indexOf(this.current) < 0)
					return false;
				this.save_and_next();
				return true;
			}

			void buffreplace(final char from, final char to) {
				int n = this.nbuff;
				final char[] p = this.buff;
				while ((--n) >= 0)
					if (p[n] == from)
						p[n] = to;
			}

			LuaValue strx2number(final String str, final SemInfo seminfo) {
				final char[] c = str.toCharArray();
				int s = 0;
				while (s < c.length && this.isspace(c[s]))
					++s;
				// Check for negative sign
				double sgn = 1.0;
				if (s < c.length && c[s] == '-') {
					sgn = -1.0;
					++s;
				}
				/* Check for "0x" */
				if (s + 2 >= c.length)
					return LuaValue.ZERO;
				if (c[s++] != '0')
					return LuaValue.ZERO;
				if (c[s] != 'x' && c[s] != 'X')
					return LuaValue.ZERO;
				++s;

				// read integer part.
				double m = 0;
				int e = 0;
				while (s < c.length && this.isxdigit(c[s]))
					m = (m * 16) + this.hexvalue(c[s++]);
				if (s < c.length && c[s] == '.') {
					++s; // skip dot
					while (s < c.length && this.isxdigit(c[s])) {
						m = (m * 16) + this.hexvalue(c[s++]);
						e -= 4; // Each fractional part shifts right by 2^4
					}
				}
				if (s < c.length && (c[s] == 'p' || c[s] == 'P')) {
					++s;
					int exp1 = 0;
					boolean neg1 = false;
					if (s < c.length && c[s] == '-') {
						neg1 = true;
						++s;
					}
					while (s < c.length && this.isdigit(c[s]))
						exp1 = exp1 * 10 + c[s++] - '0';
					if (neg1)
						exp1 = -exp1;
					e += exp1;
				}
				return LuaValue.valueOf(sgn * m * MathLib.dpow_d(2.0, e));
			}

	boolean str2d(final String str, final SemInfo seminfo) {
				if (str.indexOf('n') >= 0 || str.indexOf('N') >= 0)
					seminfo.r = LuaValue.ZERO;
				else if (str.indexOf('x') >= 0 || str.indexOf('X') >= 0)
					seminfo.r = this.strx2number(str, seminfo);
				else
					seminfo.r = LuaValue.valueOf(Double.parseDouble(str.trim()));
				return true;
			}

			void read_numeral(final SemInfo seminfo) {
				String expo = "Ee";
				final int first = this.current;
				LuaC._assert(this.isdigit(this.current));
				this.save_and_next();
				if (first == '0' && this.check_next("Xx"))
					expo = "Pp";
				while (true) {
					if (this.check_next(expo))
						this.check_next("+-");
					if (this.isxdigit(this.current) || this.current == '.')
						this.save_and_next();
					else
						break;
				}
				this.save('\0');
				final String str = new String(this.buff, 0, this.nbuff);
				this.str2d(str, seminfo);
			}

			int skip_sep() {
				int count = 0;
				final int s = this.current;
				LuaC._assert(s == '[' || s == ']');
				this.save_and_next();
				while (this.current == '=') {
					this.save_and_next();
					count++;
				}
				return (this.current == s) ? count : (-count) - 1;
			}

			void read_long_string(final SemInfo seminfo, final int sep) {
				int cont = 0;
				this.save_and_next(); /* skip 2nd `[' */
				if (this.currIsNewline()) /* string starts with a newline? */
					this.inclinenumber(); /* skip it */
				for (boolean endloop = false; !endloop;) {
					switch (this.current) {
					case EOZ:
						this.lexerror((seminfo != null) ? "unfinished long string" : "unfinished long comment", LexState.TK_EOS);
						break; /* to avoid warnings */
					case '[': {
						if (this.skip_sep() == sep) {
							this.save_and_next(); /* skip 2nd `[' */
							cont++;
							if (LexState.LUA_COMPAT_LSTR == 1) {
								if (sep == 0)
									this.lexerror("nesting of [[...]] is deprecated", '[');
							}
						}
						break;
					}
					case ']': {
						if (this.skip_sep() == sep) {
							this.save_and_next(); /* skip 2nd `]' */
							if (LexState.LUA_COMPAT_LSTR == 2) {
								cont--;
								if (sep == 0 && cont >= 0)
									break;
							}
							endloop = true;
						}
						break;
					}
					case '\n':
					case '\r': {
						this.save('\n');
						this.inclinenumber();
						if (seminfo == null)
							this.nbuff = 0; /* avoid wasting space */
						break;
					}
					default: {
						if (seminfo != null)
							this.save_and_next();
						else
							this.nextChar();
					}
					}
				}
				if (seminfo != null)
					seminfo.ts = this.L.newTString(LuaString.valueOf(this.buff, 2 + sep, this.nbuff - 2 * (2 + sep)));
			}

			int hexvalue(final int c) {
				return c <= '9' ? c - '0' : c <= 'F' ? c + 10 - 'A' : c + 10 - 'a';
			}

			int readhexaesc() {
				this.nextChar();
				final int c1 = this.current;
				this.nextChar();
				final int c2 = this.current;
				if (!this.isxdigit(c1) || !this.isxdigit(c2))
					this.lexerror("hexadecimal digit expected 'x" + ((char) c1) + ((char) c2), LexState.TK_STRING);
				return (this.hexvalue(c1) << 4) + this.hexvalue(c2);
			}

			void read_string(final int del, final SemInfo seminfo) {
				this.save_and_next();
				while (this.current != del) {
					switch (this.current) {
					case EOZ:
						this.lexerror("unfinished string", LexState.TK_EOS);
						continue; /* to avoid warnings */
					case '\n':
					case '\r':
						this.lexerror("unfinished string", LexState.TK_STRING);
						continue; /* to avoid warnings */
					case '\\': {
						int c;
						this.nextChar(); /* do not save the `\' */
						switch (this.current) {
						case 'a': /* bell */
							c = '\u0007';
							break;
						case 'b': /* backspace */
							c = '\b';
							break;
						case 'f': /* form feed */
							c = '\f';
							break;
						case 'n': /* newline */
							c = '\n';
							break;
						case 'r': /* carriage return */
							c = '\r';
							break;
						case 't': /* tab */
							c = '\t';
							break;
						case 'v': /* vertical tab */
							c = '\u000B';
							break;
						case 'x':
							c = this.readhexaesc();
							break;
						case '\n': /* go through */
						case '\r':
							this.save('\n');
							this.inclinenumber();
							continue;
						case EOZ:
							continue; /* will raise an error next loop */
				case 'z': { /* zap following span of spaces */
					this.nextChar(); /* skip the 'z' */
					while (this.isspace(this.current)) {
						if (this.currIsNewline())
							this.inclinenumber();
						else
							this.nextChar();
					}
					continue;
				}
						default: {
							if (!this.isdigit(this.current))
								this.save_and_next(); /* handles \\, \", \', and \? */
							else { /* \xxx */
								int i = 0;
								c = 0;
								do {
									c = 10 * c + (this.current - '0');
									this.nextChar();
								} while (++i < 3 && this.isdigit(this.current));
								if (c > LexState.UCHAR_MAX)
									this.lexerror("escape sequence too large", LexState.TK_STRING);
								this.save(c);
							}
							continue;
						}
						}
						this.save(c);
						this.nextChar();
						continue;
					}
					default:
						this.save_and_next();
					}
				}
				this.save_and_next(); /* skip delimiter */
				seminfo.ts = this.L.newTString(LuaString.valueOf(this.buff, 1, this.nbuff - 2));
			}

			int llex(final SemInfo seminfo) {
				this.nbuff = 0;
				while (true) {
					switch (this.current) {
					case '\n':
					case '\r': {
						this.inclinenumber();
						continue;
					}
					case '-': {
						this.nextChar();
						if (this.current != '-')
							return '-';
						/* else is a comment */
						this.nextChar();
						if (this.current == '[') {
							final int sep = this.skip_sep();
							this.nbuff = 0; /* `skip_sep' may dirty the buffer */
							if (sep >= 0) {
								this.read_long_string(null, sep); /* long comment */
								this.nbuff = 0;
								continue;
							}
						}
						/* else short comment */
						while (!this.currIsNewline() && this.current != LexState.EOZ)
							this.nextChar();
						continue;
					}
					case '[': {
						final int sep = this.skip_sep();
						if (sep >= 0) {
							this.read_long_string(seminfo, sep);
							return LexState.TK_STRING;
						} else if (sep == -1)
							return '[';
						else
							this.lexerror("invalid long string delimiter", LexState.TK_STRING);
					}
					case '=': {
						this.nextChar();
						if (this.current != '=')
							return '=';
						else {
							this.nextChar();
							return LexState.TK_EQ;
						}
					}
					case '<': {
						this.nextChar();
						if (this.current != '=')
							return '<';
						else {
							this.nextChar();
							return LexState.TK_LE;
						}
					}
					case '>': {
						this.nextChar();
						if (this.current != '=')
							return '>';
						else {
							this.nextChar();
							return LexState.TK_GE;
						}
					}
					case '~': {
						this.nextChar();
						if (this.current != '=')
							return '~';
						else {
							this.nextChar();
							return LexState.TK_NE;
						}
					}
					case ':': {
						this.nextChar();
						if (this.current != ':')
							return ':';
						else {
							this.nextChar();
							return LexState.TK_DBCOLON;
						}
					}
					case '"':
					case '\'': {
						this.read_string(this.current, seminfo);
						return LexState.TK_STRING;
					}
					case '.': {
						this.save_and_next();
						if (this.check_next(".")) {
							if (this.check_next("."))
								return LexState.TK_DOTS; /* ... */
							else
								return LexState.TK_CONCAT; /* .. */
						} else if (!this.isdigit(this.current))
							return '.';
						else {
							this.read_numeral(seminfo);
							return LexState.TK_NUMBER;
						}
					}
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9': {
				this.read_numeral(seminfo);
				return LexState.TK_NUMBER;
			}
			case EOZ: {
						return LexState.TK_EOS;
					}
					default: {
						if (this.isspace(this.current)) {
							LuaC._assert(!this.currIsNewline());
							this.nextChar();
							continue;
						} else if (this.isdigit(this.current)) {
							this.read_numeral(seminfo);
							return LexState.TK_NUMBER;
						} else if (this.isalpha(this.current) || this.current == '_') {
							/* identifier or reserved word */
							LuaString ts;
							do {
								this.save_and_next();
							} while (this.isalnum(this.current) || this.current == '_');
							ts = this.newstring(this.buff, 0, this.nbuff);
							if (LexState.RESERVED.containsKey(ts))
								return ((Integer) LexState.RESERVED.get(ts)).intValue();
							else {
								seminfo.ts = ts;
								return LexState.TK_NAME;
							}
						} else {
							final int c = this.current;
							this.nextChar();
							return c; /* single-char tokens (+ - / ...) */
						}
					}
					}
				}
			}

			void next() {
				this.lastline = this.linenumber;
				if (this.lookahead.token != LexState.TK_EOS) { /* is there a look-ahead token? */
					this.t.set(this.lookahead); /* use this one */
					this.lookahead.token = LexState.TK_EOS; /* and discharge it */
				} else
					this.t.token = this.llex(this.t.seminfo); /* read next token */
			}

			void lookahead() {
				LuaC._assert(this.lookahead.token == LexState.TK_EOS);
				this.lookahead.token = this.llex(this.lookahead.seminfo);
			}

			// =============================================================
			// from lcode.h
			// =============================================================

	// =============================================================
			// from lparser.c
			// =============================================================

			static final boolean vkisvar(final int k) {
				return (LexState.VLOCAL <= (k) && (k) <= LexState.VINDEXED);
			}

			static final boolean vkisinreg(final int k) {
				return ((k) == LexState.VNONRELOC || (k) == LexState.VLOCAL);
			}

			static class expdesc {
				int	k;	// expkind, from enumerated list, above

		static class U { // originally a union
					short				ind_idx;	// index (R/K)
					short				ind_t;		// table(register or upvalue)
					short				ind_vt;	// whether 't' is register (VLOCAL) or (UPVALUE)
					private LuaValue	_nval;
					int					info;

			public void setNval(final LuaValue r) {
						this._nval = r;
					}

			public LuaValue nval() {
						return (this._nval == null ? LuaInteger.valueOf(this.info) : this._nval);
					}
				};

		final U			u	= new U();
				final IntPtr	t	= new IntPtr(); /* patch list of `exit when true' */
				final IntPtr	f	= new IntPtr(); /* patch list of `exit when false' */

		void init(final int k, final int i) {
					this.f.i = LexState.NO_JUMP;
					this.t.i = LexState.NO_JUMP;
					this.k = k;
					this.u.info = i;
				}

				boolean hasjumps() {
					return (this.t.i != this.f.i);
				}

				boolean isnumeral() {
					return (this.k == LexState.VKNUM && this.t.i == LexState.NO_JUMP && this.f.i == LexState.NO_JUMP);
				}

				public void setvalue(final expdesc other) {
					this.f.i = other.f.i;
					this.k = other.k;
					this.t.i = other.t.i;
					this.u._nval = other.u._nval;
					this.u.ind_idx = other.u.ind_idx;
					this.u.ind_t = other.u.ind_t;
					this.u.ind_vt = other.u.ind_vt;
					this.u.info = other.u.info;
				}
			}

	/* description of active local variable */
			static class Vardesc {
				final short	idx;	/* variable index in stack */

		Vardesc(final int idx) {
					this.idx = (short) idx;
				}
			};

	/* description of pending goto statements and label statements */
			static class Labeldesc {
				LuaString	name;		/* label identifier */
				int			pc;		/* position in code */
				int			line;		/* line where it appeared */
				short		nactvar;	/* local level where it appears in current block */

		public Labeldesc(final LuaString name, final int pc, final int line, final short nactvar) {
					this.name = name;
					this.pc = pc;
					this.line = line;
					this.nactvar = nactvar;
				}
			};

	/* dynamic structures used by the parser */
			static class Dyndata {
				Vardesc[]	actvar;			/* list of active local variables */
		int			n_actvar	= 0;
				Labeldesc[]	gt;				/* list of pending gotos */
				int			n_gt		= 0;
				Labeldesc[]	label;				/* list of active labels */
				int			n_label		= 0;
			};

	boolean hasmultret(final int k) {
				return ((k) == LexState.VCALL || (k) == LexState.VVARARG);
			}

			/*----------------------------------------------------------------------
	name		args	description
	------------------------------------------------------------------------*/

	void anchor_token() {
				/* last token from outer function must be EOS */
				LuaC._assert(this.fs != null || this.t.token == LexState.TK_EOS);
				if (this.t.token == LexState.TK_NAME || this.t.token == LexState.TK_STRING) {
					final LuaString ts = this.t.seminfo.ts;
					// TODO: is this necessary?
					this.L.cachedLuaString(this.t.seminfo.ts);
				}
			}

			/* semantic error */
			void semerror(final String msg) {
				this.t.token = 0; /* remove 'near to' from final message */
				this.syntaxerror(msg);
			}

			void error_expected(final int token) {
				this.syntaxerror(this.L.pushfstring(LexState.LUA_QS(this.token2str(token)) + " expected"));
			}

			boolean testnext(final int c) {
				if (this.t.token == c) {
					this.next();
					return true;
				} else
					return false;
			}

			void check(final int c) {
				if (this.t.token != c)
					this.error_expected(c);
			}

			void checknext(final int c) {
		this.check(c);
		this.next();
			}

			void check_condition(final boolean c, final String msg) {
				if (!(c))
					this.syntaxerror(msg);
			}

	void check_match(final int what, final int who, final int where) {
				if (!this.testnext(what)) {
					if (where == this.linenumber)
						this.error_expected(what);
					else {
						this.syntaxerror(this.L.pushfstring(LexState.LUA_QS(this.token2str(what)) + " expected " + "(to close " + LexState.LUA_QS(this.token2str(who)) + " at line " + where + ")"));
					}
				}
			}

			LuaString str_checkname() {
				LuaString ts;
				this.check(LexState.TK_NAME);
				ts = this.t.seminfo.ts;
				this.next();
				return ts;
			}

	void codestring(final expdesc e, final LuaString s) {
				e.init(LexState.VK, this.fs.stringK(s));
			}

			void checkname(final expdesc e) {
				this.codestring(e, this.str_checkname());
			}

	int registerlocalvar(final LuaString varname) {
				final FuncState fs = this.fs;
				final Prototype f = fs.f;
				if (f.locvars == null || fs.nlocvars + 1 > f.locvars.length)
					f.locvars = LuaC.realloc(f.locvars, fs.nlocvars * 2 + 1);
				f.locvars[fs.nlocvars] = new LocVars(varname, 0, 0);
				return fs.nlocvars++;
			}

	void new_localvar(final LuaString name) {
				final int reg = this.registerlocalvar(name);
				this.fs.checklimit(this.dyd.n_actvar + 1, LuaC.LUAI_MAXVARS, "local variables");
				if (this.dyd.actvar == null || this.dyd.n_actvar + 1 > this.dyd.actvar.length)
					this.dyd.actvar = LuaC.realloc(this.dyd.actvar, Math.max(1, this.dyd.n_actvar * 2));
				this.dyd.actvar[this.dyd.n_actvar++] = new Vardesc(reg);
			}

			void new_localvarliteral(final String v) {
				final LuaString ts = this.newstring(v);
				this.new_localvar(ts);
			}

			void adjustlocalvars(int nvars) {
				final FuncState fs = this.fs;
				fs.nactvar = (short) (fs.nactvar + nvars);
				for (; nvars > 0; nvars--) {
					fs.getlocvar(fs.nactvar - nvars).startpc = fs.pc;
				}
			}

			void removevars(final int tolevel) {
				final FuncState fs = this.fs;
				while (fs.nactvar > tolevel)
					fs.getlocvar(--fs.nactvar).endpc = fs.pc;
			}

	void singlevar(final expdesc var) {
				final LuaString varname = this.str_checkname();
				final FuncState fs = this.fs;
				if (FuncState.singlevaraux(fs, varname, var, 1) == LexState.VVOID) { /* global name? */
					final expdesc key = new expdesc();
			FuncState.singlevaraux(fs, this.envn, var, 1); /* get environment variable */
			LuaC._assert(var.k == LexState.VLOCAL || var.k == LexState.VUPVAL);
			this.codestring(key, varname); /* key is variable name */
			fs.indexed(var, key); /* env[varname] */
				}
			}

	void adjust_assign(final int nvars, final int nexps, final expdesc e) {
				final FuncState fs = this.fs;
				int extra = nvars - nexps;
				if (this.hasmultret(e.k)) {
					/* includes call itself */
					extra++;
					if (extra < 0)
						extra = 0;
					/* last exp. provides the difference */
					fs.setreturns(e, extra);
					if (extra > 1)
						fs.reserveregs(extra - 1);
				} else {
					/* close last expression */
					if (e.k != LexState.VVOID)
						fs.exp2nextreg(e);
					if (extra > 0) {
						final int reg = fs.freereg;
						fs.reserveregs(extra);
						fs.nil(reg, extra);
					}
				}
			}

	void enterlevel() {
				if (++this.L.nCcalls > LexState.LUAI_MAXCCALLS)
					this.lexerror("chunk has too many syntax levels", 0);
			}

	void leavelevel() {
				this.L.nCcalls--;
			}

			void closegoto(final int g, final Labeldesc label) {
				final FuncState fs = this.fs;
				final Labeldesc[] gl = this.dyd.gt;
				final Labeldesc gt = gl[g];
				LuaC._assert(gt.name.eq_b(label.name));
				if (gt.nactvar < label.nactvar) {
					final LuaString vname = fs.getlocvar(gt.nactvar).varname;
					final String msg = this.L.pushfstring("<goto " + gt.name + "> at line " + gt.line + " jumps into the scope of local '" + vname.tojstring() + "'");
					this.semerror(msg);
				}
				fs.patchlist(gt.pc, label.pc);
				/* remove goto from pending list */
				System.arraycopy(gl, g + 1, gl, g, this.dyd.n_gt - g - 1);
				gl[--this.dyd.n_gt] = null;
			}

			/*
	 * * try to close a goto with existing labels; this solves backward jumps
	 */
			boolean findlabel(final int g) {
				int i;
				final BlockCnt bl = this.fs.bl;
				final Dyndata dyd = this.dyd;
				final Labeldesc gt = dyd.gt[g];
				/* check labels in current block for a match */
				for (i = bl.firstlabel; i < dyd.n_label; i++) {
					final Labeldesc lb = dyd.label[i];
					if (lb.name.eq_b(gt.name)) { /* correct label? */
						if (gt.nactvar > lb.nactvar && (bl.upval || dyd.n_label > bl.firstlabel))
							this.fs.patchclose(gt.pc, lb.nactvar);
						this.closegoto(g, lb); /* close it */
						return true;
					}
				}
				return false; /* label not found; cannot close goto */
			}

			/* Caller must LuaC.grow() the vector before calling this. */
			int newlabelentry(final Labeldesc[] l, final int index, final LuaString name, final int line, final int pc) {
				l[index] = new Labeldesc(name, pc, line, this.fs.nactvar);
				return index;
			}

			/*
	 * * check whether new label 'lb' matches any pending gotos in current* block; solves forward jumps
	 */
			void findgotos(final Labeldesc lb) {
				final Labeldesc[] gl = this.dyd.gt;
				int i = this.fs.bl.firstgoto;
				while (i < this.dyd.n_gt) {
					if (gl[i].name.eq_b(lb.name))
						this.closegoto(i, lb);
					else
						i++;
				}
			}

	/*
	 * * create a label named "break" to resolve break statements
	 */
			void breaklabel() {
				final LuaString n = LuaString.valueOf("break");
				final int l = this.newlabelentry(this.dyd.label = LuaC.grow(this.dyd.label, this.dyd.n_label + 1), this.dyd.n_label++, n, 0, this.fs.pc);
				this.findgotos(this.dyd.label[l]);
			}

			/*
	 * * generates an error for an undefined 'goto'; choose appropriate* message when label name is a reserved word (which can only be 'break')
	 */
			void undefgoto(final Labeldesc gt) {
		final String msg = this.L.pushfstring(LexState.isReservedKeyword(gt.name.tojstring()) ? "<" + gt.name + "> at line " + gt.line + " not inside a loop" : "no visible label '" + gt.name + "' for <goto> at line " + gt.line);
		this.semerror(msg);
			}

			Prototype addprototype() {
		Prototype clp;
		final Prototype f = this.fs.f; /* prototype of current function */
		if (f.p == null || this.fs.np >= f.p.length) {
			f.p = LuaC.realloc(f.p, Math.max(1, this.fs.np * 2));
		}
		f.p[this.fs.np++] = clp = new Prototype();
		return clp;
			}

			void codeclosure(final expdesc v) {
		final FuncState fs = this.fs.prev;
		v.init(LexState.VRELOCABLE, fs.codeABx(Lua.OP_CLOSURE, 0, fs.np - 1));
		fs.exp2nextreg(v); /* fix it at stack top (for GC) */
			}

			void open_func(final FuncState fs, final BlockCnt bl) {
		fs.prev = this.fs; /* linked list of funcstates */
		fs.ls = this;
		this.fs = fs;
		fs.pc = 0;
		fs.lasttarget = -1;
		fs.jpc = new IntPtr(LexState.NO_JUMP);
		fs.freereg = 0;
		fs.nk = 0;
		fs.np = 0;
		fs.nups = 0;
		fs.nlocvars = 0;
		fs.nactvar = 0;
		fs.firstlocal = this.dyd.n_actvar;
		fs.bl = null;
		fs.f.source = this.source;
		fs.f.maxstacksize = 2; /* registers 0/1 are always valid */
		fs.enterblock(bl, false);
			}

			void close_func() {
				final FuncState fs = this.fs;
				final Prototype f = fs.f;
				fs.ret(0, 0); /* final return */
				fs.leaveblock();
				f.code = LuaC.realloc(f.code, fs.pc);
				f.lineinfo = LuaC.realloc(f.lineinfo, fs.pc);
				f.k = LuaC.realloc(f.k, fs.nk);
				f.p = LuaC.realloc(f.p, fs.np);
				f.locvars = LuaC.realloc(f.locvars, fs.nlocvars);
				f.upvalues = LuaC.realloc(f.upvalues, fs.nups);
				LuaC._assert(fs.bl == null);
				this.fs = fs.prev;
				// last token read was anchored in defunct function; must reanchor it
				// ls.anchor_token();
			}

			/* ============================================================ */
			/* GRAMMAR RULES */
			/* ============================================================ */

			void fieldsel(final expdesc v) {
				/* fieldsel -> ['.' | ':'] NAME */
				final FuncState fs = this.fs;
				final expdesc key = new expdesc();
				fs.exp2anyregup(v);
				this.next(); /* skip the dot or colon */
				this.checkname(key);
				fs.indexed(v, key);
			}

	void yindex(final expdesc v) {
				/* index -> '[' expr ']' */
				this.next(); /* skip the '[' */
				this.expr(v);
				this.fs.exp2val(v);
				this.checknext(']');
			}

	/*
	 * * {======================================================================* Rules for Constructors* =======================================================================
	 */

	static class ConsControl {
				expdesc	v	= new expdesc();	/* last list item read */
				expdesc	t;						/* table descriptor */
				int		nh;					/* total number of `record' elements */
				int		na;					/* total number of array elements */
				int		tostore;				/* number of array elements pending to be stored */
			};

	void recfield(final ConsControl cc) {
				/* recfield -> (NAME | `['exp1`]') = exp1 */
				final FuncState fs = this.fs;
				final int reg = this.fs.freereg;
				final expdesc key = new expdesc();
				final expdesc val = new expdesc();
				int rkkey;
				if (this.t.token == LexState.TK_NAME) {
					fs.checklimit(cc.nh, LexState.MAX_INT, "items in a constructor");
					this.checkname(key);
				} else
					/* this.t.token == '[' */
					this.yindex(key);
				cc.nh++;
				this.checknext('=');
				rkkey = fs.exp2RK(key);
				this.expr(val);
				fs.codeABC(Lua.OP_SETTABLE, cc.t.u.info, rkkey, fs.exp2RK(val));
				fs.freereg = (short) reg; /* free registers */
			}

			void listfield(final ConsControl cc) {
		this.expr(cc.v);
		this.fs.checklimit(cc.na, LexState.MAX_INT, "items in a constructor");
		cc.na++;
		cc.tostore++;
			}

	void constructor(final expdesc t) {
				/* constructor -> ?? */
				final FuncState fs = this.fs;
				final int line = this.linenumber;
				final int pc = fs.codeABC(Lua.OP_NEWTABLE, 0, 0, 0);
				final ConsControl cc = new ConsControl();
				cc.na = cc.nh = cc.tostore = 0;
				cc.t = t;
				t.init(LexState.VRELOCABLE, pc);
				cc.v.init(LexState.VVOID, 0); /* no value (yet) */
				fs.exp2nextreg(t); /* fix it at stack top (for gc) */
				this.checknext('{');
				do {
					LuaC._assert(cc.v.k == LexState.VVOID || cc.tostore > 0);
					if (this.t.token == '}')
						break;
					fs.closelistfield(cc);
					switch (this.t.token) {
					case TK_NAME: { /* may be listfields or recfields */
						this.lookahead();
						if (this.lookahead.token != '=') /* expression? */
							this.listfield(cc);
						else
							this.recfield(cc);
						break;
					}
					case '[': { /* constructor_item -> recfield */
						this.recfield(cc);
						break;
					}
					default: { /* constructor_part -> listfield */
						this.listfield(cc);
						break;
					}
					}
				} while (this.testnext(',') || this.testnext(';'));
				this.check_match('}', '{', line);
				fs.lastlistfield(cc);
				final InstructionPtr i = new InstructionPtr(fs.f.code, pc);
				LuaC.SETARG_B(i, LexState.luaO_int2fb(cc.na)); /* set initial array size */
				LuaC.SETARG_C(i, LexState.luaO_int2fb(cc.nh)); /* set initial table size */
			}

	/*
	 * * converts an integer to a "floating point byte", represented as* (eeeeexxx), where the real value is (1xxx) * 2^(eeeee - 1) if* eeeee != 0 and (xxx) otherwise.
	 */
			static int luaO_int2fb(int x) {
		int e = 0; /* expoent */
		while (x >= 16) {
			x = (x + 1) >> 1;
			e++;
		}
		if (x < 8)
			return x;
		else
			return ((e + 1) << 3) | ((x) - 8);
			}

	/* }====================================================================== */

			void parlist() {
		/* parlist -> [ param { `,' param } ] */
		final FuncState fs = this.fs;
		final Prototype f = fs.f;
		int nparams = 0;
		f.is_vararg = 0;
		if (this.t.token != ')') { /* is `parlist' not empty? */
			do {
				switch (this.t.token) {
				case TK_NAME: { /* param . NAME */
					this.new_localvar(this.str_checkname());
					++nparams;
					break;
				}
				case TK_DOTS: { /* param . `...' */
					this.next();
					f.is_vararg = 1;
					break;
				}
				default:
					this.syntaxerror("<name> or " + LexState.LUA_QL("...") + " expected");
				}
			} while ((f.is_vararg == 0) && this.testnext(','));
		}
		this.adjustlocalvars(nparams);
		f.numparams = fs.nactvar;
		fs.reserveregs(fs.nactvar); /* reserve register for parameters */
			}

	void body(final expdesc e, final boolean needself, final int line) {
				/* body -> `(' parlist `)' chunk END */
				final FuncState new_fs = new FuncState();
				final BlockCnt bl = new BlockCnt();
				new_fs.f = this.addprototype();
				new_fs.f.linedefined = line;
				this.open_func(new_fs, bl);
				this.checknext('(');
				if (needself) {
					this.new_localvarliteral("self");
					this.adjustlocalvars(1);
				}
				this.parlist();
				this.checknext(')');
				this.statlist();
				new_fs.f.lastlinedefined = this.linenumber;
				this.check_match(LexState.TK_END, LexState.TK_FUNCTION, line);
				this.codeclosure(e);
				this.close_func();
			}

	int explist(final expdesc v) {
				/* explist1 -> expr { `,' expr } */
				int n = 1; /* at least one expression */
				this.expr(v);
				while (this.testnext(',')) {
					this.fs.exp2nextreg(v);
					this.expr(v);
					n++;
				}
				return n;
			}

	void funcargs(final expdesc f, final int line) {
				final FuncState fs = this.fs;
				final expdesc args = new expdesc();
				int base, nparams;
				switch (this.t.token) {
				case '(': { /* funcargs -> `(' [ explist1 ] `)' */
					this.next();
					if (this.t.token == ')') /* arg list is empty? */
						args.k = LexState.VVOID;
					else {
						this.explist(args);
						fs.setmultret(args);
					}
					this.check_match(')', '(', line);
					break;
				}
				case '{': { /* funcargs -> constructor */
					this.constructor(args);
					break;
				}
				case TK_STRING: { /* funcargs -> STRING */
					this.codestring(args, this.t.seminfo.ts);
					this.next(); /* must use `seminfo' before `next' */
					break;
				}
				default: {
					this.syntaxerror("function arguments expected");
					return;
				}
				}
				LuaC._assert(f.k == LexState.VNONRELOC);
				base = f.u.info; /* base register for call */
				if (this.hasmultret(args.k))
					nparams = Lua.LUA_MULTRET; /* open call */
				else {
					if (args.k != LexState.VVOID)
						fs.exp2nextreg(args); /* close last argument */
					nparams = fs.freereg - (base + 1);
				}
				f.init(LexState.VCALL, fs.codeABC(Lua.OP_CALL, base, nparams + 1, 2));
				fs.fixline(line);
				fs.freereg = (short) (base + 1); /*
										 * call remove function and arguments and leaves (unless changed) one result
										 */
			}

	/*
	 * * {======================================================================* Expression parsing* =======================================================================
	 */

			void primaryexp(final expdesc v) {
				/* primaryexp -> NAME | '(' expr ')' */
				switch (this.t.token) {
				case '(': {
					final int line = this.linenumber;
					this.next();
					this.expr(v);
					this.check_match(')', '(', line);
					this.fs.dischargevars(v);
					return;
				}
				case TK_NAME: {
					this.singlevar(v);
					return;
				}
				default: {
					this.syntaxerror("unexpected symbol " + this.t.token + " (" + ((char) this.t.token) + ")");
					return;
				}
				}
			}

	void suffixedexp(final expdesc v) {
				/*
		 * suffixedexp -> primaryexp { '.' NAME | '[' exp ']' | ':' NAME funcargs | funcargs }
		 */
				final int line = this.linenumber;
				this.primaryexp(v);
				for (;;) {
					switch (this.t.token) {
					case '.': { /* fieldsel */
						this.fieldsel(v);
						break;
					}
					case '[': { /* `[' exp1 `]' */
						final expdesc key = new expdesc();
						this.fs.exp2anyregup(v);
						this.yindex(key);
						this.fs.indexed(v, key);
						break;
					}
					case ':': { /* `:' NAME funcargs */
						final expdesc key = new expdesc();
						this.next();
						this.checkname(key);
						this.fs.self(v, key);
						this.funcargs(v, line);
						break;
					}
					case '(':
					case TK_STRING:
					case '{': { /* funcargs */
						this.fs.exp2nextreg(v);
						this.funcargs(v, line);
						break;
					}
					default:
						return;
					}
				}
	}

	void simpleexp(final expdesc v) {
				/*
		 * simpleexp -> NUMBER | STRING | NIL | true | false | ... | constructor | FUNCTION body | primaryexp
		 */
				switch (this.t.token) {
				case TK_NUMBER: {
					v.init(LexState.VKNUM, 0);
					v.u.setNval(this.t.seminfo.r);
					break;
				}
				case TK_STRING: {
					this.codestring(v, this.t.seminfo.ts);
					break;
				}
				case TK_NIL: {
					v.init(LexState.VNIL, 0);
					break;
				}
				case TK_TRUE: {
					v.init(LexState.VTRUE, 0);
					break;
				}
				case TK_FALSE: {
					v.init(LexState.VFALSE, 0);
					break;
				}
				case TK_DOTS: { /* vararg */
					final FuncState fs = this.fs;
					this.check_condition(fs.f.is_vararg != 0, "cannot use " + LexState.LUA_QL("...") + " outside a vararg function");
					v.init(LexState.VVARARG, fs.codeABC(Lua.OP_VARARG, 0, 1, 0));
					break;
				}
				case '{': { /* constructor */
					this.constructor(v);
					return;
				}
				case TK_FUNCTION: {
					this.next();
					this.body(v, false, this.linenumber);
					return;
				}
				default: {
					this.suffixedexp(v);
					return;
				}
				}
				this.next();
			}

	int getunopr(final int op) {
				switch (op) {
				case TK_NOT:
					return LexState.OPR_NOT;
				case '-':
					return LexState.OPR_MINUS;
				case '#':
					return LexState.OPR_LEN;
				default:
					return LexState.OPR_NOUNOPR;
				}
			}

	int getbinopr(final int op) {
				switch (op) {
				case '+':
					return LexState.OPR_ADD;
				case '-':
					return LexState.OPR_SUB;
				case '*':
					return LexState.OPR_MUL;
				case '/':
					return LexState.OPR_DIV;
				case '%':
					return LexState.OPR_MOD;
				case '^':
					return LexState.OPR_POW;
				case TK_CONCAT:
					return LexState.OPR_CONCAT;
				case TK_NE:
					return LexState.OPR_NE;
				case TK_EQ:
					return LexState.OPR_EQ;
				case '<':
					return LexState.OPR_LT;
				case TK_LE:
					return LexState.OPR_LE;
				case '>':
					return LexState.OPR_GT;
				case TK_GE:
					return LexState.OPR_GE;
				case TK_AND:
					return LexState.OPR_AND;
				case TK_OR:
					return LexState.OPR_OR;
				default:
					return LexState.OPR_NOBINOPR;
				}
			}

			static class Priority {
				final byte	left;	/* left priority for each binary operator */

				final byte	right;	/* right priority */

				public Priority(final int i, final int j) {
					this.left = (byte) i;
					this.right = (byte) j;
				}
			};

	static Priority[]	priority		= { /* ORDER OPR */
										new Priority(6, 6), new Priority(6, 6), new Priority(7, 7), new Priority(7, 7), new Priority(7, 7), /* `+' `-' `/' `%' */
										new Priority(10, 9), new Priority(5, 4), /* power and concat (right associative) */
										new Priority(3, 3), new Priority(3, 3), /* equality and inequality */
										new Priority(3, 3), new Priority(3, 3), new Priority(3, 3), new Priority(3, 3), /* order */
										new Priority(2, 2), new Priority(1, 1) /* logical (and/or) */
										};

			static final int	UNARY_PRIORITY	= 8;	/* priority for unary operators */

	/*
	 * * subexpr -> (simpleexp | unop subexpr) { binop subexpr }* where `binop' is any binary operator with a priority higher than `limit'
	 */
			int subexpr(final expdesc v, final int limit) {
				int op;
				int uop;
				this.enterlevel();
				uop = this.getunopr(this.t.token);
				if (uop != LexState.OPR_NOUNOPR) {
			final int line = this.linenumber;
					this.next();
					this.subexpr(v, LexState.UNARY_PRIORITY);
					this.fs.prefix(uop, v, line);
				} else
					this.simpleexp(v);
				/* expand while operators have priorities higher than `limit' */
				op = this.getbinopr(this.t.token);
				while (op != LexState.OPR_NOBINOPR && LexState.priority[op].left > limit) {
					final expdesc v2 = new expdesc();
					final int line = this.linenumber;
					this.next();
					this.fs.infix(op, v);
					/* read sub-expression with higher priority */
					final int nextop = this.subexpr(v2, LexState.priority[op].right);
					this.fs.posfix(op, v, v2, line);
					op = nextop;
				}
				this.leavelevel();
				return op; /* return first untreated operator */
			}

			void expr(final expdesc v) {
				this.subexpr(v, 0);
			}

			/* }==================================================================== */

	/*
	 * * {======================================================================* Rules for Statements* =======================================================================
	 */

	boolean block_follow(final boolean withuntil) {
				switch (this.t.token) {
		case TK_ELSE:
		case TK_ELSEIF:
		case TK_END:
		case TK_EOS:
			return true;
		case TK_UNTIL:
			return withuntil;
		default:
			return false;
				}
			}

	void block() {
		/* block -> chunk */
		final FuncState fs = this.fs;
		final BlockCnt bl = new BlockCnt();
		fs.enterblock(bl, false);
		this.statlist();
		fs.leaveblock();
			}

	/*
	 * * structure to chain all variables in the left-hand side of an* assignment
	 */
			static class LHS_assign {
				LHS_assign	prev;
				/* variable (global, local, upvalue, or indexed) */
				expdesc		v	= new expdesc();
	};

	/*
	 * * check whether, in an assignment to a local variable, the local variable* is needed in a previous assignment (to a table). If so, save original* local value in a safe place and use this safe copy in the previous* assignment.
	 */
			void check_conflict(LHS_assign lh, final expdesc v) {
				final FuncState fs = this.fs;
				final short extra = fs.freereg; /* eventual position to save local variable */
				boolean conflict = false;
				for (; lh != null; lh = lh.prev) {
					if (lh.v.k == LexState.VINDEXED) {
						/* table is the upvalue/local being assigned now? */
						if (lh.v.u.ind_vt == v.k && lh.v.u.ind_t == v.u.info) {
							conflict = true;
							lh.v.u.ind_vt = LexState.VLOCAL;
							lh.v.u.ind_t = extra; /* previous assignment will use safe copy */
						}
						/* index is the local being assigned? (index cannot be upvalue) */
						if (v.k == LexState.VLOCAL && lh.v.u.ind_idx == v.u.info) {
							conflict = true;
							lh.v.u.ind_idx = extra; /* previous assignment will use safe copy */
						}
					}
				}
				if (conflict) {
			/* copy upvalue/local value to a temporary (in position 'extra') */
			final int op = (v.k == LexState.VLOCAL) ? Lua.OP_MOVE : Lua.OP_GETUPVAL;
			fs.codeABC(op, extra, v.u.info, 0);
			fs.reserveregs(1);
				}
			}

	void assignment(final LHS_assign lh, final int nvars) {
				final expdesc e = new expdesc();
				this.check_condition(LexState.VLOCAL <= lh.v.k && lh.v.k <= LexState.VINDEXED, "syntax error");
				if (this.testnext(',')) { /* assignment -> `,' primaryexp assignment */
			final LHS_assign nv = new LHS_assign();
			nv.prev = lh;
			this.suffixedexp(nv.v);
			if (nv.v.k != LexState.VINDEXED)
				this.check_conflict(lh, nv.v);
			this.assignment(nv, nvars + 1);
				} else { /* assignment . `=' explist1 */
			int nexps;
			this.checknext('=');
			nexps = this.explist(e);
			if (nexps != nvars) {
				this.adjust_assign(nvars, nexps, e);
				if (nexps > nvars)
					this.fs.freereg -= nexps - nvars; /* remove extra values */
			} else {
				this.fs.setoneret(e); /* close last expression */
				this.fs.storevar(lh.v, e);
				return; /* avoid default */
			}
		}
		e.init(LexState.VNONRELOC, this.fs.freereg - 1); /* default assignment */
		this.fs.storevar(lh.v, e);
			}

	int cond() {
				/* cond -> exp */
				final expdesc v = new expdesc();
				/* read condition */
				this.expr(v);
				/* `falses' are all equal here */
				if (v.k == LexState.VNIL)
					v.k = LexState.VFALSE;
				this.fs.goiftrue(v);
				return v.f.i;
			}

			void gotostat(final int pc) {
				final int line = this.linenumber;
				LuaString label;
				int g;
				if (this.testnext(LexState.TK_GOTO))
					label = this.str_checkname();
				else {
					this.next(); /* skip break */
					label = LuaString.valueOf("break");
				}
				g = this.newlabelentry(this.dyd.gt = LuaC.grow(this.dyd.gt, this.dyd.n_gt + 1), this.dyd.n_gt++, label, line, pc);
				this.findlabel(g); /* close it if label already defined */
			}

	/* skip no-op statements */
			void skipnoopstat() {
				while (this.t.token == ';' || this.t.token == LexState.TK_DBCOLON)
					this.statement();
			}

	void labelstat(final LuaString label, final int line) {
				/* label -> '::' NAME '::' */
				int l; /* index of new label being created */
				this.fs.checkrepeated(this.dyd.label, this.dyd.n_label, label); /* check for repeated labels */
				this.checknext(LexState.TK_DBCOLON); /* skip double colon */
				/* create new entry for this label */
				l = this.newlabelentry(this.dyd.label = LuaC.grow(this.dyd.label, this.dyd.n_label + 1), this.dyd.n_label++, label, line, this.fs.pc);
				this.skipnoopstat(); /* skip other no-op statements */
				if (this.block_follow(false)) { /* label is last no-op statement in the block? */
					/* assume that locals are already out of scope */
					this.dyd.label[l].nactvar = this.fs.bl.nactvar;
				}
				this.findgotos(this.dyd.label[l]);
	}

	void whilestat(final int line) {
				/* whilestat -> WHILE cond DO block END */
				final FuncState fs = this.fs;
				int whileinit;
				int condexit;
				final BlockCnt bl = new BlockCnt();
				this.next(); /* skip WHILE */
				whileinit = fs.getlabel();
				condexit = this.cond();
				fs.enterblock(bl, true);
				this.checknext(LexState.TK_DO);
				this.block();
				fs.patchlist(fs.jump(), whileinit);
				this.check_match(LexState.TK_END, LexState.TK_WHILE, line);
				fs.leaveblock();
				fs.patchtohere(condexit); /* false conditions finish the loop */
			}

			void repeatstat(final int line) {
				/* repeatstat -> REPEAT block UNTIL cond */
				int condexit;
				final FuncState fs = this.fs;
				final int repeat_init = fs.getlabel();
				final BlockCnt bl1 = new BlockCnt();
				final BlockCnt bl2 = new BlockCnt();
				fs.enterblock(bl1, true); /* loop block */
				fs.enterblock(bl2, false); /* scope block */
				this.next(); /* skip REPEAT */
				this.statlist();
				this.check_match(LexState.TK_UNTIL, LexState.TK_REPEAT, line);
				condexit = this.cond(); /* read condition (inside scope block) */
				if (bl2.upval) { /* upvalues? */
			fs.patchclose(condexit, bl2.nactvar);
				}
				fs.leaveblock(); /* finish scope */
				fs.patchlist(condexit, repeat_init); /* close the loop */
				fs.leaveblock(); /* finish loop */
			}

	int exp1() {
				final expdesc e = new expdesc();
				int k;
				this.expr(e);
				k = e.k;
				this.fs.exp2nextreg(e);
				return k;
			}

	void forbody(final int base, final int line, final int nvars, final boolean isnum) {
				/* forbody -> DO block */
				final BlockCnt bl = new BlockCnt();
				final FuncState fs = this.fs;
				int prep, endfor;
				this.adjustlocalvars(3); /* control variables */
				this.checknext(LexState.TK_DO);
				prep = isnum ? fs.codeAsBx(Lua.OP_FORPREP, base, LexState.NO_JUMP) : fs.jump();
				fs.enterblock(bl, false); /* scope for declared variables */
				this.adjustlocalvars(nvars);
				fs.reserveregs(nvars);
				this.block();
				fs.leaveblock(); /* end of scope for declared variables */
				fs.patchtohere(prep);
				if (isnum) /* numeric for? */
					endfor = fs.codeAsBx(Lua.OP_FORLOOP, base, LexState.NO_JUMP);
				else { /* generic for */
					fs.codeABC(Lua.OP_TFORCALL, base, 0, nvars);
					fs.fixline(line);
					endfor = fs.codeAsBx(Lua.OP_TFORLOOP, base + 2, LexState.NO_JUMP);
				}
				fs.patchlist(endfor, prep + 1);
				fs.fixline(line);
			}

	void fornum(final LuaString varname, final int line) {
				/* fornum -> NAME = exp1,exp1[,exp1] forbody */
				final FuncState fs = this.fs;
				final int base = fs.freereg;
				this.new_localvarliteral(LexState.RESERVED_LOCAL_VAR_FOR_INDEX);
				this.new_localvarliteral(LexState.RESERVED_LOCAL_VAR_FOR_LIMIT);
				this.new_localvarliteral(LexState.RESERVED_LOCAL_VAR_FOR_STEP);
				this.new_localvar(varname);
				this.checknext('=');
				this.exp1(); /* initial value */
				this.checknext(',');
				this.exp1(); /* limit */
				if (this.testnext(','))
					this.exp1(); /* optional step */
				else { /* default step = 1 */
					fs.codeABx(Lua.OP_LOADK, fs.freereg, fs.numberK(LuaInteger.valueOf(1)));
					fs.reserveregs(1);
				}
				this.forbody(base, line, 1, true);
			}

	void forlist(final LuaString indexname) {
				/* forlist -> NAME {,NAME} IN explist1 forbody */
				final FuncState fs = this.fs;
				final expdesc e = new expdesc();
				int nvars = 4; /* gen, state, control, plus at least one declared var */
				int line;
				final int base = fs.freereg;
				/* create control variables */
				this.new_localvarliteral(LexState.RESERVED_LOCAL_VAR_FOR_GENERATOR);
				this.new_localvarliteral(LexState.RESERVED_LOCAL_VAR_FOR_STATE);
				this.new_localvarliteral(LexState.RESERVED_LOCAL_VAR_FOR_CONTROL);
				/* create declared variables */
				this.new_localvar(indexname);
				while (this.testnext(',')) {
					this.new_localvar(this.str_checkname());
					++nvars;
				}
				this.checknext(LexState.TK_IN);
				line = this.linenumber;
				this.adjust_assign(3, this.explist(e), e);
				fs.checkstack(3); /* extra space to call generator */
				this.forbody(base, line, nvars - 3, false);
			}

	void forstat(final int line) {
				/* forstat -> FOR (fornum | forlist) END */
				final FuncState fs = this.fs;
				LuaString varname;
				final BlockCnt bl = new BlockCnt();
				fs.enterblock(bl, true); /* scope for loop and control variables */
				this.next(); /* skip `for' */
				varname = this.str_checkname(); /* first variable name */
				switch (this.t.token) {
				case '=':
					this.fornum(varname, line);
					break;
				case ',':
				case TK_IN:
					this.forlist(varname);
					break;
				default:
					this.syntaxerror(LexState.LUA_QL("=") + " or " + LexState.LUA_QL("in") + " expected");
				}
				this.check_match(LexState.TK_END, LexState.TK_FOR, line);
				fs.leaveblock(); /* loop scope (`break' jumps to this point) */
			}

	void test_then_block(final IntPtr escapelist) {
				/* test_then_block -> [IF | ELSEIF] cond THEN block */
				final expdesc v = new expdesc();
				final BlockCnt bl = new BlockCnt();
				int jf; /* instruction to skip 'then' code (if condition is false) */
				this.next(); /* skip IF or ELSEIF */
				this.expr(v); /* read expression */
				this.checknext(LexState.TK_THEN);
				if (this.t.token == LexState.TK_GOTO || this.t.token == LexState.TK_BREAK) {
					this.fs.goiffalse(v); /* will jump to label if condition is true */
					this.fs.enterblock(bl, false); /* must enter block before 'goto' */
					this.gotostat(v.t.i); /* handle goto/break */
					this.skipnoopstat(); /* skip other no-op statements */
					if (this.block_follow(false)) { /* 'goto' is the entire block? */
						this.fs.leaveblock();
						return; /* and that is it */
					} else
						/* must skip over 'then' part if condition is false */
						jf = this.fs.jump();
				} else { /* regular case (not goto/break) */
					this.fs.goiftrue(v); /* skip over block if condition is false */
					this.fs.enterblock(bl, false);
					jf = v.f.i;
				}
				this.statlist(); /* `then' part */
				this.fs.leaveblock();
				if (this.t.token == LexState.TK_ELSE || this.t.token == LexState.TK_ELSEIF)
					this.fs.concat(escapelist, this.fs.jump()); /* must jump over it */
				this.fs.patchtohere(jf);
			}

	void ifstat(final int line) {
				final IntPtr escapelist = new IntPtr(LexState.NO_JUMP); /* exit list for finished parts */
				this.test_then_block(escapelist); /* IF cond THEN block */
				while (this.t.token == LexState.TK_ELSEIF)
			this.test_then_block(escapelist); /* ELSEIF cond THEN block */
				if (this.testnext(LexState.TK_ELSE))
			this.block(); /* `else' part */
				this.check_match(LexState.TK_END, LexState.TK_IF, line);
				this.fs.patchtohere(escapelist.i); /* patch escape list to 'if' end */
			}

			void localfunc() {
				final expdesc b = new expdesc();
				final FuncState fs = this.fs;
				this.new_localvar(this.str_checkname());
				this.adjustlocalvars(1);
				this.body(b, false, this.linenumber);
				/* debug information will only see the variable after this point! */
				fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
			}

	void localstat() {
				/* stat -> LOCAL NAME {`,' NAME} [`=' explist1] */
				int nvars = 0;
				int nexps;
				final expdesc e = new expdesc();
				do {
					this.new_localvar(this.str_checkname());
					++nvars;
				} while (this.testnext(','));
				if (this.testnext('='))
					nexps = this.explist(e);
				else {
					e.k = LexState.VVOID;
					nexps = 0;
				}
				this.adjust_assign(nvars, nexps, e);
				this.adjustlocalvars(nvars);
			}

	boolean funcname(final expdesc v) {
				/* funcname -> NAME {field} [`:' NAME] */
				boolean ismethod = false;
				this.singlevar(v);
				while (this.t.token == '.')
					this.fieldsel(v);
				if (this.t.token == ':') {
					ismethod = true;
					this.fieldsel(v);
				}
				return ismethod;
			}

	void funcstat(final int line) {
				/* funcstat -> FUNCTION funcname body */
				boolean needself;
				final expdesc v = new expdesc();
				final expdesc b = new expdesc();
				this.next(); /* skip FUNCTION */
				needself = this.funcname(v);
				this.body(b, needself, line);
				this.fs.storevar(v, b);
				this.fs.fixline(line); /* definition `happens' in the first line */
			}

	void exprstat() {
				/* stat -> func | assignment */
				final FuncState fs = this.fs;
				final LHS_assign v = new LHS_assign();
				this.suffixedexp(v.v);
				if (this.t.token == '=' || this.t.token == ',') { /* stat -> assignment ? */
					v.prev = null;
					this.assignment(v, 1);
				} else { /* stat -> func */
					this.check_condition(v.v.k == LexState.VCALL, "syntax error");
					LuaC.SETARG_C(fs.getcodePtr(v.v), 1); /* call statement uses no results */
				}
			}

			void retstat() {
				/* stat -> RETURN explist */
				final FuncState fs = this.fs;
				final expdesc e = new expdesc();
				int first, nret; /* registers with returned values */
				if (this.block_follow(true) || this.t.token == ';')
					first = nret = 0; /* return no values */
				else {
					nret = this.explist(e); /* optional return values */
					if (this.hasmultret(e.k)) {
						fs.setmultret(e);
						if (e.k == LexState.VCALL && nret == 1) { /* tail call? */
							LuaC.SET_OPCODE(fs.getcodePtr(e), Lua.OP_TAILCALL);
							LuaC._assert(Lua.GETARG_A(fs.getcode(e)) == fs.nactvar);
						}
						first = fs.nactvar;
						nret = Lua.LUA_MULTRET; /* return all values */
					} else {
						if (nret == 1) /* only one single value? */
							first = fs.exp2anyreg(e);
						else {
							fs.exp2nextreg(e); /* values must go to the `stack' */
							first = fs.nactvar; /* return all `active' values */
							LuaC._assert(nret == fs.freereg - first);
						}
					}
				}
				fs.ret(first, nret);
				this.testnext(';'); /* skip optional semicolon */
			}

			void statement() {
				final int line = this.linenumber; /* may be needed for error messages */
				this.enterlevel();
				switch (this.t.token) {
				case ';': { /* stat -> ';' (empty statement) */
					this.next(); /* skip ';' */
					break;
				}
				case TK_IF: { /* stat -> ifstat */
					this.ifstat(line);
					break;
				}
				case TK_WHILE: { /* stat -> whilestat */
					this.whilestat(line);
					break;
				}
				case TK_DO: { /* stat -> DO block END */
					this.next(); /* skip DO */
					this.block();
					this.check_match(LexState.TK_END, LexState.TK_DO, line);
					break;
				}
				case TK_FOR: { /* stat -> forstat */
					this.forstat(line);
					break;
				}
				case TK_REPEAT: { /* stat -> repeatstat */
					this.repeatstat(line);
					break;
				}
				case TK_FUNCTION: {
					this.funcstat(line); /* stat -> funcstat */
					break;
				}
				case TK_LOCAL: { /* stat -> localstat */
					this.next(); /* skip LOCAL */
					if (this.testnext(LexState.TK_FUNCTION)) /* local function? */
						this.localfunc();
					else
						this.localstat();
					break;
				}
				case TK_DBCOLON: { /* stat -> label */
					this.next(); /* skip double colon */
					this.labelstat(this.str_checkname(), line);
					break;
				}
				case TK_RETURN: { /* stat -> retstat */
			this.next(); /* skip RETURN */
					this.retstat();
					break;
				}
				case TK_BREAK:
				case TK_GOTO: { /* stat -> breakstat */
					this.gotostat(this.fs.jump());
					break;
				}
				default: {
					this.exprstat();
					break;
				}
				}
				LuaC._assert(this.fs.f.maxstacksize >= this.fs.freereg && this.fs.freereg >= this.fs.nactvar);
				this.fs.freereg = this.fs.nactvar; /* free registers */
				this.leavelevel();
			}

			void statlist() {
				/* statlist -> { stat [`;'] } */
				while (!this.block_follow(true)) {
					if (this.t.token == LexState.TK_RETURN) {
						this.statement();
						return; /* 'return' must be last statement */
					}
					this.statement();
				}
			}

			/*
	 * * compiles the main function, which is a regular vararg function with an* upvalue named LUA_ENV
	 */
			public void mainfunc(final FuncState funcstate) {
		final BlockCnt bl = new BlockCnt();
		this.open_func(funcstate, bl);
		this.fs.f.is_vararg = 1; /* main function is always vararg */
		final expdesc v = new expdesc();
		v.init(LexState.VLOCAL, 0); /* create and... */
		this.fs.newupvalue(this.envn, v); /* ...set environment upvalue */
		this.next(); /* read first token */
		this.statlist(); /* parse main body */
		this.check(LexState.TK_EOS);
		this.close_func();
			}

	/* }====================================================================== */

}
