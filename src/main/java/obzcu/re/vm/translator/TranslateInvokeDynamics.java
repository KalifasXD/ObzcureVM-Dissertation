package obzcu.re.vm.translator;

import obzcu.re.vm.utils.asm.AccessHelper;
import obzcu.re.vm.utils.asm.ClassWrapper;
import obzcu.re.vm.utils.asm.MethodWrapper;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author HoverCatz
 * @created 16.01.2022
 * @url https://github.com/HoverCatz
 **/
public class TranslateInvokeDynamics
{

    private final ClassWrapper node;
    private final AccessHelper classAccess;
    private final MethodWrapper method;
    private final AccessHelper methodAccess;
    private final DataOutputStream writer;
    private final boolean debug;
    private final boolean debugPrettyPrint;
    private final boolean doStackAnalyze;

    public TranslateInvokeDynamics(ClassWrapper node,
                                   AccessHelper classAccess,
                                   MethodWrapper method,
                                   AccessHelper methodAccess, DataOutputStream writer, boolean debug,
                                   boolean debugPrettyPrint, boolean doStackAnalyze) {

        this.node = node;
        this.classAccess = classAccess;
        this.method = method;
        this.methodAccess = methodAccess;
        this.writer = writer;
        this.debug = debug;
        this.debugPrettyPrint = debugPrettyPrint;
        this.doStackAnalyze = doStackAnalyze;
    }

    public boolean translate(InvokeDynamicInsnNode idin) throws Throwable
    {
        // Generic path for EVERY invokedynamic. Earlier versions of this class only
        // handled a hand-coded whitelist of functional interfaces (Runnable,
        // Consumer/IntConsumer/LongConsumer/DoubleConsumer, Function, Predicate,
        // Supplier) plus java.lang.String concatenation, and aborted the build for
        // anything else. injectGeneric serialises the call site faithfully and the
        // runtime replays the real bootstrap method, so arbitrary functional
        // interfaces, method references and custom bootstraps all virtualize. This
        // both widens coverage to "any invokedynamic" and is strictly more correct
        // than the old per-interface reconstruction (which mishandled some captured
        // argument shapes at runtime). The old whitelist handlers below are retained
        // only so the runtime can still read blobs produced by older builds.
        return injectGeneric(idin);
    }

    /**
     * Generic invokedynamic handler.
     *
     * Instead of hand-coding one branch per functional interface, this serialises
     * everything the JVM itself needs to link an invokedynamic call site:
     *   - the call-site name and descriptor (MethodType),
     *   - the bootstrap method handle,
     *   - the bootstrap's static arguments.
     * At runtime the VM reconstructs these and invokes the bootstrap to obtain a
     * real {@link java.lang.invoke.CallSite}, then calls its dynamic invoker. This
     * covers any standard bootstrap (LambdaMetafactory, StringConcatFactory, and
     * others) without an interface whitelist. Exotic constants such as
     * {@code ConstantDynamic} are not serialisable this way and make the method
     * return {@code false} (reported as an unsupported instruction), which keeps
     * them an explicit residual rather than silently miscompiling.
     */
    private boolean injectGeneric(InvokeDynamicInsnNode idin) throws Throwable
    {
        int opcode = idin.getOpcode();
        Handle bsm = idin.bsm;
        if (bsm == null)
            return false;

        Object[] bsmArgs = idin.bsmArgs;

        if (debug)
        {
            System.out.println("Generic invokedynamic: " + idin.name + idin.desc);
            System.out.println("  bsm: " + bsm);
            System.out.println("  bsmArgs: " + Arrays.toString(bsmArgs));
        }

        if (debug) System.out.println("VMInvokeDynamicInsnNode: " + opcode);
        writer.writeUTF("VMInvokeDynamicInsnNode");
        writer.writeInt(opcode);

        writer.writeUTF("Generic");     // which
        writer.writeUTF(idin.name);     // call-site name (functional-interface method name)
        writer.writeUTF(idin.desc);     // call-site descriptor (captured args -> interface type)

        // Bootstrap method handle
        writeHandleFields(bsm);

        // Bootstrap static arguments
        writer.writeInt(bsmArgs.length);
        for (Object arg : bsmArgs)
            if (!writeConst(arg))
                return false;

        return true;
    }

