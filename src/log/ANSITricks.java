package log;

import mindustry.*;

public class ANSITricks{

    public static boolean enabled = false;

    public static final String
    ANSI_RESET = h("0m"),
    ANSI_BLACK = h("30m"),
    ANSI_RED = h("31m"),
    ANSI_GREEN = h("32m"),
    ANSI_YELLOW = h("33m"),
    ANSI_BLUE = h("34m"),
    ANSI_PURPLE = h("35m"),
    ANSI_CYAN = h("36m"),
    ANSI_WHITE = h("37m");

    private static String h(String col){
        return "\u001B[" + col;
    }

    public static String colorize(String ansiColor, String in){
        if(!enabled || Vars.mobile) return in;
        return ansiColor + in + ANSI_RESET;
    }
}
