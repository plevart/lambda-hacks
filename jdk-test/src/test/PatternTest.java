package test;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class PatternTest {

    private final Pattern pattern;

    public PatternTest(Pattern pattern) {
        this.pattern = pattern;
    }

    public Matcher matcher(CharSequence input) {return pattern.matcher(input);}

    public String replaceAll(String str, Function<Matcher, String> replacementFunction) {
        Matcher m = matcher(str);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, replacementFunction.apply(m));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void main(String[] args) {

        String s = "User's home is {user.home}, current dir is {user.dir}";

        Pattern pattern = Pattern.compile("\\{(.*?)\\}");
        PatternTest pt = new PatternTest(pattern);
        String msg = pt.replaceAll("User's home is {user.home}, current dir is {user.dir}", (m) -> System.getProperty(m.group(1), "UNKNOWN"));

        System.out.println(msg);
    }
}