    /** Serialise an ASM {@link Handle} (tag, owner, name, desc, isInterface). */
    private void writeHandleFields(Handle h) throws Throwable
    {
        writer.writeInt(h.getTag());
        writer.writeUTF(h.getOwner());
        writer.writeUTF(h.getName());
        writer.writeUTF(h.getDesc());
        writer.writeBoolean(h.isInterface());
    }

    /**
     * Serialise a single bootstrap static argument. The constant-pool types that
     * can appear here are int/long/float/double, String, a class or method
     * {@link Type}, or a {@link Handle}. Anything else (e.g. ConstantDynamic)
     * returns false so the caller reports an unsupported instruction.
     */
    private boolean writeConst(Object arg) throws Throwable
    {
        if (arg instanceof Integer)      { writer.writeUTF("I"); writer.writeInt((Integer) arg); }
        else if (arg instanceof Long)    { writer.writeUTF("J"); writer.writeLong((Long) arg); }
        else if (arg instanceof Float)   { writer.writeUTF("F"); writer.writeFloat((Float) arg); }
        else if (arg instanceof Double)  { writer.writeUTF("D"); writer.writeDouble((Double) arg); }
        else if (arg instanceof String)  { writer.writeUTF("S"); writer.writeUTF((String) arg); }
        else if (arg instanceof Type)
        {
            Type t = (Type) arg;
            if (t.getSort() == Type.METHOD) { writer.writeUTF("M"); writer.writeUTF(t.getDescriptor()); }
            else                            { writer.writeUTF("C"); writer.writeUTF(t.getDescriptor()); }
        }
        else if (arg instanceof Handle)
        {
            writer.writeUTF("H");
            writeHandleFields((Handle) arg);
        }
        else
        {
            System.out.println("Unsupported bootstrap static argument type: " +
                    (arg == null ? "null" : arg.getClass().getName()));
            return false;
        }
        return true;
    }

