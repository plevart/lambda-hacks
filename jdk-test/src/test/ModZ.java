/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import java.io.Serializable;

import static test.LambdaTest.deserialize;
import static test.LambdaTest.dump;
import static test.LambdaTest.serialize;

/**
 * @author peter
 */
public class ModZ implements Runnable {

    public ModX.BinOp samX_methodX = ModX::min;
//    public ModX.BinOp serMethRefXX = (ModX.BinOp & Serializable) ModX::min;
//    public ModX.BinOp deserMethRefXX = deserialize(serialize(serMethRefXX));

    public ModY.BinOp samY_methodX = ModX::min;
//    public ModY.BinOp serMethRefYX = (ModY.BinOp & Serializable) ModX::min;
//    public ModY.BinOp deserMethRefYX = deserialize(serialize(serMethRefYX));

    public ModX.BinOp samX_methodY = ModY::min;
//    public ModX.BinOp serMethRefXY = (ModX.BinOp & Serializable) ModY::min;
//    public ModX.BinOp deserMethRefXY = deserialize(serialize(serMethRefXY));

    public ModX.BinOp lambdaX = (a, b) -> a <= b ? a : b;
//    public ModX.BinOp serLambdaX = (ModX.BinOp & Serializable) (a, b) -> a <= b ? a : b;
//    public ModX.BinOp deserLambdaX = deserialize(serialize(serLambdaX));

    @Override
    public void run() {
        dump(this);
    }
}
