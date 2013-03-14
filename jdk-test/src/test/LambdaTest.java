/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import sun.misc.IOUtils;
import sun.reflect.Reflection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;

public class LambdaTest {

    public static void main(String[] args) throws Exception {
        // "system" class-loader
        ClassLoader system = LambdaTest.class.getClassLoader();
        // module X has no dependencies (only system classes)
        ModuleLoader loaderX = new ModuleLoader(system, new ModuleLoader[0], "test.x");
        // module Y has no dependencies (only system classes)
        ModuleLoader loaderY = new ModuleLoader(system, new ModuleLoader[0], "test.y");
        // module Z depends on X and Y (and system classes)
        ModuleLoader loaderZ = new ModuleLoader(system, new ModuleLoader[]{loaderX, loaderY}, "test.z");

        Class<?> testAppClass = loaderZ.loadClass("test.z.TestApp");
        Runnable testApp = (Runnable) testAppClass.newInstance();
        testApp.run();
    }

    public static void dump(Object bean) {
        System.out.println("\n" + bean + " {");
        for (Field f : bean.getClass().getDeclaredFields()) {
            try {
                Object lambda = f.get(bean);
                System.out.printf(
                    "  %40s %20s = %-50s // %s\n",
                    f.getType().getName(),
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

    public static byte[] serialize(Object o) {
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

    public static <T> T deserialize(byte[] bytes) {
        final ClassLoader callerLoader = Reflection.getCallerClass(2).getClassLoader();
        try (
            ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(bytes)) {
                    @Override
                    protected Class<?> resolveClass(ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                        String name = desc.getName();
                        try {
                            return Class.forName(name, false, callerLoader);
                        }
                        catch (ClassNotFoundException ex) {
                            return super.resolveClass(desc);
                        }
                    }
                }
        ) {
            T res = (T) ois.readObject();
            System.out.println("deserialized: " + res + " loaded with: " + res.getClass().getClassLoader() + " latestUserDefinedLoader would be: " + sun.misc.VM.latestUserDefinedLoader());
            return res;
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
            System.out.println(this + ": initiated loading " + name);

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
            if (!name.startsWith(moduleName)) {
                throw new ClassNotFoundException(name);
            }
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
