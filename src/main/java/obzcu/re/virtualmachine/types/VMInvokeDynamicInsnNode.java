package obzcu.re.virtualmachine.types;

import obzcu.re.virtualmachine.VMStack;
import obzcu.re.virtualmachine.ObzcureVM;
import obzcu.re.virtualmachine.asm.VMHandle;
import obzcu.re.virtualmachine.asm.VMType;
import obzcu.re.virtualmachine.types.invokedynamics.*;


import java.lang.invoke.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

/**
 * @author HoverCatz
 * @created 09.01.2022
 * @url https://github.com/HoverCatz
 **/
public class VMInvokeDynamicInsnNode extends VMNode
{

    public VMInvokeDynamicInsnNode(int opcode)
    {
        super(opcode);
    }

    @Override
    public void execute(ObzcureVM vm, VMStack stack) throws Throwable
    {
        super.execute(vm, stack);

        if (vm.debug) System.out.println("VMInvokeDynamicsInsnNode execute");

        MethodHandles.Lookup lookup = vm.getMethodHandlesLookup();
        if (lookup == null)
            throw new IllegalStateException("MethodHandles.Lookup not found! Can't proceed.");

        String which = getNextString();
        if (vm.debug)
            System.out.println("!!! which: " + which);
        switch (which)
        {
            case "StringConcatFactory": prepareStringConcat(vm, stack, lookup); break;
            case "Runnable": prepareRunnable(vm, stack, lookup); break;

            // Consumers
            case "Consumer": prepareConsumer(vm, stack, lookup, Object.class.getName()); break;
            case "IntConsumer": prepareConsumer(vm, stack, lookup, Integer.class.getName()); break;
            case "LongConsumer": prepareConsumer(vm, stack, lookup, Long.class.getName()); break;
            case "DoubleConsumer": prepareConsumer(vm, stack, lookup, Double.class.getName()); break;

            // Others
            case "Function": prepareFunction(vm, stack, lookup); break;
            case "Predicate": preparePredicate(vm, stack, lookup); break;
            case "Supplier": prepareSupplier(vm, stack, lookup); break;

            // Generic path: replay the real bootstrap for any other invokedynamic.
            case "Generic": prepareGeneric(vm, stack, lookup); break;

            default: throw new RuntimeException("Invalid invokedynamics type: " + which);
        }

    }

    private void prepareStringConcat(ObzcureVM vm, VMStack stack, MethodHandles.Lookup lookup) throws Throwable
    {
        String string = getNextString();

        Object o = getNext();
        o = vm.cast(o, o.getClass(), String[].class);
        String[] argumentStrings = (String[]) o;
        Class<?>[] classes = getArgumentClasses(vm, argumentStrings);

        int index = -1, found = 0;
        while (true)
        {
            index = string.indexOf('\u0001', index);
            if (index == -1) break;
            found++;
            index++;
        }

        if (found != classes.length)
            throw new IllegalStateException("Unexpected found != classes.length. " +
                    found + " != " + classes.length);

        Object[] objects = new Object[found];
        for (int i = found - 1; i >= 0; i--)
        {
            Object pop = stack.pop();
            objects[i] = vm.cast(pop, pop.getClass(), classes[i]);
        }

        StringBuilder sb = new StringBuilder();

        int n = 0;
        for (int i = 0; i < string.length(); i++)
        {
            char c = string.charAt(i);
            if (c == '\u0001') sb.append(objects[n++]);
            else sb.append(c);
        }

        stack.push(sb.toString());
    }

