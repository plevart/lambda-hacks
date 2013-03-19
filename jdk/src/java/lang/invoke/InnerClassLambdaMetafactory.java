/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.invoke;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

import sun.misc.SharedSecrets;
import sun.misc.Unsafe;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Lambda metafactory implementation which dynamically creates an inner-class-like class per lambda callsite.
 *
 * @see LambdaMetafactory
 */
/* package */ final class InnerClassLambdaMetafactory extends AbstractValidatingLambdaMetafactory {
    private static final int CLASSFILE_VERSION = 51;
    private static final String METHOD_DESCRIPTOR_VOID = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String NAME_MAGIC_ACCESSOR_IMPL = "java/lang/invoke/MagicLambdaImpl";
    private static final String NAME_CTOR = "<init>";

    //Serialization support
    private static final String NAME_SERIALIZED_LAMBDA = "java/lang/invoke/SerializedLambda";
    private static final String DESCR_METHOD_WRITE_REPLACE = "()Ljava/lang/Object;";
    private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
    private static final String NAME_OBJECT = "java/lang/Object";
    private static final String DESCR_CTOR_SERIALIZED_LAMBDA
            = MethodType.methodType(void.class,
                                    Class.class,
                                    int.class, String.class, String.class, String.class,
                                    int.class, String.class, String.class, String.class,
                                    String.class,
                                    Object[].class).toMethodDescriptorString();

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger(0);

    // See context values in AbstractValidatingLambdaMetafactory
    private final String implMethodClassName;        // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final String implMethodDesc;             // Type descriptor for implementation methods "(I)Ljava/lang/String;"
    private final Type[] implMethodArgumentTypes;    // ASM types for implementaion method parameters
    private final Type implMethodReturnType;         // ASM type for implementaion method return type "Ljava/lang/String;"
    private final MethodType constructorType;        // Generated class constructor type "(CC)void"
    private final String constructorDesc;            // Type descriptor for constructor "(LCC;)V"
    private final ClassWriter cw;                    // ASM class writer
    private final Type[] argTypes;                   // ASM types for the constructor arguments
    private final String[] argNames;                 // Generated names for the constructor arguments
    private final ClassLoader lambdaClassLoader;     // Defining ClassLoader for the generated class
    private final Class<?> lambdaProtectDomainClass; // The class which protection domain will be used for the generated class
    private final String lambdaClassNamePrefix;      // The name of the generated class without unique index suffix "X$$Lambda$..."
    private final Type[] instantiatedArgumentTypes;  // ASM types for the functional interface arguments

    /**
     * General meta-factory constructor, standard cases and allowing for uncommon options such as serialization.
     *
     * @param caller Stacked automatically by VM; represents a lookup context with the accessibility privileges
     *               of the caller.
     * @param invokedType Stacked automatically by VM; the signature of the invoked method, which includes the
     *                    expected static type of the returned lambda object, and the static types of the captured
     *                    arguments for the lambda.  In the event that the implementation method is an instance method,
     *                    the first argument in the invocation signature will correspond to the receiver.
     * @param samMethod The primary method in the functional interface to which the lambda or method reference is
     *                  being converted, represented as a method handle.
     * @param implMethod The implementation method which should be called (with suitable adaptation of argument
     *                   types, return types, and adjustment for captured arguments) when methods of the resulting
     *                   functional interface instance are invoked.
     * @param instantiatedMethodType The signature of the primary functional interface method after type variables
     *                               are substituted with their instantiation from the capture site
     * @param flags A bitmask containing flags that may influence the translation of this lambda expression.  Defined
     *              fields include FLAG_SERIALIZABLE.
     * @param markerInterfaces Additional interfaces which the lambda object should implement.
     * @throws ReflectiveOperationException
     * @throws LambdaConversionException If any of the meta-factory protocol invariants are violated
     */
    public InnerClassLambdaMetafactory(MethodHandles.Lookup caller,
                                       MethodType invokedType,
                                       MethodHandle samMethod,
                                       MethodHandle implMethod,
                                       MethodType instantiatedMethodType,
                                       int flags,
                                       Class<?>[] markerInterfaces)
            throws ReflectiveOperationException, LambdaConversionException {
        super(caller, invokedType, samMethod, implMethod, instantiatedMethodType, flags, markerInterfaces);
        implMethodClassName = implDefiningClass.getName().replace('.', '/');
        implMethodName = implInfo.getName();
        implMethodDesc = implMethodType.toMethodDescriptorString();
        Type implMethodAsmType = Type.getMethodType(implMethodDesc);
        implMethodArgumentTypes = implMethodAsmType.getArgumentTypes();
        implMethodReturnType = implMethodAsmType.getReturnType();
        constructorType = invokedType.changeReturnType(Void.TYPE);
        constructorDesc = constructorType.toMethodDescriptorString();
        ClassLoader classLoader;
        if (// only non-serializable proxy can be defined inside the impl method defining class
            // (because of $deserializeLambda$ method not being there)
            !isSerializable &&
            // only bother to put the proxy class inside the impl method defining class if caching it
            cacheGeneratedClass() &&
            // check whether the types referenced from proxy class are visible
            // from the impl method defining ClassLoader
            canDefineLambdaWithClassLoader(classLoader = implDefiningClass.getClassLoader())
            ) {
            lambdaClassLoader = classLoader;
            lambdaProtectDomainClass = implDefiningClass;
            lambdaClassNamePrefix = implDefiningClass.getName().replace('.', '/') +
                                    "$$Lambda" +
                                    getImplMethodNameSuffix();
        } else if (
            !isSerializable &&
            cacheGeneratedClass() &&
            // check whether the types referenced from proxy class are visible
            // from the samBase defining ClassLoader
            canDefineLambdaWithClassLoader(classLoader = samBase.getClassLoader())
            ) {
            lambdaClassLoader = classLoader;
            lambdaProtectDomainClass = samBase;
            lambdaClassNamePrefix = samBase.getName().replace('.', '/') +
                                    "$$Lambda" +
                                    getImplMethodNameSuffix();
        } else {
            lambdaClassLoader = targetClass.getClassLoader();
            lambdaProtectDomainClass = targetClass;
            lambdaClassNamePrefix = targetClass.getName().replace('.', '/') + "$$Lambda$" +
                                    samBase.getSimpleName() +
                                    getImplMethodNameSuffix();
        }
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        argTypes = Type.getArgumentTypes(constructorDesc);
        argNames = new String[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            argNames[i] = "arg$" + (i + 1);
        }
        instantiatedArgumentTypes = Type.getArgumentTypes(instantiatedMethodType.toMethodDescriptorString());
    }

    private String getImplMethodNameSuffix() {
        return implMethodName.startsWith("lambda$")
           ? ""
           : "$" + implMethodName;
    }

    private boolean canDefineLambdaWithClassLoader(ClassLoader classLoader) {
        boolean can =
                isClassVisibleByClassLoader(samBase, classLoader) &&
                isClassVisibleByClassLoader(implDefiningClass, classLoader) &&
                areClassesVisibleByClassLoader(markerInterfaces, classLoader) &&
                isClassVisibleByClassLoader(invokedType.rtype(), classLoader) &&
                areClassesVisibleByClassLoader(invokedType.ptypes(), classLoader) &&
                isClassVisibleByClassLoader(instantiatedMethodType.rtype(), classLoader) &&
                areClassesVisibleByClassLoader(instantiatedMethodType.ptypes(), classLoader);

        System.out.println("canDefineLambdaWithClassLoader: " + classLoader + " {");
        System.out.println("                 samBase: " + samBase);
        System.out.println("       implDefiningClass: " + implDefiningClass);
        System.out.println("        markerInterfaces: " + Arrays.toString(markerInterfaces));
        System.out.println("             invokedType: " + invokedType);
        System.out.println("  instantiatedMethodType: " + instantiatedMethodType);
        System.out.println("} -> " + can);

        return can;
    }

    private boolean areClassesVisibleByClassLoader(Class<?>[] klasses, ClassLoader classLoader) {
        for (Class<?> klass : klasses) {
            if (!isClassVisibleByClassLoader(klass, classLoader)) {
                return false;
            }
        }
        return true;
    }

    private boolean isClassVisibleByClassLoader(Class<?> klass, ClassLoader classLoader) {
        boolean visible = isVisible0(klass, classLoader);
//        System.out.println("is " + klass.getName() + " (loaded by " + klass.getClassLoader() + ") visible by " + classLoader + ": " + visible);
        return visible;
    }

    private boolean isVisible0(Class<?> klass, ClassLoader classLoader) {
        if (klass.isPrimitive()) {
            return true;
        }
        if (klass.isArray()) {
            return isClassVisibleByClassLoader(klass.getComponentType(), classLoader);
        }
        try {
            return klass == Class.forName(klass.getName(), false, classLoader);
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean cacheGeneratedClass() {
        // only do caching if requesting serialized proxy or if
        // implementation method is not synthetically generated "lambda$...." method
        // (the later means that a method reference is being converted into a SAM proxy)
        return isSerializable || !implMethodName.startsWith("lambda$");
    }

    /**
     * Build the CallSite. Generate a class file which implements the functional
     * interface, define the class, if there are no parameters create an instance
     * of the class which the CallSite will return, otherwise, generate handles
     * which will call the class' constructor.
     *
     * @return a CallSite, which, when invoked, will return an instance of the
     * functional interface
     * @throws ReflectiveOperationException
     * @throws LambdaConversionException If properly formed functional interface is not found
     */
    @Override
    CallSite buildCallSite() throws ReflectiveOperationException, LambdaConversionException {
        final Class<?> innerClass;
        if (cacheGeneratedClass()) {
            // lookup cached inner Class
            InnerClassKey key = new InnerClassKey(
                invokedType,
                samInfo,
                implInfo,
                instantiatedMethodType,
                isSerializable ? targetClass : null,
                markerInterfaces
            );
            ConcurrentMap<Object, Object> clPrivateMap =
                SharedSecrets.getJavaLangAccess().getPrivateMap(lambdaClassLoader);
            Class<?> clazz = (Class<?>) clPrivateMap.get(key);
            if (clazz == null) {
                clazz = spinInnerClass();
                Class<?> oldClass = (Class<?>) clPrivateMap.putIfAbsent(key, clazz);
                if (oldClass != null) {
                    clazz = oldClass;
                }
            }
            innerClass = clazz;
        } else {
            innerClass = spinInnerClass();
        }

        if (invokedType.parameterCount() == 0) {
            final Constructor[] ctrs = AccessController.doPrivileged(
                    new PrivilegedAction<Constructor[]>() {
                        @Override
                        public Constructor[] run() {
                            return innerClass.getDeclaredConstructors();
                        }
                    });
            if (ctrs.length != 1) {
                throw new ReflectiveOperationException("Expected one lambda constructor for "
                        + innerClass.getCanonicalName() + ", got " + ctrs.length);
            }
            // The lambda implementing inner class constructor is private, set
            // it accessible (by us) before creating the constant sole instance
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    ctrs[0].setAccessible(true);
                    return null;
                }
            });
            Object inst = ctrs[0].newInstance();
            return new ConstantCallSite(MethodHandles.constant(samBase, inst));
        } else {
            return new ConstantCallSite(
                    MethodHandles.Lookup.IMPL_LOOKUP
                            .findConstructor(innerClass, constructorType)
                            .asType(constructorType.changeReturnType(samBase)));
        }
    }

    /**
     * Each lambdaClassLoader maintains a cache of inner {@link Class} objects per relevant request parameters.
     * The types encountered in the request parameters are always visible from the lambdaClassLoader,
     * so the symbolic names in the key are always unique when the types are unique. The key is
     * composed of already interned strings, so it's space overhead is just the length of the backing array.
     */
    private static final class InnerClassKey {
        private final String[] parts;

        private int length(MethodType methodType) {
            return 1 + methodType.parameterCount();
        }

        private int add(int i, MethodType methodType) {
            parts[i++] = methodType.rtype().getName(); // Class.getName() already interned
            for (Class<?> ptype : methodType.ptypes()) {
                parts[i++] = ptype.getName(); // Class.getName() already interned
            }
            return i;
        }

        private int length(MethodHandleInfo methodHandleInfo) {
            return 3 + length(methodHandleInfo.getMethodType());
        }

        private int add(int i, MethodHandleInfo methodHandleInfo) {
            parts[i++] = methodHandleInfo.getDeclaringClass().getName(); // Class.getName() already interned
            parts[i++] = methodHandleInfo.getName().intern(); // already interned in j.l.r.Method.getName() but not here
            parts[i++] = MethodHandleInfo.getReferenceKindString(methodHandleInfo.getReferenceKind()); // interned constants
            i = add(i, methodHandleInfo.getMethodType());
            return i;
        }

        InnerClassKey(MethodType invokedType,
                      MethodHandleInfo samInfo,
                      MethodHandleInfo implInfo,
                      MethodType instantiatedMethodType,
                      Class<?> deserializeMethodDeclaringClass,
                      Class<?>[] markerInterfaces) {
            int len = length(implInfo) + 1 +
                    length(samInfo) + 1 +
                    length(invokedType) + 1 +
                    length(instantiatedMethodType) + 1 +
                    (deserializeMethodDeclaringClass == null ? 0 : 1) + 1 +
                    markerInterfaces.length;
            parts = new String[len];
            int i = 0;
            i = add(i, implInfo);
            parts[i++] = null; // delimiter
            i = add(i, samInfo);
            parts[i++] = null; // delimiter
            i = add(i, invokedType);
            parts[i++] = null; // delimiter
            i = add(i, instantiatedMethodType);
            parts[i++] = null; // delimiter
            if (deserializeMethodDeclaringClass != null) {
                parts[i++] = deserializeMethodDeclaringClass.getName(); // Class.getName() already interned
            }
            parts[i++] = null; // delimiter
            for (Class<?> markerInterface : markerInterfaces) {
                parts[i++] = markerInterface.getName(); // Class.getName() already interned
            }
            if (i != parts.length) {
                throw new IllegalStateException("Invalid 'parts' array length estimation");
            }
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(parts);
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null &&
                    obj.getClass() == InnerClassKey.class &&
                    Arrays.equals(((InnerClassKey) obj).parts, parts);
        }

        @Override
        public String toString() {
            return Arrays.toString(parts);
        }
    }

    /**
     * Generate a class file which implements the functional
     * interface, define and return the class.
     *
     * @return a Class which implements the functional interface
     * @throws LambdaConversionException If properly formed functional interface is not found
     */
    private Class<?> spinInnerClass() throws LambdaConversionException {
        String samName = samBase.getName().replace('.', '/');
        String lambdaClassName = lambdaClassNamePrefix + "$" + counter.incrementAndGet();
        String[] interfaces = new String[markerInterfaces.length + 1];
        interfaces[0] = samName;
        for (int i=0; i<markerInterfaces.length; i++) {
            interfaces[i+1] = markerInterfaces[i].getName().replace('.', '/');
        }

        cw.visit(CLASSFILE_VERSION, ACC_SUPER,
                 lambdaClassName, null,
                 NAME_MAGIC_ACCESSOR_IMPL, interfaces);

        // Generate final fields to be filled in by constructor
        for (int i = 0; i < argTypes.length; i++) {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, argNames[i], argTypes[i].getDescriptor(),
                                            null, null);
            fv.visitEnd();
        }

        generateConstructor(lambdaClassName);

        MethodAnalyzer ma = new MethodAnalyzer();

        // Forward the SAM method
        if (ma.getSamMethod() == null) {
            throw new LambdaConversionException(String.format("Functional interface method not found: %s", samMethodType));
        } else {
            generateForwardingMethod(ma.getSamMethod(), false, lambdaClassName);
        }

        // Forward the bridges
        // @@@ The commented-out code is temporary, pending the VM's ability to bridge all methods on request
        // @@@ Once the VM can do fail-over, uncomment the !ma.wasDefaultMethodFound() test, and emit the appropriate
        // @@@ classfile attribute to request custom bridging.  See 8002092.
        if (!ma.getMethodsToBridge().isEmpty() /* && !ma.conflictFoundBetweenDefaultAndBridge() */ ) {
            for (Method m : ma.getMethodsToBridge()) {
                generateForwardingMethod(m, true, lambdaClassName);
            }
        }

        if (isSerializable) {
            generateWriteReplace(lambdaClassName);
        }

        cw.visitEnd();

        // Define the generated class in this VM.

        final byte[] classBytes = cw.toByteArray();

        /*** Uncomment to dump the generated file
            System.out.printf("Loaded: %s (%d bytes) %n", lambdaClassName, classBytes.length);
            try (FileOutputStream fos = new FileOutputStream(lambdaClassName.replace('/', '.') + ".class")) {
                fos.write(classBytes);
            } catch (IOException ex) {
                PlatformLogger.getLogger(InnerClassLambdaMetafactory.class.getName()).severe(ex.getMessage(), ex);
            }
        ***/

        ProtectionDomain pd = (lambdaClassLoader == null)
            ? null
            : AccessController.doPrivileged(
            new PrivilegedAction<ProtectionDomain>() {
                @Override
                public ProtectionDomain run() {
                    return lambdaProtectDomainClass.getProtectionDomain();
                }
            }
        );

        System.out.println(lambdaClassLoader + ": defining class: " + lambdaClassName);

        return Unsafe.getUnsafe().defineAnonymousClass(lambdaProtectDomainClass, classBytes, new Object[0]);

