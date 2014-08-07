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

import java.util.Calendar;
import java.util.Date;

import org.luaj.vm2.Buffer;
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
public class RestrictedOsLib extends TwoArgFunction {
	private static final int		DATE		= 0;
	private static final int		DIFFTIME	= 1;
	private static final int		TIME		= 2;

	private static final String[]	NAMES		= { "date", "difftime", "time" };

	protected Globals				globals;

	/**
	 * Create and OsLib instance.
	 */
	public RestrictedOsLib() {
	}

	@Override
	public LuaValue call(final LuaValue modname, final LuaValue env) {
		this.globals = env.checkglobals();
		final LuaTable os = new LuaTable();
		for (int i = 0; i < RestrictedOsLib.NAMES.length; ++i)
			os.set(RestrictedOsLib.NAMES[i], new OsLibFunc(i, RestrictedOsLib.NAMES[i]));
		env.set("os", os);
		env.get("package").get("loaded").set("os", os);
		return os;
	}

	class OsLibFunc extends VarArgFunction {
		public OsLibFunc(final int opcode, final String name) {
			this.opcode = opcode;
			this.name = name;
		}

		@Override
		public Varargs invoke(final Varargs args) {
			switch (this.opcode) {
			case DATE: {
				final String s = args.optjstring(1, "%c");
				final double t = args.isnumber(2) ? args.todouble(2) : RestrictedOsLib.this.time(null);
				if (s.equals("*t")) {
					final Calendar d = Calendar.getInstance();
					d.setTime(new Date((long) (t * 1000)));
					final LuaTable tbl = LuaValue.tableOf();
					tbl.set("year", LuaValue.valueOf(d.get(Calendar.YEAR)));
					tbl.set("month", LuaValue.valueOf(d.get(Calendar.MONTH) + 1));
					tbl.set("day", LuaValue.valueOf(d.get(Calendar.DAY_OF_MONTH)));
					tbl.set("hour", LuaValue.valueOf(d.get(Calendar.HOUR)));
					tbl.set("min", LuaValue.valueOf(d.get(Calendar.MINUTE)));
					tbl.set("sec", LuaValue.valueOf(d.get(Calendar.SECOND)));
					tbl.set("wday", LuaValue.valueOf(d.get(Calendar.DAY_OF_WEEK)));
					tbl.set("yday", LuaValue.valueOf(d.get(0x6))); // Day of year
					tbl.set("isdst", LuaValue.valueOf(RestrictedOsLib.this.isDaylightSavingsTime(d)));
					return tbl;
				}
				return LuaValue.valueOf(RestrictedOsLib.this.date(s, t == -1 ? RestrictedOsLib.this.time(null) : t));
			}
			case DIFFTIME: {
				return LuaValue.valueOf(RestrictedOsLib.this.difftime(args.checkdouble(1), args.checkdouble(2)));
			}
			case TIME: {
				return LuaValue.valueOf(RestrictedOsLib.this.time(args.opttable(1, null)));
			}
			}
			return LuaValue.NONE;
		};
	}

	/**
	 * Returns the number of seconds from time t1 to time t2. In POSIX, Windows, and some other systems, this value is exactly t2-t1.
	 * 
	 * @param t2
	 * @param t1
	 * @return diffeence in time values, in seconds
	 */
	protected double difftime(final double t2, final double t1) {
		return t2 - t1;
	}