    private void prepareSupplier(ObzcureVM vm, VMStack stack, MethodHandles.Lookup lookup) throws Throwable
    {
        int tag = getNextInt();
        if (vm.debug)
            System.out.println("!!! tag: " + tag);

        String owner = getNextString();
        String name = getNextString();

        String[] args = (String[]) getNext();
        Class<?>[] argsClasses = getArgumentClasses(vm, args);

        String[] arguments = (String[]) getNext();
        Class<?>[] argumentClasses = getArgumentClasses(vm, arguments);

        Class<?> clazz = vm.getClass(owner);
        if (clazz == null)
            throw new IllegalStateException("Invokedynamics owner class not found.");

//        final Method method = vm.getMethod(clazz, name, argsClasses);
//        if (method == null)
//            throw new IllegalStateException("Invokedynamics method not found.");
//
//        if (!method.trySetAccessible() && vm.debug)
//            System.err.println("Couldn't set method accessible. This may cause the method call to fail.");

        final MethodHandle mh = vm.getMethodHandle(clazz, name, argsClasses);
        if (mh == null)
            throw new IllegalStateException("Invokedynamics method not found.");

        if (vm.debug)
        {
            System.out.println("!!! stack: " + stack);
            System.out.println("!!! inputs3: " + Arrays.toString(args));
            System.out.println("!!! inputs4: " + Arrays.toString(arguments));
            System.out.println("!!! mh: " + mh);
        }

        Object[] popped = new Object[argumentClasses.length];
        for (int i = popped.length - 1; i >= 0; i--)
            popped[i] = stack.pop();

        if (vm.debug)
            System.out.println("!!! popped: " + Arrays.toString(popped));

        VMSupplier s = new VMSupplier(() ->
        {
            int argsCount = popped.length;
            try
            {
                // Supplier<o>#get returns T
                Object[] objects = new Object[argsCount + 1];
                if (popped.length > 0)
                    System.arraycopy(popped, 0, objects, 0, popped.length);
                if (vm.debug)
                {
                    System.out.println("!!! objects.length: " + objects.length);
                    System.out.println("objects: " + Arrays.stream(objects).map(o2 -> (o2 == null ? null : o2.getClass().getSimpleName())).collect(Collectors.toList()));
                }
                return mh.invokeWithArguments(objects);
            }
            catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        });
        stack.push(s);
    }

    private void preparePredicate(ObzcureVM vm, VMStack stack, MethodHandles.Lookup lookup) throws Throwable
    {
        int tag = getNextInt();
        if (vm.debug)
            System.out.println("!!! tag: " + tag);

        String owner = getNextString();
        String name = getNextString();

        String[] args = (String[]) getNext();
        Class<?>[] argsClasses = getArgumentClasses(vm, args);

        String[] arguments = (String[]) getNext();
        Class<?>[] argumentClasses = getArgumentClasses(vm, arguments);

        Class<?> clazz = vm.getClass(owner);
        if (clazz == null)
            throw new IllegalStateException("Invokedynamics owner class not found.");

//        final Method method = vm.getMethod(clazz, name, argsClasses);
//        if (method == null)
//            throw new IllegalStateException("Invokedynamics method not found.");
//
//        if (!method.trySetAccessible() && vm.debug)
//            System.err.println("Couldn't set method accessible. This may cause the method call to fail.");

        final MethodHandle mh = vm.getMethodHandle(clazz, name, argsClasses);
        if (mh == null)
            throw new IllegalStateException("Invokedynamics method not found.");

        if (vm.debug)
        {
            System.out.println("!!! stack: " + stack);
            System.out.println("!!! inputs3: " + Arrays.toString(args));
            System.out.println("!!! inputs4: " + Arrays.toString(arguments));
            System.out.println("!!! mh: " + mh);
        }

        Object[] popped = new Object[argumentClasses.length];
        for (int i = popped.length - 1; i >= 0; i--)
            popped[i] = stack.pop();

        if (vm.debug)
            System.out.println("!!! popped: " + Arrays.toString(popped));

        VMPredicate p = new VMPredicate(o ->
        {
            int argsCount = popped.length;
            try
            {
                // Predicate<o>#test returns boolean
                Object[] objects = new Object[argsCount + 1];
                objects[argsCount] = o;
                if (popped.length > 0)
                    System.arraycopy(popped, 0, objects, 0, popped.length);
                if (vm.debug)
                {
                    System.out.println("!!! objects.length: " + objects.length);
                    System.out.println("objects: " + Arrays.stream(objects).map(o2 -> (o2 == null ? null : o2.getClass().getSimpleName())).collect(Collectors.toList()));
                }
                return (boolean) mh.invokeWithArguments(objects);
            }
            catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        });
        stack.push(p);
    }

