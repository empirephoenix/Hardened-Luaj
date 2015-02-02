Harened-Luaj
============

This is a modified version of the Luaj Project, aimed at providing a hardened enviroment to execute unsave scripts.
Currently this is very vip.

All limits are optional and configurable, some even for each call.

1. Limit the maximum String lenght possible via concationation to prevent memory hogging
 *  Prohibits to create a instructions low loop, that creates extremly large Strings, by concatination with itself, leading to very memory intensive variables
 * Since variable creation uses instructions, prevents memory hogging due the allocation of millions of them
 * Ensured that no sized array constructor exit, to prevent the allocation of very large arrays with few instructions (combined with the instruction limit, this creates a practical upper limit at runtime)
 * planned (add a sized array constructor, that uses a defined limit to prevent arrays over a predefined size)

2. Limit the maximum letters a script is allowed to have
 * Prevents large inline Strings/data.

3. Added a Thread local counter for instructions, allowing to limit the maximum amount of instructions a call can make.
 * Can be reset from the JVM side
 * Reaching the limit creates a Exception on the calling java side, that cannot be circumvented in lua.

4. Removed parts that allow execution of bytecode
 * There are some vulnarabilities to handmanipulated bytecode, also later is planned a system to check the sourcecode for problems before compilation/executions.

5. Removed jvm object interoperability, as this is nearly impossible to saveguard
 * It is still easiyl possible to add functions to lua objects from the java side, however they now need to be explicitly defined.

6. Removed larger parts of the default librarys, (eg the ability to execute cmd lines)
 * planned (creating a whitelist for modules allowed to be loaded via require currently all are possible)

7. Performance 
 * To reduce the required instructions for fast operations creation of some java side functionality.
 * table.contains is such an example, this operation is really fast, but would need a large amount of instructions if done in lua.
7.1.2 Ability to create instructions costs from java side. For example domain specific function could be given a cost to reduce unnecessary calls to it.

LICENSE:
The same license as for LUAJ applies according to Sourceforge this is seems to be MIT License (the links are somewhat down)
