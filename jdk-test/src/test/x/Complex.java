package test.x;

/**
 */
public final class Complex {
    public final double re, im;

    public Complex(double re, double im) {
        this.re = re;
        this.im = im;
    }

    public Complex add(Complex c) {
        return new Complex(re + c.re, im + c.im);
    }

    @Override
    public String toString() {
        return re + " + " + im + " * i";
    }
}
