package org.luaj.vm2;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * instruction counter
 * 
 * @author empire
 *
 */
public class InstructionLimit {
	private static ConcurrentHashMap<Thread, InstructionLimit>	instructionLimit	= new ConcurrentHashMap<Thread, InstructionLimit>();

	private int													currentInstructions;
	private int													maxInstructions;

	/**
	 * returns the threadlocal instructions counter
	 * 
	 * @return
	 */
	public static InstructionLimit instructionLimit() {
		return InstructionLimit.instructionLimit(Thread.currentThread(), null);
	}

	public static InstructionLimit instructionLimit(final Thread currentThread) {
		return InstructionLimit.instructionLimit(currentThread, null);
	}

	public static InstructionLimit instructionLimit(final InstructionLimit initializerLimit) {
		return InstructionLimit.instructionLimit(Thread.currentThread(), initializerLimit);
	}

	public static InstructionLimit instructionLimit(final Thread currentThread, final InstructionLimit defaultValue) {
		InstructionLimit rv = InstructionLimit.instructionLimit.get(currentThread);
		if (rv == null) {
			if (defaultValue == null) {
				throw new RuntimeException("No Instruction Limits set, and no default provided");
			}
			rv = defaultValue;
			InstructionLimit.instructionLimit.put(currentThread, defaultValue);
		}
		return rv;
	}

	public void setMaxInstructions(final int maxInstructions) {
		this.maxInstructions = maxInstructions;
	}

	public int getMaxInstructions() {
		return this.maxInstructions;
	}

	public int getCurrentInstructions() {
		return this.currentInstructions;
	}

	public static boolean reset() {
		return InstructionLimit.reset(Thread.currentThread());
	}

	public static boolean reset(final LuaThread luaThread) {
		final WeakReference<Thread> threadref = luaThread.getThread();
		assert threadref != null : "Thread was never resumed before!";
		final Thread thread = threadref.get();
		if (thread == null) {
			return false;
		}
		return InstructionLimit.reset(thread);
	}

	public static boolean reset(final Thread thread) {
		final InstructionLimit rv = InstructionLimit.instructionLimit.get(thread);
		if (rv == null) {
			return false;
		}
		rv.currentInstructions = 0;
		return true;
	}

	/**
	 * returns the InstructionLimit if possible, if none exists a empty Zero limit is created (necessary for coroutines)
	 * 
	 * @param luaThread
	 * @return
	 */
	public static InstructionLimit instructionLimit(final LuaThread luaThread) {
		final WeakReference<Thread> threadref = luaThread.getThread();
		assert threadref != null : "Thread was never resumed before!";
		final Thread thread = threadref.get();
		if (thread == null) {
			throw new RuntimeException("Thread is already GarbageCollected");
		}
		return InstructionLimit.instructionLimit(thread, new InstructionLimit());
	}

	public static boolean increase(final Thread t, final int instructions) {
		final InstructionLimit rv = InstructionLimit.instructionLimit.get(t);
		if (rv == null) {
			return false;
		}
		rv.currentInstructions += instructions;
		if (rv.currentInstructions >= rv.maxInstructions) {
			return false;
		}
		return true;
	}

	public static boolean increase(final int instructions) {
		return InstructionLimit.increase(Thread.currentThread(), instructions);
	}

	public static boolean increase() {
		return InstructionLimit.increase(1);
	}

}
