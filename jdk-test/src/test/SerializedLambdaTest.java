package test;

import sun.misc.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializedLambdaTest {

    public interface SerializableRunnable extends Runnable, Serializable {}

    public static class MyCode implements SerializableRunnable {

        private byte[] serialize(Object o) {
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

        private <T> T deserialize(byte[] bytes) {
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

        @Override
        public void run() {
            System.out.println("                this: " + this);

            SerializableRunnable deSerializedThis = deserialize(serialize(this));
            System.out.println("    deSerializedThis: " + deSerializedThis);

            SerializableRunnable runnable = () -> {System.out.println("HELLO");};
            System.out.println("            runnable: " + runnable);

            SerializableRunnable deSerializedRunnable = deserialize(serialize(runnable));
            System.out.println("deSerializedRunnable: " + deSerializedRunnable);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader myCl = new MyClassLoader(
            SerializedLambdaTest.class.getClassLoader()
        );
        Class<?> myCodeClass = Class.forName(
            SerializedLambdaTest.class.getName() + "$MyCode",
            true,
            myCl
        );
        Runnable myCode = (Runnable) myCodeClass.newInstance();
        myCode.run();
    }

    static class MyClassLoader extends ClassLoader {
        MyClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("test.")) {
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
            } else {
                return super.loadClass(name, resolve);
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
    }
}
