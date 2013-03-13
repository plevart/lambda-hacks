/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import sun.misc.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;

import static test.LambdaTest.*;

public class LambdaTest {

    public static void main(String[] args) throws Exception {
        ClassLoader system = LambdaTest.class.getClassLoader();
        // module X has no dependencies (only system classes)
        ModuleLoader loaderX = new ModuleLoader(system, new ModuleLoader[0], "test.ModX");
        // module Y has no dependencies (only system classes)
        ModuleLoader loaderY = new ModuleLoader(system, new ModuleLoader[0], "test.ModY");
        // module Z depends on X and Y (and system classes)
        ModuleLoader loaderZ = new ModuleLoader(system, new ModuleLoader[]{loaderX, loaderY}, "test.ModZ");

        Class<?> modZclass = loaderZ.loadClass("test.ModZ", false);
        Runnable modZ = (Runnable) modZclass.newInstance();
        modZ.run();
    }

    static void dump(Object bean) {
        System.out.println("\n" + bean + " {");
        for (Field f : bean.getClass().getDeclaredFields()) {
            try {
                Object lambda = f.get(bean);
                System.out.printf(
                    "%20s: %-64s (%s)\n",
                    f.getName(),
                    String.valueOf(lambda),
                    String.valueOf(lambda.getClass().getClassLoader())
                );
            }
            catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            }
        }
        System.out.println("}");
    }

    static byte[] serialize(Object o) {
        ByteArrayOutputStream baos;
        try (
            ObjectOutputStream oos =
                new ObjectOutputStream(baos = new ByteArrayOutputStream())
        ) {
            oos.writeObject(o);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    static <T> T deserialize(byte[] bytes) {
        try (
            ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(bytes))
        ) {
            return (T) ois.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static class ModuleLoader extends ClassLoader {
        private final String moduleName;
        private final ModuleLoader[] dependencies;

        ModuleLoader(ClassLoader parent, ModuleLoader[] dependencies, String moduleName) {
            super(parent);
            this.dependencies = dependencies;
            this.moduleName = moduleName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            System.out.println(this + ": loading " + name);

            if (name.startsWith(moduleName)) {
                return loadModuleClass(name, resolve);
            } else {
                // not our class, 1st delegate to dependencies
                for (ModuleLoader dependency : dependencies) {
                    try {
                        return dependency.loadModuleClass(name, resolve);
                    }
                    catch (ClassNotFoundException e) {
                        // ignore
                    }
                }
                // then delegate to super (1st parent, then us...)
                return super.loadClass(name, resolve);
            }
        }

        private Class<?> loadModuleClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    c = findClass(name);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/').concat(".class");
            try (InputStream is = getResourceAsStream(path)) {
                if (is != null) {
                    byte[] bytes = IOUtils.readFully(is, -1, true);
                    return defineClass(name, bytes, 0, bytes.length);
                } else {
                    throw new ClassNotFoundException(name);
                }
            }
            catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        @Override
        public String toString() {
            return "ModuleLoader[" + moduleName + "]";
        }
    }
}

class ModX {

    interface BinOp {
        int apply(int a, int b);
    }

    static int min(int a, int b) {
        return a <= b ? a : b;
    }
}

class ModY {

    interface BinOp {
        int apply(int a, int b);
    }

    static int min(int a, int b) {
        return a <= b ? a : b;
    }
}

class ModZ implements Runnable {

    ModX.BinOp methRefXX = ModX::min;
    ModX.BinOp serMethRefXX = (ModX.BinOp & Serializable) ModX::min;
    ModX.BinOp deserMethRefXX = deserialize(serialize(serMethRefXX));

    ModY.BinOp methRefYX = ModX::min;
    ModY.BinOp serMethRefYX = (ModY.BinOp & Serializable) ModX::min;
    ModY.BinOp deserMethRefYX = deserialize(serialize(serMethRefYX));

    ModX.BinOp methRefXY = ModY::min;
    ModX.BinOp serMethRefXY = (ModX.BinOp & Serializable) ModY::min;
    ModX.BinOp deserMethRefXY = deserialize(serialize(serMethRefXY));

    ModX.BinOp lambdaX = (a, b) -> a <= b ? a : b;
    ModX.BinOp serLambdaX = (ModX.BinOp & Serializable) (a, b) -> a <= b ? a : b;
    ModX.BinOp deserLambdaX = deserialize(serialize(serLambdaX));

    @Override
    public void run() {
        dump(this);
    }
}