	/**
	 * If the time argument is present, this is the time to be formatted (see the os.time function for a description of this value). Otherwise, date formats the current time.
	 * 
	 * Date returns the date as a string, formatted according to the same rules as ANSII strftime, but without support for %g, %G, or %V.
	 * 
	 * When called without arguments, date returns a reasonable date and time representation that depends on the host system and on the current locale (that is, os.date() is equivalent to os.date("%c")).
	 * 
	 * @param format
	 * @param time
	 *            time since epoch, or -1 if not supplied
	 * @return a LString or a LTable containing date and time, formatted according to the given string format.
	 */
	public String date(String format, double time) {
		final Calendar d = Calendar.getInstance();
		d.setTime(new Date((long) (time * 1000)));
		if (format.startsWith("!")) {
			time -= this.timeZoneOffset(d);
			d.setTime(new Date((long) (time * 1000)));
			format = format.substring(1);
		}
		final byte[] fmt = format.getBytes();
		final int n = fmt.length;
		final Buffer result = new Buffer(n);
		byte c;
		for (int i = 0; i < n;) {
			switch (c = fmt[i++]) {
			case '\n':
				result.append("\n");
				break;
			default:
				result.append(c);
				break;
			case '%':
				if (i >= n)
					break;
				switch (c = fmt[i++]) {
				default:
					LuaValue.argerror(1, "invalid conversion specifier '%" + c + "'");
					break;
				case '%':
					result.append((byte) '%');
					break;
				case 'a':
					result.append(RestrictedOsLib.WeekdayNameAbbrev[d.get(Calendar.DAY_OF_WEEK) - 1]);
					break;
				case 'A':
					result.append(RestrictedOsLib.WeekdayName[d.get(Calendar.DAY_OF_WEEK) - 1]);
					break;
				case 'b':
					result.append(RestrictedOsLib.MonthNameAbbrev[d.get(Calendar.MONTH)]);
					break;
				case 'B':
					result.append(RestrictedOsLib.MonthName[d.get(Calendar.MONTH)]);
					break;
				case 'c':
					result.append(this.date("%a %b %d %H:%M:%S %Y", time));
					break;
				case 'd':
					result.append(String.valueOf(100 + d.get(Calendar.DAY_OF_MONTH)).substring(1));
					break;
				case 'H':
					result.append(String.valueOf(100 + d.get(Calendar.HOUR_OF_DAY)).substring(1));
					break;
				case 'I':
					result.append(String.valueOf(100 + (d.get(Calendar.HOUR_OF_DAY) % 12)).substring(1));
					break;
				case 'j': { // day of year.
					final Calendar y0 = this.beginningOfYear(d);
					final int dayOfYear = (int) ((d.getTime().getTime() - y0.getTime().getTime()) / (24 * 3600L * 1000L));
					result.append(String.valueOf(1001 + dayOfYear).substring(1));
					break;
				}
				case 'm':
					result.append(String.valueOf(101 + d.get(Calendar.MONTH)).substring(1));
					break;
				case 'M':
					result.append(String.valueOf(100 + d.get(Calendar.MINUTE)).substring(1));
					break;
				case 'p':
					result.append(d.get(Calendar.HOUR_OF_DAY) < 12 ? "AM" : "PM");
					break;
				case 'S':
					result.append(String.valueOf(100 + d.get(Calendar.SECOND)).substring(1));
					break;
				case 'U':
					result.append(String.valueOf(this.weekNumber(d, 0)));
					break;
				case 'w':
					result.append(String.valueOf((d.get(Calendar.DAY_OF_WEEK) + 6) % 7));
					break;
				case 'W':
					result.append(String.valueOf(this.weekNumber(d, 1)));
					break;
				case 'x':
					result.append(this.date("%m/%d/%y", time));
					break;
				case 'X':
					result.append(this.date("%H:%M:%S", time));
					break;
				case 'y':
					result.append(String.valueOf(d.get(Calendar.YEAR)).substring(2));
					break;
				case 'Y':
					result.append(String.valueOf(d.get(Calendar.YEAR)));
					break;
				case 'z': {
					final int tzo = this.timeZoneOffset(d) / 60;
					final int a = Math.abs(tzo);
					final String h = String.valueOf(100 + a / 60).substring(1);
					final String m = String.valueOf(100 + a % 60).substring(1);
					result.append((tzo >= 0 ? "+" : "-") + h + m);
					break;
				}
				}
			}
		}
		return result.tojstring();
	}

	private static final String[]	WeekdayNameAbbrev	= { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
	private static final String[]	WeekdayName			= { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
	private static final String[]	MonthNameAbbrev		= { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
	private static final String[]	MonthName			= { "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" };

	private Calendar beginningOfYear(final Calendar d) {
		final Calendar y0 = Calendar.getInstance();
		y0.setTime(d.getTime());
		y0.set(Calendar.MONTH, 0);
		y0.set(Calendar.DAY_OF_MONTH, 1);
		y0.set(Calendar.HOUR_OF_DAY, 0);
		y0.set(Calendar.MINUTE, 0);
		y0.set(Calendar.SECOND, 0);
		y0.set(Calendar.MILLISECOND, 0);
		return y0;
	}

	private int weekNumber(final Calendar d, final int startDay) {
		final Calendar y0 = this.beginningOfYear(d);
		y0.set(Calendar.DAY_OF_MONTH, 1 + (startDay + 8 - y0.get(Calendar.DAY_OF_WEEK)) % 7);
		if (y0.after(d)) {
			y0.set(Calendar.YEAR, y0.get(Calendar.YEAR) - 1);
			y0.set(Calendar.DAY_OF_MONTH, 1 + (startDay + 8 - y0.get(Calendar.DAY_OF_WEEK)) % 7);
		}
		final long dt = d.getTime().getTime() - y0.getTime().getTime();
		return 1 + (int) (dt / (7L * 24L * 3600L * 1000L));
	}

	private int timeZoneOffset(final Calendar d) {
		final int localStandarTimeMillis = (d.get(Calendar.HOUR_OF_DAY) * 3600 + d.get(Calendar.MINUTE) * 60 + d.get(Calendar.SECOND)) * 1000;
		return d.getTimeZone().getOffset(1, d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH), d.get(Calendar.DAY_OF_WEEK), localStandarTimeMillis) / 1000;
	}

	private boolean isDaylightSavingsTime(final Calendar d) {
		return this.timeZoneOffset(d) != d.getTimeZone().getRawOffset() / 1000;
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

	/**
	 * Returns the current time when called without arguments, or a time representing the date and time specified by the given table. This table must have fields year, month, and day, and may have fields hour, min, sec, and isdst (for a description
	 * of these fields, see the os.date function).
	 * 
	 * @param table
	 * @return long value for the time
	 */
	protected double time(final LuaTable table) {
		java.util.Date d;
		if (table == null) {
			d = new java.util.Date();
		} else {
			final Calendar c = Calendar.getInstance();
			c.set(Calendar.YEAR, table.get("year").checkint());
			c.set(Calendar.MONTH, table.get("month").checkint() - 1);
			c.set(Calendar.DAY_OF_MONTH, table.get("day").checkint());
			c.set(Calendar.HOUR, table.get("hour").optint(12));
			c.set(Calendar.MINUTE, table.get("min").optint(0));
			c.set(Calendar.SECOND, table.get("sec").optint(0));
			c.set(Calendar.MILLISECOND, 0);
			d = c.getTime();
		}
		return d.getTime() / 1000.;
	}
}
