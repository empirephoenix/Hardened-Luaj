package de.visiongamestudios;

import java.io.InputStream;

import org.luaj.vm2.Globals;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseMathLib;

public class HardenedGlobals {
	public static Globals standardGlobals() {
		final Globals globals = new Globals();
		globals.load(new BaseLib() {

			@Override
			public InputStream findResource(String filename) {
				throw new RuntimeException("Not implemented for testCases");
			}
		});
		globals.load(new PackageLib());
		globals.load(new Bit32Lib());
		globals.load(new TableLib(globals));
		globals.load(new StringLib());
		globals.load(new JseMathLib());
		globals.load(new RestrictedOsLib());
		globals.load(new DebugLib());
		globals.load(new CoroutineLib());
		LuaC.install(globals);
		return globals;
	}
}