    private void prepareFunction(ObzcureVM vm, VMStack stack, MethodHandles.Lookup lookup) throws Throwable
    {
        int tag = getNextInt();
        if (vm.debug)
            System.out.println("!!! tag: " + tag);

        String owner = getNextString();
        String name = getNextString();

        String[] args = (String[]) getNext();
        Class<?>[] argsClasses = getArgumentClasses(vm, args);

        String[] arguments = (String[]) getNext();
        Class<?>[] argumentClasses = getArgumentClasses(vm, arguments);

        Class<?> clazz = vm.getClass(owner);
        if (clazz == null)
            throw new IllegalStateException("Invokedynamics owner class not found.");

//        final Method method = vm.getMethod(clazz, name, argsClasses);
//        if (method == null)
//            throw new IllegalStateException("Invokedynamics method not found.");
//
//        if (!method.trySetAccessible() && vm.debug)
//            System.err.println("Couldn't set method accessible. This may cause the method call to fail.");

        final MethodHandle mh = vm.getMethodHandle(clazz, name, argsClasses);
        if (mh == null)
            throw new IllegalStateException("Invokedynamics method not found.");

        if (vm.debug)
        {
            System.out.println("!!! stack: " + stack);
            System.out.println("!!! inputs3: " + Arrays.toString(args));
            System.out.println("!!! inputs4: " + Arrays.toString(arguments));
            System.out.println("!!! mh: " + mh);
        }

        Object[] popped = new Object[argumentClasses.length];
        for (int i = popped.length - 1; i >= 0; i--)
            popped[i] = stack.pop();

        if (vm.debug)
            System.out.println("!!! popped: " + Arrays.toString(popped));

        VMFunction f = new VMFunction(o ->
        {
            int argsCount = popped.length;
            try
            {
                // Function<T, R>#apply returns R
                Object[] objects = new Object[argsCount + 1];
                objects[argsCount] = o;
                if (popped.length > 0)
                    System.arraycopy(popped, 0, objects, 0, popped.length);
                if (vm.debug)
                {
                    System.out.println("!!! objects.length: " + objects.length);
                    System.out.println("objects: " + Arrays.stream(objects).map(o2 -> (o2 == null ? null : o2.getClass().getSimpleName())).collect(Collectors.toList()));
                }
                return mh.invokeWithArguments(objects);
            }
            catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        });
        stack.push(f);
    }

    private void prepareRunnable(ObzcureVM vm, VMStack stack, MethodHandles.Lookup lookup) throws Throwable
    {
        int tag = getNextInt();
        if (vm.debug)
            System.out.println("!!! tag: " + tag);

        String owner = getNextString();
        String name = getNextString();

        String[] args = (String[]) getNext();
        Class<?>[] argsClasses = getArgumentClasses(vm, args);

        String[] arguments = (String[]) getNext();
        if (tag == 5)
            arguments = Arrays.stream(Arrays.copyOfRange(arguments, 1, arguments.length)).toArray(String[]::new);
        Class<?>[] argumentClasses = getArgumentClasses(vm, arguments);

        Class<?> clazz = vm.getClass(owner);
        if (clazz == null)
            throw new IllegalStateException("Invokedynamics owner class not found.");

//        final Method method = vm.getMethod(clazz, name, argumentClasses);
//        if (method == null)
//            throw new IllegalStateException("Invokedynamics method not found.");
//
//        if (!method.trySetAccessible() && vm.debug)
//            System.err.println("Couldn't set method accessible. This may cause the method call to fail.");

        final MethodHandle mh = vm.getMethodHandle(clazz, name, argumentClasses);
        if (mh == null)
            throw new IllegalStateException("Invokedynamics method not found.");

        if (vm.debug)
        {
            System.out.println("!!! stack: " + stack);
            System.out.println("!!! inputs3: " + Arrays.toString(args));
            System.out.println("!!! inputs4: " + Arrays.toString(arguments));
            System.out.println("!!! mh: " + mh);
        }

        Object ref = tag == 5 ? stack.pop() : null;

        Object[] popped = new Object[argumentClasses.length];
        for (int i = popped.length - 1; i >= 0; i--)
            popped[i] = stack.pop();

        if (vm.debug)
            System.out.println("!!! popped: " + Arrays.toString(popped));

        VMRunnable r = new VMRunnable(() ->
        {
            try
            {
                // Runnable#run returns void
                if (vm.debug)
                {
                    System.out.println("!!! popped.length: " + popped.length);
                    System.out.println("popped: " + Arrays.stream(popped).map(o2 -> (o2 == null ? null : o2.getClass().getSimpleName())).collect(Collectors.toList()));
                }
                MethodHandle _mh = mh;
                if (tag == 5)
                    _mh = _mh.bindTo(ref);
                if (popped.length == 0)
                    _mh.invoke();
                else
                    _mh.invokeWithArguments(popped);
            }
            catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        });
        stack.push(r);
    }

