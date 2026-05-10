# create-logic
Mod for minecraft, adding own safe scripting language for Create automation.

[![Modrinth](https://img.shields.io/badge/Modrinth-Create:_Logic-00AF5C)](https://modrinth.com/mod/create-logic)

A create automation with using own programming language, naming "Metal". Place a computer, right click, enter code, press save, shift+right click for toggle run.

# Coding: 
Declare 2 Systems (System is just like function), "INIT" and "TICK" like that:

SYSTEM(INIT): START;

init code here

END_SYSTEM;

SYSTEM(TICK): START;

tick (loop) code here

END_SYSTEM;

# API: 
There is some functions to work with Metal.

**built-in functions**:

MAKE_VAR(VAR_TYPE,$name,start_value); - making a global variable (global variable MUST start with '$', ONLY in INIT)

MAKE_ITEM_VAR($name,item_id); - making a INT variable by minecraft item number id (ONLY in INIT)

SET($name,value); - set new value for variable

ADD/SUB/MUL/DIV($name,value); - operations with variables

WAIT(ms); - delaying script (ONLY in TICK)

CONTINUE(); - return to the current System begin (ONLY in TICK)

RERUN(); - return to the main System of current script (ONLY in TICK)

EXIT_STACK(); - exit entire stack of System calls and returns to the point of stack start

RETURN(); - exit 1 System of stack of System calls

ABORT(); - forcibly terminate current script (Doesn't turn off computer)

CALL_SYSTEM(name, args...); - call System (max stack size is 16), cannot call Systems-EntryPoints (INIT, TICK), args is ONLY in in 0.0.3+ mod version

IF(condition): START; - Base IF operator (closing - "END_IF;")

ELSE(): START; - Base ELSE operator (between IF and END_IF)

BIND_ARGS_TO_SYSTEM(name, args...); ONLY in INIT, args must be like "#var:INT", setting arguments to system call (CALL_SYSTEM), ONLY in 0.0.3+ mod version

MAKE_LOCAL_VAR(VAR_TYPE,#name,start_value); - making a local variable (local variable MUST start with '#') ONLY in 0.0.3+ mod version

ACCEPT_INVOKES(); - allow invoke non-reserved systems by integrations ONLY in INIT, for 0.0.4+ mod version

**end of built-in functions**

**Java Functions** (registered outside of a Metal):

CONNECT_LINK_SENDER(id,item1,item2); - making a transmitter connection (for two last arguments recommends use MAKE_ITEM_VAR, id is any value, doesn't used before), ONLY in INIT

CONNECT_LINK_RECEIVER(id,item1,item2); - making a receiver connection (use MAKE_ITEM_VAR variables for two last arguments) ONLY in INIT

SEND_LINK(id,power); - send power to Create Redstone Link (use with id, used in CONNECT_LINK_SENDER)

RECEIVE_LINK(id); - receive power from Create Redstone Link (use with id, used in CONNECT_LINK_RECEIVER)

RANDOM(min,max); - returns random INT between min and max

RAND(); - returns random DOUBLE between 0.0 - 1.0

SIN(value); - returns sin of the value (use radians)

COS(value); - returns cos of the value (use radians)

TAN(value); - returns tan of the value (use radians)

SQRT(value); - returns sqrt of the value

DTR(value); - degrees to radians

RTD(value); - radians to degrees

GET_PI(); - returns PI

MIN(args...); - returns MIN of all arguments

MAX(args...); - returns MAX of all arguments

CLAMP(value,min,max); - returns limited value in range

ABS(value); - returns module of value

POW(value,pow); - returns exponentiation of the value

# end of Java Functions;

# Tips:

ADD,SUB, etc using ONLY in modification of value for example:

ADD($x,1); = x += 1;

In brackets:

IF($x + 1 > $y): START;

code

END_IF;

And, ": START" is not necessarily, but recommended, you also can use

SYSTEM(INIT); ...

IF(condition); ...

etc.

Current variable types: INT, DOUBLE, POWER, BOOL.

TICK EntryPoint (System) running in the other thread 1000 times per second.

# INTEGRATION: 
Since 0.0.4 you can call non-reserved systems from Computer Craft mod, its looks like: 
Metal: SYSTEM(INIT): START; 
ACCEPT_INVOKES(); 
BIND_ARGS_TO_SYSTEM(SUM,#first:DOUBLE,#second:DOUBLE); 
END_SYSTEM;

SYSTEM(SUM): START; 
RETURN(#first + #second);
END_SYSTEM;

SYSTEM(TICK): START;

END_SYSTEM;

CC Lua: local metal = peripheral.find("metal"); if metal then print(metal.callSystem("SUM",5,10)); else print("Cannot find Metal Computer"); end

# Addons API:
You can register your own metal functions like that:
Java:
static {
Metal.register("YOUR_FUNCTION", (script, args) -> {
// ...
});
}

get **Metal** object:
Metal metal = script.getHost();

get **ComputerBlockEntity** (or null):
if (metal.getOwner() instanceof ComputerBlockEntity be) {
// be is computer
}

**call system**:
metal.callSystem("YOUR_SYSTEM_NAME", args);

**inject script**:
Metal.Script scr = new Metal.Script(RunContext.RUN,new Metal.Script.CodeBlock(Metal.Script.CodeBlockType.SYSTEM,"YOUR CODE HERE",0), metal, true);
Metal.MetalVariable result = scr.execute(args, operationsLimit(long));

# Addons API: Modules
- Since **0.0.6** version added Metal.MetalModule, there is API:

- public class YourModuleName extends Metal.MetalModule {
-  public YourModuleName(Metal metal) {
-    super(metal);
-  }
-  @Override
-  protected void onStart(){
-    // on computer start
-  }
-  @Override
-  protected void onStop(){
-    // on computer stop
-  }
-  protected void onServerTick() {
-    // on every server tick
-  }
-  @Override
-  protected void onNext(Metal.Script script, String cmd, Metal.Script.Bracket head) {
-    // script executes function
-  }
-  @Override
-  protected boolean handleAdvanced(Metal.Script script, String cmd, Metal.Script.Bracket head) {
-    // function intercept, you can add or overwrite built-in functions: return true - intercept, false - continue
-  }
- }

- MetalModule.evaluate(Script script,String expr) - protected method, wrapper to private Script.evaluate(String expr)
- MetalModule.putVariable(String name, MetalVariable var) - protected method, wrapper to private Metal.putVariable(String name, MetalVariable var)
- MetalModule.getVariable(Script script, String name) - protected method, wrapper to private Script.getVariable (auto global or local)
- Map<String,MetalVariable> getDynamicLocalVariables(Script script) - getter, Script.DYNAMIC_LOCAL_VARIABLES (created by MAKE_LOCAL_VAR)
- Map<String,MetalVariable> getLocalVariables(Script script) - getter, Script.LOCAL_VARIABLES (system arguments)
- Map<String,MetalVariable> getGlobalVariables() - getter, Metal.VARIABLES (created by MAKE_VAR or MAKE_ITEM_VAR in INIT)

registration:

- // somewhere in static {...}
- Metal.registerModule("your_module_name",YourModuleName.class);

using in JFunctions or another modules, or somewhere else:

- Metal metal = // Your method to get Metal object
- if(metal.getModule("your_module_name") instanceof YourModuleName yourModule) {
-  // if you make method, for example, helloWorld() in your module
-  yourModule.helloWorld();
- }

> Note: Registered modules creating in every Metal instance
