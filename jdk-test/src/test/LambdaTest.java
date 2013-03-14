/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import sun.misc.IOUtils;

import java.io.IOException;
import java.io.InputStream;

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

    static class ModuleLoader extends ClassLoader {
        private final String moduleName, packagePrefix;
        private final ModuleLoader[] dependencies;

        ModuleLoader(ClassLoader parent, ModuleLoader[] dependencies, String moduleName) {
            super(parent);
            this.dependencies = dependencies;
            this.moduleName = moduleName;
            this.packagePrefix = moduleName + '.';
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
//            System.out.println(this + ": initiated loading " + name);
            Class<?> klass = loadModuleClass(name, resolve);
            if (klass != null) return klass;
            // try dependencies
            for (ModuleLoader dependency : dependencies) {
                klass = dependency.loadModuleClass(name, resolve);
                if (klass != null) return klass;
            }
            // delegate to super
            return super.loadClass(name, resolve);
        }

        private Class<?> loadModuleClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(packagePrefix)) return null;
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
                }
                else {
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