    private void prepareConsumer(ObzcureVM vm, VMStack stack, MethodHandles.Lookup lookup, String type) throws Throwable
    {
        int tag = getNextInt();
        if (vm.debug)
            System.out.println("!!! tag: " + tag);

        String owner = getNextString();
        String name = getNextString();

        String[] args = (String[]) getNext();
        Class<?>[] argsClasses = getArgumentClasses(vm, args);

        String[] arguments = (String[]) getNext();
        Class<?>[] argumentClasses = getArgumentClasses(vm, arguments);

        Class<?> clazz = vm.getClass(owner);
        if (clazz == null)
            throw new IllegalStateException("Invokedynamics owner class not found.");

//        final Method method = vm.getMethod(clazz, name, argsClasses);
//        if (method == null)
//            throw new IllegalStateException("Invokedynamics method not found.");
//
//        if (!method.trySetAccessible() && vm.debug)
//            System.err.println("Couldn't set method accessible. This may cause the method call to fail.");

        final MethodHandle mh = vm.getMethodHandle(clazz, name, argsClasses);
        if (mh == null)
            throw new IllegalStateException("Invokedynamics method not found.");

        if (vm.debug)
        {
            System.out.println("!!! type: " + type);
            System.out.println("!!! tag: " + (tag == 5 ? "H_INVOKEVIRTUAL" : "H_INVOKESTATIC"));
            System.out.println("!!! stack: " + stack);
            System.out.println("!!! inputs3: " + Arrays.toString(args));
            System.out.println("!!! inputs4: " + Arrays.toString(arguments));
            System.out.println("!!! mh: " + mh);
        }

        Object[] popped = new Object[argumentClasses.length];
        for (int i = popped.length - 1; i >= 0; i--)
            popped[i] = stack.pop();
        if (vm.debug)
            System.out.println("!!! popped: " + Arrays.toString(popped));

        Class<?> typeClass = vm.getClass(type);

        Consumer<Object> consumer = (Consumer<Object>) arg ->
        {
            int argsCount = popped != null ? popped.length : 0;
            try
            {
                arg = vm.cast(arg, typeClass);
                // Consumer#accept returns void
                if (argsCount <= 0)
                    mh.invoke(arg);
                else
                {
                    Object[] objects = new Object[argsCount + 1];
                    objects[argsCount] = arg;
                    if (popped.length > 0)
                        System.arraycopy(popped, 0, objects, 0, popped.length);
                    if (vm.debug)
                    {
                        System.out.println("!!! objects.length: " + objects.length);
                        System.out.println("objects: " + Arrays.stream(objects).map(o2 -> (o2 == null ? null : o2.getClass().getSimpleName())).collect(Collectors.toList()));
                    }
                    mh.invokeWithArguments(objects);
                }
            }
            catch (Throwable t)
            {
                throw new RuntimeException(t);
            }
        };

        Object c;
        switch (type)
        {
            case "java.lang.Object": c = consumer; break;
            case "java.lang.Integer": c = (IntConsumer) consumer::accept; break;
            case "java.lang.Long": c = (LongConsumer) consumer::accept; break;
            case "java.lang.Double": c = (DoubleConsumer) consumer::accept; break;
            default: throw new IllegalStateException("Unexpected value: " + type);
        };

        stack.push(c);
    }

