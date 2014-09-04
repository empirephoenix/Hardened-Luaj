package de.visiongamestudios;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.luaj.vm2.Globals;
import org.luaj.vm2.InstructionLimit;
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * This file should show a very long running function, that just gets paused and resumed when reaching instruction limits
 * 
 * @author empire
 *
 */
public class TestLongRunningTask {
	private static int	lastMemory;

	public static void main(final String[] args) throws FileNotFoundException, InterruptedException {
		final Globals globals = HardenedGlobals.standardGlobals();

		final StringBuilder script = new StringBuilder();
		final Scanner in = new Scanner(new File("./src/test/java/de/visiongamestudios/longRunningTask.lua"));
		while (in.hasNextLine()) {
			if (script.length() > 0) {
				script.append("\n");
			}
			script.append(in.nextLine());
		}
		in.close();
		System.out.println(script);

		final LuaClosure chunk = (LuaClosure) globals.load(script.toString(), 500);

		final InstructionLimit initializerLimit = new InstructionLimit();
		initializerLimit.setMaxInstructions(50);
		initializerLimit.setMaxStringSize(100);
		InstructionLimit.instructionLimit(initializerLimit);
		chunk.call();

		final LuaClosure tickHook = (LuaClosure) globals.get("tick");
		final LuaThread tickWorker = new LuaThread(globals, tickHook.checkfunction());
		tickWorker.resume(LuaValue.NIL); // tick 1 is expected to be immidiatly put to sleep,as no limits are yet configured
		final InstructionLimit coroutineInstructionLimit = InstructionLimit.instructionLimit(tickWorker);
		coroutineInstructionLimit.setMaxInstructions(500);
		coroutineInstructionLimit.setMaxStringSize(100);

		while (true) {
			Thread.sleep(50);
			final int mb = 1024 * 1024;
			final Runtime runtime = Runtime.getRuntime();
			final long start = System.currentTimeMillis();
			final Varargs returnValue = tickWorker.resume(LuaValue.NIL); // tick 1 is expected to be immidiatly put to sleep
			final int newMemory = globals.getUsedMemory();
			final int memoryGrowth = newMemory - TestLongRunningTask.lastMemory;
			TestLongRunningTask.lastMemory = newMemory;
			System.out.println("Used Memory " + TestLongRunningTask.lastMemory + " " + ((memoryGrowth > 0) ? ("+" + memoryGrowth) : memoryGrowth));
			System.out.println("Took " + (System.currentTimeMillis() - start) + " Instructions " + coroutineInstructionLimit.getCurrentInstructions() + "  Used Memory:" + (runtime.totalMemory() - runtime.freeMemory()) / mb);
			final LuaValue processedWithoutError = returnValue.arg(1);
			if (processedWithoutError.isboolean() && !((LuaBoolean) processedWithoutError).v) {
				System.out.println("Terminating long running Task due to error " + returnValue.arg(2));
				break;
			} else {
				InstructionLimit.reset(tickWorker);
			}

		}
	}
}
