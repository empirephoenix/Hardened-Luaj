package de.visiongamestudios;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public class Test {
	public static void main(final String[] args) throws FileNotFoundException, InterruptedException {
		final Globals globals = HardenedGlobals.standardGlobals();

		// list global methods!
		for (final LuaValue key : globals.keys()) {
			final LuaValue value = globals.get(key);
			if (value.istable()) {
				final LuaTable t = (LuaTable) value;
				for (final LuaValue tk : t.keys()) {
					final LuaValue tv = t.get(tk);
					if (tv.isfunction()) {
						System.out.println("Function  " + key + "." + tk);
					}
				}

			}
		}

		final StringBuilder script = new StringBuilder();
		final Scanner in = new Scanner(new File("./src/test/java/de/visiongamestudios/doorcontroller.lua"));
		while (in.hasNextLine()) {
			if (script.length() > 0) {
				script.append("\n");
			}
			script.append(in.nextLine());
		}
		in.close();
		System.out.println(script);

		final LuaClosure chunk = (LuaClosure) globals.load(script.toString(), 500);
		Varargs.getInstructionLimit().setMaxInstructions(50);
		Varargs.getInstructionLimit().setMaxStringSize(100);
		Varargs.getInstructionLimit().reset();

		chunk.call();

		Varargs.getInstructionLimit().reset();
		final LuaClosure openDoorHook = (LuaClosure) globals.get("canOpenDoor");
		final LuaClosure tickHook = (LuaClosure) globals.get("tick");

		final LuaTable door = new LuaTable();
		door.set("processing", new LuaFunction() {
			@Override
			public LuaValue call() {
				System.out.println("Door is processing");
				return LuaValue.NONE;
			}
		});

		door.set("open", new LuaFunction() {
			@Override
			public LuaValue call() {
				System.out.println("Access Granted");
				return LuaValue.NONE;
			}
		});

		door.set("deny", new LuaFunction() {
			@Override
			public LuaValue call() {
				System.out.println("Access Denied");
				return LuaValue.NONE;
			}
		});
		final LuaTable values = new LuaTable();
		values.set("name", "empire");

		int tick = 0;
		while (tick < 10) {
			tick++;
			if (tick == 7) {
				openDoorHook.call(values, door);
			}
			Thread.sleep(50);
			final int mb = 1024 * 1024;
			final Runtime runtime = Runtime.getRuntime();

			final long start = System.currentTimeMillis();
			Varargs.getInstructionLimit().reset();
			tickHook.call();
			System.out.println("Took " + (System.currentTimeMillis() - start) + " Instructions " + Varargs.getInstructionLimit().getCurrentInstructions() + "  Used Memory:" + (runtime.totalMemory() - runtime.freeMemory()) / mb);
		}
	}
}