    /**
     * Generic invokedynamic linkage. This replays exactly what the JVM does for an
     * invokedynamic instruction: reconstruct the bootstrap method handle and its
     * static arguments, invoke the bootstrap to obtain a {@link CallSite}, then
     * call the call site's dynamic invoker with the captured (dynamic) arguments
     * popped from the VM stack.
     *
     * Because the lookup injected into the host class is a full-privilege lookup of
     * that class, bootstraps such as LambdaMetafactory can access the private
     * synthetic lambda bodies just as they would in the original program, so any
     * standard functional interface or string-concatenation site links correctly.
     */
    private void prepareGeneric(ObzcureVM vm, VMStack stack, MethodHandles.Lookup lookup) throws Throwable
    {
        String name = getNextString();
        String desc = getNextString();
        int bsmTag = getNextInt();
        String bsmOwner = getNextString();
        String bsmName = getNextString();
        String bsmDesc = getNextString();
        boolean bsmItf = getNextBoolean(); // kept for completeness; not needed to relink
        Object[] rawConsts = (Object[]) getNext();

        ClassLoader loader = lookup.lookupClass().getClassLoader();

        MethodType callSiteType = MethodType.fromMethodDescriptorString(desc, loader);
        MethodHandle bsm = makeMethodHandle(lookup, bsmTag, bsmOwner, bsmName, bsmDesc, loader);

        Object[] staticArgs = new Object[rawConsts.length];
        for (int i = 0; i < staticArgs.length; i++)
            staticArgs[i] = reconstructConst(lookup, loader, (Object[]) rawConsts[i]);

        if (vm.debug)
        {
            System.out.println("!!! Generic indy: " + name + desc);
            System.out.println("!!! bsm: " + bsm);
            System.out.println("!!! staticArgs: " + Arrays.toString(staticArgs));
        }

        Object[] bootArgs = new Object[3 + staticArgs.length];
        bootArgs[0] = lookup;
        bootArgs[1] = name;
        bootArgs[2] = callSiteType;
        System.arraycopy(staticArgs, 0, bootArgs, 3, staticArgs.length);

        CallSite cs = (CallSite) bsm.invokeWithArguments(bootArgs);
        MethodHandle target = cs.dynamicInvoker();

        // The captured (dynamic) arguments live on the VM stack, deepest first.
        int n = callSiteType.parameterCount();
        Object[] dyn = new Object[n];
        for (int i = n - 1; i >= 0; i--)
            dyn[i] = stack.pop();

        if (vm.debug)
            System.out.println("!!! captured: " + Arrays.toString(dyn));

        // invokeWithArguments applies asType conversions (incl. unboxing) to match
        // the call-site type, so the popped boxed values link to primitives fine.
        Object result = target.invokeWithArguments(dyn);
        if (callSiteType.returnType() != void.class)
            stack.push(result);
    }

    /** Reconstruct a direct method handle from a serialised ASM handle. */
    private MethodHandle makeMethodHandle(MethodHandles.Lookup lookup, int tag, String ownerInternal,
                                          String name, String desc, ClassLoader loader) throws Throwable
    {
        Class<?> owner = classFromInternal(ownerInternal, loader);
        switch (tag)
        {
            case H_INVOKESTATIC:
                return lookup.findStatic(owner, name, MethodType.fromMethodDescriptorString(desc, loader));
            case H_INVOKEVIRTUAL:
            case H_INVOKEINTERFACE:
                return lookup.findVirtual(owner, name, MethodType.fromMethodDescriptorString(desc, loader));
            case H_INVOKESPECIAL:
                return lookup.findSpecial(owner, name, MethodType.fromMethodDescriptorString(desc, loader), lookup.lookupClass());
            case H_NEWINVOKESPECIAL:
                return lookup.findConstructor(owner, MethodType.fromMethodDescriptorString(desc, loader));
            case H_GETFIELD:
                return lookup.findGetter(owner, name, classFromDescriptor(desc, loader));
            case H_GETSTATIC:
                return lookup.findStaticGetter(owner, name, classFromDescriptor(desc, loader));
            case H_PUTFIELD:
                return lookup.findSetter(owner, name, classFromDescriptor(desc, loader));
            case H_PUTSTATIC:
                return lookup.findStaticSetter(owner, name, classFromDescriptor(desc, loader));
            default:
                throw new IllegalStateException("Unsupported method-handle tag: " + tag);
        }
    }

