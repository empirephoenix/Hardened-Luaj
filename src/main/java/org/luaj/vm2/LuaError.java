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
package org.luaj.vm2;

/**
 * RuntimeException that is thrown and caught in response to a lua error.
 * <p>
 * {@link LuaError} is used wherever a lua call to {@code error()} would be used within a script.
 * <p>
 * Since it is an unchecked exception inheriting from {@link RuntimeException}, Java method signatures do notdeclare this exception, althoug it can be thrown on almost any luaj Java operation. This is analagous to the fact that any lua script can
 * throw a lua error at any time.
 * <p>
 */
public class LuaError extends RuntimeException {
	private static final long	serialVersionUID	= 1L;

	protected int				level;

	protected String			fileline;

	protected String			traceback;

	protected Throwable			cause;

	@Override
	public String getMessage() {
		if (this.traceback != null) {
			return this.traceback;
		}
		final String m = super.getMessage();
		if (m == null) {
			return null;
		}
		if (this.fileline != null) {
			return this.fileline + " " + m;
		}
		return m;
	}

	/**
	 * Construct LuaError when a program exception occurs.
	 * <p>
	 * All errors generated from lua code should throw LuaError(String) instead.
	 * 
	 * @param cause
	 *            the Throwable that caused the error, if known.
	 */
	public LuaError(final Throwable cause) {
		super("vm error: " + cause);
		this.cause = cause;
		this.level = 1;
	}

	/**
	 * Construct a LuaError with a specific message.
	 * 
	 * @param message
	 *            message to supply
	 */
	public LuaError(final String message) {
		super(message, null, false, false);
		this.level = 1;
	}

	/**
	 * Construct a LuaError with a message, and level to draw line number information from.
	 * 
	 * @param message
	 *            message to supply
	 * @param level
	 *            where to supply line info from in call stack
	 */
	public LuaError(final String message, final int level) {
		super(message, null, false, false);
		this.level = level;
	}

	public LuaError(final String message, final Throwable cause) {
		super(message, cause, false, false);
	}

	/**
	 * Get the cause, if any.
	 */
	@Override
	public synchronized Throwable getCause() {
		return this.cause;
	}

}