    private boolean injectStringConcat(InvokeDynamicInsnNode idin) throws Throwable
    {
        int opcode = idin.getOpcode();
        String name = idin.name;
        Type returnType = Type.getReturnType(idin.desc);
        Type[] arguments = Type.getArgumentTypes(idin.desc);
        Handle bsm = idin.bsm;
        Object[] bsmArgs = idin.bsmArgs;

        if (debug)
        {
            System.out.println(name);
            System.out.println(returnType.getInternalName());
            System.out.println(Arrays.toString(arguments));
            System.out.println(bsm);
            System.out.println(bsmArgs.length);
            System.out.println(Arrays.toString(bsmArgs));
        }

        // Filter name
        if (!name.equals("makeConcatWithConstants"))
            return false;

        // Filter returnType
        if (!returnType.getInternalName().equals("java/lang/String"))
            return false;

        // Filter bsm
        if (bsm == null)
            return false;
        else
        {
            String bsmOwner = bsm.getOwner();
            // Filter bsm owner
            if (!bsmOwner.equals("java/lang/invoke/StringConcatFactory"))
                return false;

            String bsmName = bsm.getName();
            // Filter bsm name
            if (!bsmName.equals("makeConcatWithConstants"))
                return false;

            String bsmDesc = bsm.getDesc();
            // Filter bsm desc
            if (!bsmDesc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"))
                return false;
        }

        // Filter bsmArgs length
        if (bsmArgs.length != 1)
            return false;

        String string = (String) bsmArgs[0];

        if (debug) System.out.println("VMInvokeDynamicInsnNode: " + opcode);
        writer.writeUTF("VMInvokeDynamicInsnNode");
        writer.writeInt(opcode);

        if (debug) System.out.println("which: StringConcatFactory");
        writer.writeUTF("StringConcatFactory"); // which

        if (debug) System.out.println("string: " + string);
        writer.writeUTF(string);

        String[] args = getArgumentStrings(arguments);
        if (debug) System.out.println("args.length: " + args.length);
        writer.writeInt(args.length);

        for (String arg : args)
        {
            if (debug) System.out.println("\targ: " + arg);
            writer.writeUTF(arg);
        }

        return true;
    }

    private static final Map<String, String> nameMap = new HashMap<>()
    {{
        put("Runnable", "run");

        // Consumers
        put("Consumer", "accept");
        put("IntConsumer", "accept");
        put("LongConsumer", "accept");
        put("DoubleConsumer", "accept");

        // Others
        put("Function", "apply");
        put("Predicate", "test");
        put("Supplier", "get");
    }};

    private boolean inject(InvokeDynamicInsnNode idin, String which) throws Throwable
    {
        int opcode = idin.getOpcode();
        String name = idin.name;
        Type[] arguments = Type.getArgumentTypes(idin.desc);
        Handle bsm = idin.bsm;
        Object[] bsmArgs = idin.bsmArgs;

        if (debug)
        {
            System.out.println(which);
            System.out.println(name);
            System.out.println(Arrays.toString(arguments));
            System.out.println(bsm);
            System.out.println(bsmArgs.length);
            System.out.println(Arrays.toString(bsmArgs));
        }

        // Filter name
        if (!name.equals(nameMap.get(which)))
        {
            if (debug) System.out.println("Failed name filter check");
            return false;
        }

        // Filter bsm
        if (bsm == null)
        {
            if (debug) System.out.println("Failed bsm filter check");
            return false;
        }
        else
        {
            String bsmOwner = bsm.getOwner();
            // Filter bsm owner
            if (!bsmOwner.equals("java/lang/invoke/LambdaMetafactory"))
            {
                if (debug) System.out.println("Failed bsm owner check");
                return false;
            }

            String bsmName = bsm.getName();
            // Filter bsm name
            if (!bsmName.equals("metafactory"))
            {
                if (debug) System.out.println("Failed bsm name check");
                return false;
            }

            String bsmDesc = bsm.getDesc();
            // Filter bsm desc
            if (!bsmDesc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"))
            {
                if (debug) System.out.println("Failed bsm desc check");
                return false;
            }
        }

        // Filter bsmArgs count
        if (bsmArgs.length != 3)
        {
            if (debug) System.out.println("Failed bsmArgs length check");
            return false;
        }

        Object bsmArg1 = bsmArgs[1];

        // Filter arg1 type
        if (!(bsmArg1 instanceof Handle))
        {
            if (debug) System.out.println("Failed bsmArg1 handle check");
            return false;
        }
        Handle handle = (Handle) bsmArg1;

        // Filter handle tag
        // (5 = H_INVOKEVIRTUAL)
        // (6 = H_INVOKESTATIC)
        int tag = handle.getTag();
        if (tag < Opcodes.H_INVOKEVIRTUAL || tag > Opcodes.H_INVOKESTATIC)
        {
            if (debug) System.out.println("Failed bsm tag check");
            return false;
        }

        boolean doPrint = false;
        if (doPrint)
        {
            System.out.println();
            System.out.println("! inject");
            System.out.println("which: " + which);
            System.out.println("name: " + name);
            System.out.println("arguments: " + Arrays.toString(arguments));
            System.out.println("bsm: " + bsm);
            System.out.println("bsmArgsCount: " + bsmArgs.length);
            System.out.println("bsmArgs: " + Arrays.toString(bsmArgs));
            System.out.println("handle: " + handle);
            System.out.println("handle.getTag: " + tag);
            System.out.println();
        }

        if (debug) System.out.println("VMInvokeDynamicInsnNode: " + opcode);
        writer.writeUTF("VMInvokeDynamicInsnNode");
        writer.writeInt(opcode);

        if (debug) System.out.println("which: " + which);
        writer.writeUTF(which); // which

        if (debug) System.out.println("tag: " + tag);
        writer.writeInt(tag); // tag

        if (debug) System.out.println("owner: " + handle.getOwner().replace("/", "."));
        writer.writeUTF(handle.getOwner().replace("/", "."));

        if (debug) System.out.println("name: " + handle.getName());
        writer.writeUTF(handle.getName());

        String[] args = getArgumentStrings(Type.getArgumentTypes(handle.getDesc()));
        if (debug) System.out.println("args.length: " + args.length);
        writer.writeInt(args.length);

        for (String arg : args)
        {
            if (debug) System.out.println("\targ: " + arg);
            writer.writeUTF(arg);
        }

        String[] argumentStrings = getArgumentStrings(arguments);
        if (debug) System.out.println("arguments.length: " + argumentStrings.length);
        writer.writeInt(argumentStrings.length);

        for (String arg : argumentStrings)
        {
            if (debug) System.out.println("\targ: " + arg);
            writer.writeUTF(arg);
        }

        return true;
    }

    private String[] getArgumentStrings(Type[] argumentTypes)
    {
        String[] strings = new String[argumentTypes.length];
        for (int i = 0; i < strings.length; i++)
            strings[i] = argumentTypes[i].getInternalName().replace("/", ".");
        return strings;
    }

}