    /** Turn one tagged constant (produced by VMLoader.readConst) into a live value. */
    private Object reconstructConst(MethodHandles.Lookup lookup, ClassLoader loader, Object[] rc) throws Throwable
    {
        String t = (String) rc[0];
        switch (t)
        {
            case "I": case "J": case "F": case "D": case "S":
                return rc[1];
            case "M":
                return MethodType.fromMethodDescriptorString((String) rc[1], loader);
            case "C":
                return classFromDescriptor((String) rc[1], loader);
            case "H":
                return makeMethodHandle(lookup, (int) rc[1], (String) rc[2], (String) rc[3], (String) rc[4], loader);
            default:
                throw new IllegalStateException("Unknown bootstrap const tag: " + t);
        }
    }

    /** Resolve an internal class name (e.g. java/util/Comparator) to a Class. */
    private Class<?> classFromInternal(String internal, ClassLoader loader) throws ClassNotFoundException
    {
        return Class.forName(internal.replace('/', '.'), false, loader);
    }

    /** Resolve a field/type descriptor (e.g. I, Ljava/lang/String;, [I) to a Class. */
    private Class<?> classFromDescriptor(String desc, ClassLoader loader) throws ClassNotFoundException
    {
        switch (desc.charAt(0))
        {
            case 'I': return int.class;
            case 'J': return long.class;
            case 'F': return float.class;
            case 'D': return double.class;
            case 'S': return short.class;
            case 'B': return byte.class;
            case 'C': return char.class;
            case 'Z': return boolean.class;
            case 'V': return void.class;
            case 'L': return Class.forName(desc.substring(1, desc.length() - 1).replace('/', '.'), false, loader);
            case '[': return Class.forName(desc.replace('/', '.'), false, loader);
            default: throw new IllegalStateException("Bad type descriptor: " + desc);
        }
    }

    private Object cast(Object bsmArg, Class<?> argumentType, MethodHandles.Lookup lookup, ObzcureVM vm) throws Throwable
    {
        if (bsmArg instanceof VMType && argumentType == MethodType.class)
        {
            VMType vmType = (VMType) bsmArg;
            return MethodType.methodType(vm.getClass(vmType.returnType), getArgumentClasses(vm, vmType.argumentTypes));
        }
        else
        if (bsmArg instanceof VMHandle && argumentType == MethodHandle.class)
        {
            VMHandle vmHandle = (VMHandle) bsmArg;
            if (vmHandle.tag == H_INVOKESTATIC)
            {
                Class<?> refClass = vm.getClass(vmHandle.owner);
                Class<?> returnType = vm.getClass(vmHandle.returnType);

                MethodType methodType = MethodType.methodType(returnType, getArgumentClasses(vm, vmHandle.argumentTypes));

                MethodHandle aStatic = lookup.findStatic(refClass, vmHandle.name, methodType);
                if (aStatic != null)
                    return aStatic;
            }
        }
        return bsmArg;
    }

    private Class<?>[] getArgumentClasses(ObzcureVM vm, String[] argumentTypes) throws ClassNotFoundException
    {
        Class<?>[] classes = new Class[argumentTypes.length];
        for (int i = 0; i < classes.length; i++)
            classes[i] = vm.getClass(argumentTypes[i]);
        return classes;
    }

}
