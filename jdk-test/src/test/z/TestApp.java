/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.z;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.function.IntBinaryOperator;


/**
 * @author peter
 */
public class TestApp implements Runnable {

    public test.x.BinOp samX_methodX = test.x.Math::min;
    public test.x.BinOp ser_samX_methodX = (test.x.BinOp & Serializable) test.x.Math::min;
    public test.x.BinOp deser_samX_methodX = deserialize(serialize(ser_samX_methodX));

    public test.y.BinOp samY_methodX = test.x.Math::min;
    public test.y.BinOp ser_samY_methodX = (test.y.BinOp & Serializable) test.x.Math::min;
    public test.y.BinOp deser_samY_methodX = deserialize(serialize(ser_samY_methodX));

    public test.x.BinOp samX_methodY = test.y.Math::min;
    public test.x.BinOp ser_samX_methodY = (test.x.BinOp & Serializable) test.y.Math::min;
    public test.x.BinOp deser_samX_methodY = deserialize(serialize(ser_samX_methodY));

    public test.x.BinOp samX_lambda = (a, b) -> a <= b ? a : b;
    public test.x.BinOp ser_samX_lambda = (test.x.BinOp & Serializable) (a, b) -> a <= b ? a : b;
    public test.x.BinOp deser_samX_lambda = deserialize(serialize(ser_samX_lambda));

    public IntBinaryOperator samS_methodS = java.lang.Math::min;
    public IntBinaryOperator samS_methodX = test.x.Math::min;
    public test.x.BinOp samX_methodS = java.lang.Math::min;

    @Override
    public void run() {
        dump(this);
    }

    static void dump(Object bean) {
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
            T res = (T) ois.readObject();
            System.out.println(res.getClass().getClassLoader() + ": deserialized: " + res);
            return res;
        }
        catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
