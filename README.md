Harened-Luaj
============

This is a modified version of the Luaj Project, aimed at providing a hardened enviroment to execute unsave scripts.
Currently this is very vip.

All limits are optional and configurable, some even for each call.

1. Maximum Ram limit, curretn useage is determined by traversing all reachable Lua Objects, and counting them.
 *  Prohibits to create a instructions low loop, that creates extremly large Objects

2. Added a Thread local counter for instructions, allowing to limit the maximum amount of instructions a call can make.
 * Can be reset from the JVM side
 * Reaching the limit creates a Exception on the calling java side, that cannot be circumvented in lua.

3. Removed parts that allow execution of bytecode
 * There are some vulnarabilities to handmanipulated bytecode

4. Removed jvm object interoperability, as this is nearly impossible to saveguard
 * It is still easily possible to add functions to lua objects from the java side, however they now need to be explicitly defined.

5. Removed larger parts of the default librarys, (eg the ability to execute cmd lines)
 * require can now be used to load other scripts, any other means to execute files is removed

6. Performance 
 * To reduce the required instructions for fast operations creation of some java side functionality.
 * table.contains is such an example, this operation is really fast, but would need a large amount of instructions if done in lua.
7.1.2 Ability to create instructions costs from java side. For example domain specific function could be given a cost to reduce unnecessary calls to it.

LICENSE:
The same license as for LUAJ applies according to Sourceforge this is seems to be MIT License (the links are somewhat down)
