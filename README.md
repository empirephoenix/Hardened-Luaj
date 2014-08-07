Harened-Luaj
============

This is a modified version of the Luaj Project, aimed at providing a hardened enviroment to execute unsave scripts.
Currently this is very vip.

All limits are optional and configurable, some even for each call.
1. Limit the maximum String lenght possible via concationation to prevent memory hogging
1.1 Prohibits to create a instructions low loop, that creates extremly large Strings, by concatination with itself, leading to very memory intensive variables
1.2 Since variable creation uses instructions, prevents memory hogging due the allocation of millions of them
1.3 Ensured that no sized array constructor exit, to prevent the allocation of very large arrays with few instructions (combined with the instruction limit, this creates a practical upper limit at runtime)
1.4 planned (add a sized array constructor, that uses a defined limit to prevent arrays over a predefined size)

2. Limit the maximum letters a script is allowed to have
2.1 Prevents large inline Strings/data.

3. Added a Thread local counter for instructions, allowing to limit the maximum amount of instructions a call can make.
3.1 Can be reset from the JVM side
3.2 Reaching the limit creates a Exception on the calling java side, that cannot be circumvented in lua.

4. Removed parts that allow execution of bytecode
4.1 There are some vulnarabilities to handmanipulated bytecode, also later is planned a system to check the sourcecode for problems before compilation/executions.

5. Removed jvm object interoperability, as this is nearly impossible to saveguard
5.1 It is still easiyl possible to add functions to lua objects from the java side, however they now need to be explicitly defined.

6. Removed larger parts of the default librarys, (eg the ability to execute cmd lines)
6.1 planned (creating a whitelist for modules allowed to be loaded via require currently all are possible)

7. Performance 
7.1 To reduce the required instructions for fast operations creation of some java side functionality.
7.1.1 table.contains is such an example, this operation is really fast, but would need a large amount of instructions if done in lua.
7.1.2 Ability to create instructions costs from java side. For example domain specific function could be given a cost to reduce unnecessary calls to it.