//        return Unsafe.getUnsafe().defineClass(lambdaClassName, classBytes, 0, classBytes.length,
//                                                                   lambdaClassLoader, pd);
    }

    /**
     * Generate the constructor for the class
     */
    private void generateConstructor(String lambdaClassName) {
        // Generate constructor
        MethodVisitor ctor = cw.visitMethod(ACC_PRIVATE, NAME_CTOR, constructorDesc, null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, NAME_MAGIC_ACCESSOR_IMPL, NAME_CTOR, METHOD_DESCRIPTOR_VOID);
        int lvIndex = 0;
        for (int i = 0; i < argTypes.length; i++) {
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(argTypes[i].getOpcode(ILOAD), lvIndex + 1);
            lvIndex += argTypes[i].getSize();
            ctor.visitFieldInsn(PUTFIELD, lambdaClassName, argNames[i], argTypes[i].getDescriptor());
        }
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(-1, -1); // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        ctor.visitEnd();
    }

    /**
     * Generate the writeReplace method (if needed for serialization)
     */
    private void generateWriteReplace(String lambdaClassName) {
        TypeConvertingMethodAdapter mv
                = new TypeConvertingMethodAdapter(cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                                                                 NAME_METHOD_WRITE_REPLACE, DESCR_METHOD_WRITE_REPLACE,
                                                                 null, null));

        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_SERIALIZED_LAMBDA);
        mv.visitInsn(DUP);
        mv.visitLdcInsn(Type.getType(targetClass));
        mv.visitLdcInsn(samInfo.getReferenceKind());
        mv.visitLdcInsn(invokedType.returnType().getName().replace('.', '/'));
        mv.visitLdcInsn(samInfo.getName());
        mv.visitLdcInsn(samInfo.getMethodType().toMethodDescriptorString());
        mv.visitLdcInsn(implInfo.getReferenceKind());
        mv.visitLdcInsn(implInfo.getDeclaringClass().getName().replace('.', '/'));
        mv.visitLdcInsn(implInfo.getName());
        mv.visitLdcInsn(implInfo.getMethodType().toMethodDescriptorString());
        mv.visitLdcInsn(instantiatedMethodType.toMethodDescriptorString());

        mv.iconst(argTypes.length);
        mv.visitTypeInsn(ANEWARRAY, NAME_OBJECT);
        for (int i = 0; i < argTypes.length; i++) {
            mv.visitInsn(DUP);
            mv.iconst(i);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, lambdaClassName, argNames[i], argTypes[i].getDescriptor());
            mv.boxIfTypePrimitive(argTypes[i]);
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESPECIAL, NAME_SERIALIZED_LAMBDA, NAME_CTOR,
                DESCR_CTOR_SERIALIZED_LAMBDA);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(-1, -1); // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        mv.visitEnd();
    }

    /**
     * Generate a method which calls the lambda implementation method,
     * converting arguments, as needed.
     * @param m The method whose signature should be generated
     * @param isBridge True if this methods should be flagged as a bridge
     */
    private void generateForwardingMethod(Method m, boolean isBridge, String lambdaClassName) {
        Class<?>[] exceptionTypes = m.getExceptionTypes();
        String[] exceptionNames = new String[exceptionTypes.length];
        for (int i = 0; i < exceptionTypes.length; i++) {
            exceptionNames[i] = exceptionTypes[i].getName().replace('.', '/');
        }
        String methodDescriptor = Type.getMethodDescriptor(m);
        int access = isBridge? ACC_PUBLIC | ACC_BRIDGE : ACC_PUBLIC;
        MethodVisitor mv = cw.visitMethod(access, m.getName(), methodDescriptor, null, exceptionNames);
        new ForwardingMethodGenerator(mv, lambdaClassName).generate(m);
    }

    /**
     * This class generates a method body which calls the lambda implementation
     * method, converting arguments, as needed.
     */
    private class ForwardingMethodGenerator extends TypeConvertingMethodAdapter {

        private final String lambdaClassName;

        ForwardingMethodGenerator(MethodVisitor mv, String lambdaClassName) {
            super(mv);
            this.lambdaClassName = lambdaClassName;
        }

        void generate(Method m) throws InternalError {
            visitCode();

            if (implKind == MethodHandleInfo.REF_newInvokeSpecial) {
                visitTypeInsn(NEW, implMethodClassName);
                visitInsn(DUP);;
            }
            for (int i = 0; i < argTypes.length; i++) {
                visitVarInsn(ALOAD, 0);
                visitFieldInsn(GETFIELD, lambdaClassName, argNames[i], argTypes[i].getDescriptor());
            }

            convertArgumentTypes(Type.getArgumentTypes(m));

            // Invoke the method we want to forward to
            visitMethodInsn(invocationOpcode(), implMethodClassName, implMethodName, implMethodDesc);

            // Convert the return value (if any) and return it
            // Note: if adapting from non-void to void, the 'return' instruction will pop the unneeded result
            Type samReturnType = Type.getReturnType(m);
            convertType(implMethodReturnType, samReturnType, samReturnType);
            visitInsn(samReturnType.getOpcode(Opcodes.IRETURN));

            visitMaxs(-1, -1); // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
            visitEnd();
        }

        private void convertArgumentTypes(Type[] samArgumentTypes) {
            int lvIndex = 0;
            boolean samIncludesReceiver = implIsInstanceMethod && argTypes.length == 0;
            int samReceiverLength = samIncludesReceiver ? 1 : 0;
            if (samIncludesReceiver) {
                // push receiver
                Type rcvrType = samArgumentTypes[0];
                Type instantiatedRcvrType = instantiatedArgumentTypes[0];

                visitVarInsn(rcvrType.getOpcode(ILOAD), lvIndex + 1);
                lvIndex += rcvrType.getSize();
                convertType(rcvrType, Type.getType(implDefiningClass), instantiatedRcvrType);
            }
            int argOffset = implMethodArgumentTypes.length - samArgumentTypes.length;
            for (int i = samReceiverLength; i < samArgumentTypes.length; i++) {
                Type argType = samArgumentTypes[i];
                Type targetType = implMethodArgumentTypes[argOffset + i];
                Type instantiatedArgType = instantiatedArgumentTypes[i];

                visitVarInsn(argType.getOpcode(ILOAD), lvIndex + 1);
                lvIndex += argType.getSize();
                convertType(argType, targetType, instantiatedArgType);
            }
        }

        private void convertType(Type argType, Type targetType, Type functionalType) {
            convertType(argType.getDescriptor(), targetType.getDescriptor(), functionalType.getDescriptor());
        }

        private int invocationOpcode() throws InternalError {
            switch (implKind) {
                case MethodHandleInfo.REF_invokeStatic:
                    return INVOKESTATIC;
                case MethodHandleInfo.REF_newInvokeSpecial:
                    return INVOKESPECIAL;
                 case MethodHandleInfo.REF_invokeVirtual:
                    return INVOKEVIRTUAL;
                case MethodHandleInfo.REF_invokeInterface:
                    return INVOKEINTERFACE;
                case MethodHandleInfo.REF_invokeSpecial:
                    return INVOKESPECIAL;
                default:
                    throw new InternalError("Unexpected invocation kind: " + implKind);
            }
        }
    }
}
