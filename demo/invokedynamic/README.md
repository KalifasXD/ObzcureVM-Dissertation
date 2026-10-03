# `invokedynamic` support in ObzcureVM (Task 4)

`invokedynamic` is the JVM instruction behind lambdas, method references and (since
Java 9) string concatenation. This folder documents how ObzcureVM virtualizes it:
first the original boundary, then the generic support that removes it.

## Original boundary (hand-coded whitelist)

ObzcureVM used to handle `invokedynamic` only for a **fixed set** of bootstrap
results:

- lambdas whose functional interface is one of: `Runnable`,
  `Consumer` / `IntConsumer` / `LongConsumer` / `DoubleConsumer`, `Function`,
  `Predicate`, `Supplier`;
- `java.lang.String` concatenation via `StringConcatFactory`.

Anything else hit `default: return false` in
`src/main/java/obzcu/re/vm/translator/TranslateInvokeDynamics.java`, and the
translator aborted at build time:

```
Translator.java:
    if (!translateInvokeDynamics.translate(idin))
        throw new IllegalStateException(
            "Invokedynamic instruction has unsupported arguments in: "
            + className + " " + methodName + methodDesc);
```

Running `run_generic_indy.sh` (below) against a **pre-generalization** build
reproduces this boundary: the `Comparator`, `BiFunction`, custom-interface and
method-reference methods in `GenericIndy.java` abort virtualization, and the script
reports that the jar lacks generic support.

## Generic support (bootstrap replay)

The whitelist is now gone. `TranslateInvokeDynamics.translate()` routes every
`invokedynamic` through a generic handler that serializes the call site faithfully:
its name and `MethodType`, the bootstrap method handle, and all bootstrap static
arguments. At runtime `VMInvokeDynamicInsnNode` rebuilds those and **replays the real
bootstrap method** - it calls the bootstrap to get a `java.lang.invoke.CallSite`,
takes `callSite.dynamicInvoker()`, pops the captured arguments off the VM stack,
invokes, and pushes the result. This is exactly how the JVM itself links
`invokedynamic`, so any standard functional interface, method reference or string
concatenation virtualizes.

This works because the virtualizer injects `MethodHandles.lookup()` into the host
class's static initializer and hands that lookup to the VM, so the bootstrap
(`LambdaMetafactory` / `StringConcatFactory`) has the same access to the private
synthetic lambda bodies that it would in the original program.

Residual: hand-written `ConstantDynamic` bootstraps are not serialized this way; such
a class still fails cleanly at build time. Normal Java source never produces them.

## Reproduce

`GenericIndy.java` exercises `Supplier` and `Function` (previously supported) together
with `Comparator`, `BiFunction`, a custom `@FunctionalInterface`, a method reference
and string concatenation (previously rejected). Every method is tagged for
virtualization; `main` prints each result for a range of inputs.

```bash
bash demo/invokedynamic/run_generic_indy.sh [path/to/virtualizer.jar]
```

The script compiles and runs `GenericIndy` un-virtualized (baseline), virtualizes it
(which must no longer abort), and runs the virtualized jar to diff the output against
the baseline. It needs a JDK (17 or 18) and a built virtualizer jar; the jar defaults
to `target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar` and can be passed as
an argument or via `OBZCURE_JAR`. The final differential-test step runs the protected
program offline and therefore needs a build that accepts the developer seed override
(`-Dobzcure.seed`); a release build expects the license server, and the script reports
that and skips the run step (virtualization itself still succeeds).
