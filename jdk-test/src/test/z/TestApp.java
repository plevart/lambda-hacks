/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.z;

import java.io.Serializable;
import java.util.function.IntBinaryOperator;

import static test.LambdaTest.*;

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
}
