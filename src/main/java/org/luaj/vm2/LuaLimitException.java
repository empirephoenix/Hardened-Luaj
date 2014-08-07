package org.luaj.vm2;


public class LuaLimitException extends RuntimeException {

	public LuaLimitException() {
		super();
	}

	public LuaLimitException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public LuaLimitException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public LuaLimitException(final String message) {
		super(message);
	}

	public LuaLimitException(final Throwable cause) {
		super(cause);
	}

}
