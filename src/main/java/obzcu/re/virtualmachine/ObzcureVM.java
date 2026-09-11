package obzcu.re.virtualmachine;

import obzcu.re.virtualmachine.asm.VMHandle;
import obzcu.re.virtualmachine.asm.VMTryCatch;
import obzcu.re.virtualmachine.asm.VMType;
import obzcu.re.virtualmachine.types.VMNode;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author HoverCatz
 * @created 09.01.2022
 * @url https://github.com/HoverCatz
 **/
public final class ObzcureVM
{

    private final Map<Integer, Object> localsMap;
    private final VMStack vmStack;
    private final VMTryCatch[] tryCatch;
    private final MethodHandles.Lookup lookup;

    private final VMNode[] instructions;
    private int pointer = 0, lineNumber = 0;
    private String className = null, methodName = null, methodDesc =  null;

    // PoC stub for OFF-MACHINE seed delivery. The diversification seed is NOT shipped in the
    // artifact; it is supplied at runtime from outside. Here that "outside" is a system property
    // (-Dobzcure.seed=...). In Phase B this is replaced by the license server's response, returned
    // only after license + hardware-fingerprint authentication. A dumped blob alone cannot decode.
    // Anti-debug (envelope layer): refuse to reveal the seed if a debugger or instrumentation
    // agent is present. This guards the single moment the off-machine secret is exposed, which is
    // exactly how a dynamic attacker (jdb/JDWP or -javaagent, both Level 2) would try to grab it.
    // Detection here is via the JVM's own launch arguments (portable, no native code needed).
    private static void antiDebugCheck()
    {
        // Anti-debug: catch a debugger/agent injected at JVM startup. getInputArguments() reflects
        // BOTH command-line flags AND options injected via JAVA_TOOL_OPTIONS / _JAVA_OPTIONS env
        // vars (empirically verified), so this covers command-line and environment-variable injection.
        //
        // KNOWN LIMITATION (empirically established, see ArgProbe): this does NOT catch a debugger
        // ATTACHED AFTER LAUNCH via the Attach API. A naive runtime thread-scan does not help either,
        // because JDWP's transport threads are VM-internal and are NOT visible to
        // Thread.getAllStackTraces(). Reliable runtime detection of a dynamically-attached debugger
        // requires native / JVMTI code -> future work (PhD native anti-debug).
        for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())
        {
            String a = arg.toLowerCase();
            if (a.contains("jdwp") || a.contains("-xdebug") || a.contains("-agentlib")
                    || a.contains("-agentpath") || a.contains("-javaagent"))
                throw new IllegalStateException("Debugger or instrumentation agent detected; refusing to run.");
        }
    }

    private static volatile Long cachedSeed = null;

    private static long fetchSeed()
    {
        antiDebugCheck();

        // Test/override path (DEV_MODE builds only): an explicit seed still works (DiffTest harness +
        // ObzcureVM's own tests). Stripped from release builds (F2): with DEV_MODE=false javac elides
        // this branch, so there is no compiled-in -Dobzcure.seed bypass of the server + fingerprint + TLS.
        if (BlobCrypto.DEV_MODE) {
            String override = System.getProperty("obzcure.seed");
            if (override != null)
                return Long.parseLong(override);
        }

        // Off-machine path: fetch the seed from the license server ONCE, then reuse it (the seed is
        // constant per build). Server-first: an invalid/absent license -> no seed -> no decode -> lock.
        Long c = cachedSeed;
        if (c != null)
            return c;

        // F6: release enforces TLS. Dev defaults to cleartext localhost for the offline demos; a release
        // build (DEV_MODE=false) defaults to https, REFUSES a non-https server, and REFUSES to fall back to
        // the JVM's default (public-CA) trust store - it must validate against an explicit pinned trust
        // store (the client-truststore.p12 holding only the license server's cert). Compile-time gated.
        String server = System.getProperty("obzcure.server",
                BlobCrypto.DEV_MODE ? "http://localhost:8080" : "https://localhost:8443");
        if (!BlobCrypto.DEV_MODE) {
            if (!server.toLowerCase().startsWith("https://"))
                throw new IllegalStateException("Release requires an https license server; refusing cleartext.");
            if (System.getProperty("javax.net.ssl.trustStore") == null)
                throw new IllegalStateException("Release requires a pinned trust store (-Djavax.net.ssl.trustStore); refusing default CA trust.");
        }
        String license = System.getProperty("obzcure.license");
        if (license == null)
            throw new IllegalStateException(
                "No license provided: set -Dobzcure.license=... (the license server delivers the seed).");
        try
        {
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(server + "/seed?license="
                        + java.net.URLEncoder.encode(license, java.nio.charset.StandardCharsets.UTF_8)
                        + "&fingerprint="
                        + java.net.URLEncoder.encode(fingerprint(), java.nio.charset.StandardCharsets.UTF_8)))
                .GET().build();
            java.net.http.HttpResponse<String> resp =
                http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new IllegalStateException(
                    "License server refused the license (HTTP " + resp.statusCode() + "): " + resp.body());
            long seed = Long.parseLong(resp.body().trim());
            cachedSeed = seed;
            return seed;
        }
        catch (java.io.IOException | InterruptedException e)
        {
            throw new IllegalStateException(
                "Could not reach the license server at " + server + ": " + e.getMessage(), e);
        }
    }

    // Hardware fingerprint = the machine identity a license binds to (node-locking). Single source of
    // truth is BlobCrypto.fingerprint(), which ALSO derives the blob decode key (F2), so the machine
    // bound at the server is exactly the machine whose fingerprint must reproduce the key. The
    // -Dobzcure.fingerprint override and the hardware computation now live there (override DEV_MODE-only).
    private static String fingerprint()
    {
        return BlobCrypto.fingerprint();
    }

    public ObzcureVM(int index, int maxLocals, int maxStack)
    {
        this(index, maxLocals, maxStack, null, null);
    }

    public ObzcureVM(int index, int maxLocals, int maxStack, VMTryCatch[] tryCatch)
    {
        this(index, maxLocals, maxStack, tryCatch, null);
    }

    public ObzcureVM(int index, int maxLocals, int maxStack, VMTryCatch[] tryCatch, MethodHandles.Lookup lookup)
    {
        this.localsMap = new HashMap<>(maxLocals);
        this.vmStack = new VMStack(this, maxStack);
        this.tryCatch = tryCatch;
        this.lookup = lookup;
        try {
            long seed = fetchSeed();
            byte[] plain = BlobCrypto.decrypt(meowData.get(index), seed); // anti-dump: decrypt the blob at rest
            this.instructions = VMLoader.load(this, new DataInputStream(new ByteArrayInputStream(plain)), seed);
        } catch (Throwable e) {
            if (BlobCrypto.DEV_MODE) e.printStackTrace(); // F7: no failure-mode oracle in release builds
            throw new RuntimeException("Failed loading .meow data.");
        }
    }

    // VMTests constructor (gets removed automatically)
    public ObzcureVM(int index, int maxLocals, int maxStack, VMTryCatch[] tryCatch, MethodHandles.Lookup lookup, byte[] result)
    {
        this.localsMap = new HashMap<>(maxLocals);
        this.vmStack = new VMStack(this, maxStack);
        this.tryCatch = tryCatch;
        this.lookup = lookup;
        try {
            long seed = fetchSeed();
            byte[] plain = BlobCrypto.decrypt(result, seed); // anti-dump: Translator output is always encrypted
            this.instructions = VMLoader.load(this, new DataInputStream(new ByteArrayInputStream(plain)), seed);
        } catch (Throwable e) {
            if (BlobCrypto.DEV_MODE) e.printStackTrace(); // F7: no failure-mode oracle in release builds
            throw new RuntimeException("Failed loading .meow data.");
        }
    }

    public static synchronized ObzcureVM virtualize(int index, int maxLocals, int maxStack)
    {
        return new ObzcureVM(index, maxLocals, maxStack, null, (MethodHandles.Lookup)null);
    }
    public static synchronized ObzcureVM virtualize(int index, int maxLocals, int maxStack, VMTryCatch[] tryCatch)
    {
        return new ObzcureVM(index, maxLocals, maxStack, tryCatch, null);
    }
    public static synchronized ObzcureVM virtualize(int index, int maxLocals, int maxStack, MethodHandles.Lookup lookup)
    {
        return new ObzcureVM(index, maxLocals, maxStack, null, lookup);
    }
    public static synchronized ObzcureVM virtualize(int index, int maxLocals, int maxStack, VMTryCatch[] tryCatch, MethodHandles.Lookup lookup)
    {
        return new ObzcureVM(index, maxLocals, maxStack, tryCatch, lookup);
    }

    // VMTests (gets removed automatically)
    public static synchronized ObzcureVM virtualizeTests(int index, int maxLocals, int maxStack, MethodHandles.Lookup lookup, byte[] result)
    {
        return new ObzcureVM(index, maxLocals, maxStack, null, lookup, result);
    }
    public static synchronized ObzcureVM virtualizeTests(int index, int maxLocals, int maxStack, VMTryCatch[] tryCatch, MethodHandles.Lookup lookup)
    {
        return new ObzcureVM(index, maxLocals, maxStack, tryCatch, lookup);
    }
    public static synchronized ObzcureVM virtualizeTests(int index, int maxLocals, int maxStack, VMTryCatch[] tryCatch, MethodHandles.Lookup lookup, byte[] result)
    {
        return new ObzcureVM(index, maxLocals, maxStack, tryCatch, lookup, result);
    }

    private static final Map<Integer, byte[]> meowData = new HashMap<>();
    static
    {
        InputStream inputStream = ObzcureVM.class.getResourceAsStream("/obzcure/cats.meow");
        if (inputStream != null)
            try (DataInputStream dis = new DataInputStream(inputStream))
            {
                int count = dis.readInt();
                for (int i = 0; i < count; i++)
                {
                    int length = dis.readInt();
                    byte[] bytes = dis.readNBytes(length);
                    meowData.put(i, bytes);
                }
            }
            catch (IOException e)
            {
                if (BlobCrypto.DEV_MODE) e.printStackTrace(); // F7: no failure-mode oracle in release builds
                throw new RuntimeException("Failed loading .meow data.");
            }
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public String getClassName()
    {
        return this.className;
    }

    public void setMethodName(String methodName)
    {
        this.methodName = methodName;
    }

    public String getMethodName()
    {
        return this.methodName;
    }

    public void setMethodDesc(String methodDesc)
    {
        this.methodDesc = methodDesc;
    }

    public String getmethodDesc()
    {
        return this.methodDesc;
    }

    public void setLocal(int local, Object obj)
    {
        localsMap.put(local, obj);
    }

    public Object getLocal(int local)
    {
        return localsMap.get(local);
    }

    public void setLineNumber(int lineNumber)
    {
        this.lineNumber = lineNumber;
    }

    public int getLineNumber()
    {
        return lineNumber;
    }

    public void jump(int newPointer)
    {
        this.pointer = newPointer;
    }

    public int getPointer()
    {
        return pointer;
    }

    public MethodHandles.Lookup getMethodHandlesLookup()
    {
        return lookup;
    }

    public void push(Object obj)
    {
        vmStack.push(obj);
    }

    public void push(Object... objs)
    {
        for (Object obj : objs)
            vmStack.push(obj);
    }

    public Object pop()
    {
        return vmStack.pop();
    }

    public int stackSize()
    {
        return vmStack.size();
    }
    
    public boolean debug = false, printThrowable = false;

    public Object execute() throws Throwable
    {
        final VMStack stack = this.vmStack;
        VMTryCatch[] tryCatch = this.tryCatch;
        final VMNode[] insns = this.instructions;
        final int len = insns.length;
        if (debug) System.err.println("\033[91[" + className + "." + methodName + methodDesc + "]\033[0m");
        if (debug) System.err.println("\033[91minstructions.length: " + len + "\033[0m");
        while (pointer >= 0 && pointer < len)
            try
            {
                final int pc = pointer;
                VMNode insn = insns[pc];
                if (debug) System.out.print("\033[91m" +
                           "# " + pc + " " + stack + " -> " + insn.getClass().getSimpleName() + " -> ");
                insn.execute(this, stack);
                if (debug) System.err.println((pc != pointer ? "# " + pointer + " " : "") +
                           getOpcodeName(insn.opcode) + " " + (insn.input == null ? "" :
                           Arrays.toString(insn.input)) + "\033[0m");
                if (insn.doReturn)
                    return insn.retValue;
                if (pc == pointer)
                    pointer++;
            }
            catch (Throwable t)
            {
                if (tryCatch != null)
                {
                    Map<String, VMTryCatch> inside = new HashMap<>();
                    for (VMTryCatch vmTryCatch : tryCatch)
                        if (vmTryCatch.isWithin(pointer))
                            if (vmTryCatch.type.isInstance(t))
                                inside.put(vmTryCatch.start + "." + vmTryCatch.end + "." + vmTryCatch.handler, vmTryCatch);
                    if (!inside.isEmpty())
                    {
                        stack.clear();
                        stack.push(t);
                        if (inside.size() == 1)
                            jump(inside.values().iterator().next().handler);
                        else
                        {
                            int highestStart = 0;
                            for (String str : inside.keySet())
                            {
                                String[] split = str.split("\\.");
                                int start = Integer.parseInt(split[0]);
                                if (start > highestStart)
                                    highestStart = start;
                            }
                            int lowestHandler = Integer.MAX_VALUE;
                            String lowestHandlerString = "";
                            for (String str : inside.keySet())
                            {
                                String[] split = str.split("\\.");
                                int start = Integer.parseInt(split[0]);
                                int handler = Integer.parseInt(split[2]);
                                if (start != highestStart)
                                    continue;
                                if (handler < lowestHandler)
                                {
                                    lowestHandler = handler;
                                    lowestHandlerString = str;
                                }
                            }
                            jump(inside.get(lowestHandlerString).handler);
                        }
                        continue;
                    }
                }
                if (printThrowable)
                    System.err.println("\033[91m[#" + lineNumber + "] Error at " + className
                            + "." + methodName + methodDesc + ":" + t.getMessage() + "\033[0m");
//                t.printStackTrace();
                throw t;
            }
        return null; // Probably void return (...)V
    }

    private static final Map<String, Class<?>> classCache = new HashMap<>();
    public Class<?> getClass(String name) throws ClassNotFoundException
    {
        if (name.length() <= 7)
        {
            if ("int".equals(name) || "I".equals(name))
                return int.class;
            if ("long".equals(name) || "J".equals(name))
                return long.class;
            if ("float".equals(name) || "F".equals(name))
                return float.class;
            if ("double".equals(name) || "D".equals(name))
                return double.class;
            if ("char".equals(name) || "C".equals(name))
                return char.class;
            if ("short".equals(name) || "S".equals(name))
                return short.class;
            if ("byte".equals(name) || "B".equals(name))
                return byte.class;
            if ("boolean".equals(name) || "Z".equals(name))
                return boolean.class;
            if ("void".equals(name) || "V".equals(name))
                return void.class;
        }

        if (classCache.containsKey(name))
            return classCache.get(name);

        Class<?> clazz = Class.forName(name.replace("/", "."));
        classCache.put(name, clazz);
        return clazz;
    }

    private static final Map<String, Duo<Field, MethodHandle>> fieldGetterCache = new HashMap<>();
    public Duo<Field, MethodHandle> getFieldGetter(Class<?> clazz, String name) throws Throwable
    {
        String key = clazz.getName() + "." + name;
        if (fieldGetterCache.containsKey(key))
            return fieldGetterCache.get(key);
        Field field = getField(clazz, name);
        field.trySetAccessible();
        MethodHandle getter = lookup.unreflectGetter(field);
        Duo<Field, MethodHandle> duo = new Duo<>(field, getter);
        fieldGetterCache.put(key, duo);
        return duo;
    }

    private static final Map<String, Duo<Field, MethodHandle>> fieldSetterCache = new HashMap<>();
    public Duo<Field, MethodHandle> getFieldSetter(Class<?> clazz, String name) throws Throwable
    {
        String key = clazz.getName() + "." + name;
        if (fieldSetterCache.containsKey(key))
            return fieldSetterCache.get(key);
        Field field = getField(clazz, name);
        field.trySetAccessible();
        MethodHandle setter = lookup.unreflectSetter(field);
        Duo<Field, MethodHandle> duo = new Duo<>(field, setter);
        fieldSetterCache.put(key, duo);
        return duo;
    }

    private static final Map<String, Field> fieldCache = new HashMap<>();
    public Field getField(Class<?> clazz, String name) throws Throwable
    {
        String path = clazz.getName() + "." + name;
        if (fieldCache.containsKey(path))
            return fieldCache.get(path);
        try
        {
            Field field = clazz.getField(name);
            fieldCache.put(path, field);
//            field.setAccessible(true);
            return field;
        }
        catch (NoSuchFieldException e)
        {
            try
            {
                Field field = clazz.getDeclaredField(name);
                fieldCache.put(path, field);
//                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored)
            {
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null)
                    try {
                        Field field = getField(superClass, name);
                        if (field != null)
                            return field;
                    }
                    catch (NoSuchFieldException ignored2)
                    { }
                Class<?>[] interfaces = clazz.getInterfaces();
                if (interfaces != null && interfaces.length > 0)
                    for (Class<?> inter : interfaces)
                        try {
                            Field field = getField(inter, name);
                            if (field != null)
                                return field;
                        }
                        catch (NoSuchFieldException ignored2)
                        { }
                throw e;
            }
        }
    }

    private static final Map<String, MethodHandle> methodHandleCache = new HashMap<>();
    public MethodHandle getMethodHandle(Class<?> clazz, String name, Class<?>[] argumentTypes) throws Throwable
    {
        String key = clazz.getName() + "." + name + "." + Arrays.toString(argumentTypes);
        if (methodHandleCache.containsKey(key))
            return methodHandleCache.get(key);
        Method method = getMethod(clazz, name, argumentTypes);
        method.trySetAccessible();
        MethodHandle mh = lookup.unreflect(method);
        methodHandleCache.put(key, mh);
        return mh;
    }

    private static final Map<String, MethodHandle> methodSpecialCache = new HashMap<>();
    public MethodHandle getSpecialHandle(Class<?> clazz, String name, Class<?> returnType, Class<?>[] argumentTypes, Class<?> valueClass) throws Throwable
    {
        String key = clazz.getName() + "." + name + "." + returnType.getName() + "." + Arrays.toString(argumentTypes) + "." + (valueClass == null ? "null" : valueClass.getName());
        if (methodSpecialCache.containsKey(key))
            return methodSpecialCache.get(key);
        MethodHandle special = lookup.findSpecial(clazz, name, MethodType.methodType(returnType, argumentTypes), valueClass);
        methodSpecialCache.put(key, special);
        return special;
    }

    private static final Map<String, Method> methodCache = new HashMap<>();
    public Method getMethod(Class<?> clazz, String name, Class<?>... argumentTypes) throws Throwable
    {
        String key = clazz.getName() + "." + name + "." + Arrays.toString(argumentTypes);
        if (methodCache.containsKey(key))
            return methodCache.get(key);
        try
        {
            Method method = clazz.getMethod(name, argumentTypes);
//            method.setAccessible(true);
            methodCache.put(key, method);
            return method;
        }
        catch (NoSuchMethodException e)
        {
            try
            {
                Method method = clazz.getDeclaredMethod(name, argumentTypes);
//                method.setAccessible(true);
                methodCache.put(key, method);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null)
                    try {
                        Method method = getMethod(superClass, name, argumentTypes);
                        if (method != null)
                            return method;
                    }
                    catch (NoSuchMethodException ignored2)
                    { }
                Class<?>[] interfaces = clazz.getInterfaces();
                if (interfaces != null && interfaces.length > 0)
                    for (Class<?> inter : interfaces)
                        try {
                            Method method = getMethod(inter, name, argumentTypes);
                            if (method != null)
                                return method;
                        }
                        catch (NoSuchMethodException ignored2)
                        { }
                throw e;
            }
        }
    }

    private static final Map<String, Constructor<?>> constructorCache = new HashMap<>();
    public Constructor<?> getConstructor(Class<?> clazz, Class<?>[] argumentTypes) throws Throwable
    {
        String key = clazz.getName() + "." + Arrays.toString(argumentTypes);
        if (constructorCache.containsKey(key))
            return constructorCache.get(key);
        try
        {
            Constructor<?> constructor = clazz.getConstructor(argumentTypes);
            constructor.setAccessible(true);
            constructorCache.put(key, constructor);
            return constructor;
        }
        catch (NoSuchMethodException e)
        {
            try
            {
                Constructor<?> constructor = clazz.getDeclaredConstructor(argumentTypes);
                constructor.setAccessible(true);
                constructorCache.put(key, constructor);
                return constructor;
            }
            catch (NoSuchMethodException ignored)
            {
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null)
                    try {
                        Constructor<?> constructor = getConstructor(superClass, argumentTypes);
                        if (constructor != null)
                            return constructor;
                    }
                    catch (NoSuchMethodException ignored2)
                    { }
                Class<?>[] interfaces = clazz.getInterfaces();
                if (interfaces != null && interfaces.length > 0)
                    for (Class<?> inter : interfaces)
                        try {
                            Constructor<?> constructor = getConstructor(inter, argumentTypes);
                            if (constructor != null)
                                return constructor;
                        }
                        catch (NoSuchMethodException ignored2)
                        { }
                throw e;
            }
        }
    }

    public Object[] deepClone(Object[] inputs, Object... obj)
    {
        final Object[] cloned = new Object[obj.length];
        final boolean inputsIsNull = inputs == null;
        for (int i = 0; i < cloned.length; i++)
        {
            Object original = obj[i];
            // If the original is not set, or if the original has changed, deep-clone!
            if (inputsIsNull || inputs[i] != original)
                cloned[i] = deepClone(original);
            else
                cloned[i] = original; // Else, original has not changed (it's the same), so re-use it
        }
        return cloned;
    }

    private Object deepClone(Object original)
    {
        if(original instanceof String str) return str;
        else if(original instanceof Integer k) return k;
        else if(original instanceof Long l) return l;
        else if(original instanceof Float f) return f;
        else if(original instanceof Double d) return d;
        else if(original instanceof Short s) return s;
        else if(original instanceof Byte b) return b;
        else if(original instanceof Boolean z) return z;
        else if(original instanceof VMType type) return new VMType(type.returnType, type.argumentTypes);
        else if(original instanceof VMHandle handle) return new VMHandle(handle.tag, handle.owner, handle.name, handle.returnType, handle.argumentTypes);
        else if(original instanceof Object[] objArray) return deepClone(null, objArray);
        else if(original instanceof int[] a) return a.clone();
        else throw new IllegalStateException("Unexpected value: " + original);
    }

    public Object cast(Object pop, Class<?> argumentClass)
    {
        if (pop == null) return pop;
        return cast(pop, pop.getClass(), argumentClass);
    }

    public Object cast(Object pop, Class<?> popClass, Class<?> argumentClass)
    {
        boolean didCast = true;
        // Convert Boolean to X
        if (popClass == Boolean.class)
        {
            Boolean b = (Boolean) pop;
            if (argumentClass == boolean.class)
                pop = b.booleanValue();
            else
            if (argumentClass == byte.class)
                pop = b.booleanValue() ? (byte)1 : (byte)0;
            else
            if (argumentClass == char.class)
                pop = b.booleanValue() ? (char)1 : (char)0;
            else
            if (argumentClass == short.class)
                pop = b.booleanValue() ? (short)1 : (short)0;
            else
            if (argumentClass == int.class)
                pop = b.booleanValue() ? 1 : 0;
            else
            if (argumentClass == long.class)
                pop = b.booleanValue() ? (long)1 : (long)0;
            else
            if (argumentClass == float.class)
                pop = b.booleanValue() ? (float)1 : (float)0;
            else
            if (argumentClass == double.class)
                pop = b.booleanValue() ? (double)1 : (double)0;
            else
                didCast = false;
        }
        // Convert Byte to X
        else if (popClass == Byte.class)
        {
            Byte b = (Byte) pop;
            if (argumentClass == boolean.class)
                pop = b.intValue() == 1;
            else
            if (argumentClass == byte.class)
                pop = b.byteValue();
            else
            if (argumentClass == char.class)
                pop = (char)b.byteValue();
            else
            if (argumentClass == short.class)
                pop = b.shortValue();
            else
            if (argumentClass == int.class)
                pop = b.intValue();
            else
            if (argumentClass == long.class)
                pop = b.longValue();
            else
            if (argumentClass == float.class)
                pop = b.floatValue();
            else
            if (argumentClass == double.class)
                pop = b.doubleValue();
            else
                didCast = false;
        }
        // Convert Character to X
        else if (popClass == Character.class)
        {
            Character c = (Character) pop;
            char val = c.charValue();
            if (argumentClass == boolean.class)
                pop = val == 1;
            else
            if (argumentClass == byte.class)
                pop = (byte)val;
            else
            if (argumentClass == char.class)
                pop = val;
            else
            if (argumentClass == short.class)
                pop = (short)val;
            else
            if (argumentClass == int.class)
                pop = (int)val;
            else
            if (argumentClass == long.class)
                pop = (long)val;
            else
            if (argumentClass == float.class)
                pop = (float)val;
            else
            if (argumentClass == double.class)
                pop = (double)val;
            else
                didCast = false;
        }
        // Convert Short to X
        else if (popClass == Short.class)
        {
            Short s = (Short) pop;
            if (argumentClass == boolean.class)
                pop = s.shortValue() == 1;
            else
            if (argumentClass == byte.class)
                pop = s.byteValue();
            else
            if (argumentClass == char.class)
                pop = (char)s.shortValue();
            else
            if (argumentClass == short.class)
                pop = s.shortValue();
            else
            if (argumentClass == int.class)
                pop = s.intValue();
            else
            if (argumentClass == long.class)
                pop = s.longValue();
            else
            if (argumentClass == float.class)
                pop = s.floatValue();
            else
            if (argumentClass == double.class)
                pop = s.doubleValue();
            else
                didCast = false;
        }
        // Convert Integer to X
        else if (popClass == Integer.class)
        {
            Integer i = (Integer) pop;
            if (argumentClass == boolean.class)
                pop = i.intValue() == 1;
            else
            if (argumentClass == byte.class)
                pop = i.byteValue();
            else
            if (argumentClass == char.class)
                pop = (char)i.intValue();
            else
            if (argumentClass == short.class)
                pop = i.shortValue();
            else
            if (argumentClass == int.class)
                pop = i.intValue();
            else
            if (argumentClass == long.class)
                pop = i.longValue();
            else
            if (argumentClass == float.class)
                pop = i.floatValue();
            else
            if (argumentClass == double.class)
                pop = i.doubleValue();
            else
                didCast = false;
        }
        // Convert Long to X
        else if (popClass == Long.class)
        {
            Long l = (Long) pop;
            if (argumentClass == boolean.class)
                pop = l == 1;
            else
            if (argumentClass == byte.class)
                pop = l.byteValue();
            else
            if (argumentClass == char.class)
                pop = (char)l.intValue();
            else
            if (argumentClass == short.class)
                pop = l.shortValue();
            else
            if (argumentClass == int.class)
                pop = l.intValue();
            else
            if (argumentClass == long.class)
                pop = l.longValue();
            else
            if (argumentClass == float.class)
                pop = l.floatValue();
            else
            if (argumentClass == double.class)
                pop = l.doubleValue();
            else
                didCast = false;
        }
        // Convert Float to X
        else if (popClass == Float.class)
        {
            Float f = (Float) pop;
            if (argumentClass == boolean.class)
                pop = f == 1;
            else
            if (argumentClass == byte.class)
                pop = f.byteValue();
            else
            if (argumentClass == char.class)
                pop = (char)f.intValue();
            else
            if (argumentClass == short.class)
                pop = f.shortValue();
            else
            if (argumentClass == int.class)
                pop = f.intValue();
            else
            if (argumentClass == long.class)
                pop = f.longValue();
            else
            if (argumentClass == float.class)
                pop = f.floatValue();
            else
            if (argumentClass == double.class)
                pop = f.doubleValue();
            else
                didCast = false;
        }
        // Convert Double to X
        else if (popClass == Double.class)
        {
            Double d = (Double) pop;
            if (argumentClass == boolean.class)
                pop = d == 1;
            else
            if (argumentClass == byte.class)
                pop = d.byteValue();
            else
            if (argumentClass == char.class)
                pop = (char)d.intValue();
            else
            if (argumentClass == short.class)
                pop = d.shortValue();
            else
            if (argumentClass == int.class)
                pop = d.intValue();
            else
            if (argumentClass == long.class)
                pop = d.longValue();
            else
            if (argumentClass == float.class)
                pop = d.floatValue();
            else
            if (argumentClass == double.class)
                pop = d.doubleValue();
            else
                didCast = false;
        }
        else
        if (popClass == Object[].class)
        {
            Object[] objects = (Object[]) pop;
            if (argumentClass == String[].class)
            {
                String[] newObjects = new String[objects.length];
                for (int i = 0; i < objects.length; i++)
                {
                    Object obj = objects[i];
                    if (obj instanceof String str)
                        newObjects[i] = str;
                    else
                        throw new IllegalStateException("Unknown object type: " + obj + " (" + (obj == null ? null : obj.getClass()) + ")");
                }
                pop = newObjects;
            }
            else
                didCast = false;
        }
        else
            didCast = false;
        if (!didCast)
        {
//            System.out.println(pop + " (" + pop.getClass() + " -> " + argumentClass + ")");
            // Last attempt! #Yolo
            pop = argumentClass.cast(pop);
        }
        return pop;
    }

    public boolean isInstance(Class<?> argumentClazz, Object obj)
    {
        Class<?> objClazz = obj.getClass();
        if (argumentClazz == int.class)
            return objClazz == Integer.class;
        if (argumentClazz == byte.class)
            return objClazz == Byte.class;
        if (argumentClazz == short.class)
            return objClazz == Short.class;
        if (argumentClazz == long.class)
            return objClazz == Long.class;
        if (argumentClazz == float.class)
            return objClazz == Float.class;
        if (argumentClazz == double.class)
            return objClazz == Double.class;
        if (argumentClazz == boolean.class)
            return objClazz == Boolean.class;
        if (argumentClazz == char.class)
            return objClazz == Character.class;
        return false;
    }

    private final String[] OPCODE_NAMES =
    {
        "nop", "aconst_null", "iconst_m1", "iconst_0", "iconst_1",
        "iconst_2", "iconst_3", "iconst_4", "iconst_5", "lconst_0",
        "lconst_1", "fconst_0", "fconst_1", "fconst_2", "dconst_0",
        "dconst_1", "bipush", "sipush", "ldc", "ldc_w", "ldc2_w", "iload",
        "lload", "fload", "dload", "aload", "iload_0", "iload_1", "iload_2",
        "iload_3", "lload_0", "lload_1", "lload_2", "lload_3", "fload_0",
        "fload_1", "fload_2", "fload_3", "dload_0", "dload_1", "dload_2",
        "dload_3", "aload_0", "aload_1", "aload_2", "aload_3", "iaload",
        "laload", "faload", "daload", "aaload", "baload", "caload", "saload",
        "istore", "lstore", "fstore", "dstore", "astore", "istore_0",
        "istore_1", "istore_2", "istore_3", "lstore_0", "lstore_1",
        "lstore_2", "lstore_3", "fstore_0", "fstore_1", "fstore_2",
        "fstore_3", "dstore_0", "dstore_1", "dstore_2", "dstore_3",
        "astore_0", "astore_1", "astore_2", "astore_3", "iastore", "lastore",
        "fastore", "dastore", "aastore", "bastore", "castore", "sastore",
        "pop", "pop2", "dup", "dup_x1", "dup_x2", "dup2", "dup2_x1",
        "dup2_x2", "swap", "iadd", "ladd", "fadd", "dadd", "isub", "lsub",
        "fsub", "dsub", "imul", "lmul", "fmul", "dmul", "idiv", "ldiv",
        "fdiv", "ddiv", "irem", "lrem", "frem", "drem", "ineg", "lneg",
        "fneg", "dneg", "ishl", "lshl", "ishr", "lshr", "iushr", "lushr",
        "iand", "land", "ior", "lor", "ixor", "lxor", "iinc", "i2l", "i2f",
        "i2d", "l2i", "l2f", "l2d", "f2i", "f2l", "f2d", "d2i", "d2l", "d2f",
        "i2b", "i2c", "i2s", "lcmp", "fcmpl", "fcmpg",
        "dcmpl", "dcmpg", "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle",
        "if_icmpeq", "if_icmpne", "if_icmplt", "if_icmpge", "if_icmpgt",
        "if_icmple", "if_acmpeq", "if_acmpne", "goto", "jsr", "ret",
        "tableswitch", "lookupswitch", "ireturn", "lreturn", "freturn",
        "dreturn", "areturn", "return", "getstatic", "putstatic", "getfield",
        "putfield", "invokevirtual", "invokespecial", "invokestatic",
        "invokeinterface", "invokedynamic", "new", "newarray", "anewarray",
        "arraylength", "athrow", "checkcast", "instanceof", "monitorenter",
        "monitorexit", "wide", "multianewarray", "ifnull", "ifnonnull",
        "goto_w", "jsr_w", "breakpoint"
    };
    public String getOpcodeName(final int index)
    {
        if (index == -1) return "-1";
        return OPCODE_NAMES[index];
    }

    public boolean isWide(Object value) {
        return value instanceof Long || value instanceof Double;
    }

    public static class Duo<A, B>
    {
        public A a;
        public B b;
        public Duo(A a, B b)
        {
            this.a = a;
            this.b = b;
        }
    }

    public static class Trio<A, B, C>
    {
        public A a;
        public B b;
        public C c;
        public Trio(A a, B b, C c)
        {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    public static class Quad<A, B, C, D>
    {
        public A a;
        public B b;
        public C c;
        public D d;
        public Quad(A a, B b, C c, D d)
        {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }
    }

}
