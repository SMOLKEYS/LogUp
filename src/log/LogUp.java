package log;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Log.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static arc.Core.*;
import static log.ANSITricks.*;

public class LogUp extends Mod{

    //handles %time% formatting
    private static DateTimeFormatter dateTimeFormatter;
    private static String tmpStr;
    private static final Seq<String> logBuffer = new Seq<>();

    // unlisted hardcoded entries:
    // %level% - log level (info blue, debug purple, warn yellow, error red)
    // %time% - log time (no default color)
    // %loggy% - the formatted first section, see map for colors
    // %content% - log contents, uncolored
    @SuppressWarnings("unchecked")
    private static Seq<Pair<String, Func<StackTraceElement, String>>> map = Seq.with(
            //class name (no package), cyan
            new Pair<>(field("class"), func(st -> {
                int last = st.getClassName().lastIndexOf('.');
                return colorize(ANSI_CYAN, st.getClassName().substring(last + 1));
            })),
            //class name (with package), cyan
            new Pair<>(field("qualifiedclass"), func(st -> colorize(ANSI_CYAN, st.getClassName()))),
            //origin source file, blue
            new Pair<>(field("file"), func(st -> colorize(ANSI_BLUE, st.getFileName()))),
            //exact method name, purple
            new Pair<>(field("method"), func(st -> colorize(ANSI_PURPLE, st.getMethodName()))),
            //exact line, blue
            new Pair<>(field("line"), func(st -> colorize(ANSI_BLUE, String.valueOf(st.getLineNumber()))))
    );

    public static boolean letter = true;

    //formatting used
    public static String formatting = "[%level%] [%class%.%method%(%file%:%line%)]";
    //final output to write
    public static String finalOutput = "%loggy% %content%";
    //dialog
    public static LogUpDialog dialog;

    public LogUp(){
        dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME;

        formatting = settings.getString("logup-formatting", "[%level%] [%class%.%method%(%file%:%line%)]");
        finalOutput = settings.getString("logup-final-output", "%loggy% %content%");
        enabled = settings.getBool("logup-enable-ansi", false);

        Log.logger = new LoggyLogger();

        Events.run(ClientLoadEvent.class, () -> {
            logBuffer.each(Vars.ui.consolefrag::addMessage);

            dialog = new LogUpDialog();
            Vars.ui.menufrag.addButton("@logup", Icon.terminal, dialog::show);
        });

        Log.info("Loaded LogUp logger.");
    }

    private static String field(String f){
        return "%" + f + "%";
    }

    private static Func<StackTraceElement, String> func(Func<StackTraceElement, String> f){
        return f;
    }

    public static String caller(){
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        //index of the first found Log call, as behind it is usually the class that made the call
        int logClassIndex = Structs.indexOf(stack, s -> s.getClassName().equals(Log.class.getName()));

        tmpStr = formatting;
        int i = 0;
        for(StackTraceElement el : stack){
            i++;
            if(el.getClassName().equals(Log.class.getName()) || i <= logClassIndex) continue;

            map.each(p -> {
                tmpStr = tmpStr.replace(p.a, p.b.get(el));
            });

            return tmpStr;
        }

        return "";
    }

    public static class LoggyLogger extends DefaultLogHandler{

        //append to existing log
        public String priorLog = settings.getDataDirectory().child("last_log.txt").readString();
        public Writer logWriter = settings.getDataDirectory().child("last_log.txt").writer(false);

        public LoggyLogger(){
            try{
                logWriter.write("LogUp is enabled! This may invalidate your log.\n\n" + priorLog);
                logWriter.flush();
            }catch(IOException e){
                Log.warn("Could not overwrite existing log. Skipping.");
            }
        }

        @Override
        public void log(LogLevel level, String text){
            String lev = level.toString();
            String color = switch(level){
                case debug -> ANSI_PURPLE;
                case info -> ANSI_BLUE;
                case warn -> ANSI_YELLOW;
                case err -> ANSI_RED;
                case none -> ANSI_WHITE;
            };

            String tex = finalOutput.replace("%loggy%", caller()
                    .replace("%level%", colorize(color, (letter ? String.valueOf(lev.charAt(0)) : lev).toUpperCase(Locale.ROOT)))
                    .replace("%time%", dateTimeFormatter.format(LocalTime.now()))
            ).replace("%content%", text);

            System.out.println(tex);

            if(!Vars.headless && (Vars.ui == null || Vars.ui.consolefrag == null)){
                logBuffer.add(tex);
            }else if(!Vars.headless){
                if(!OS.isWindows){
                    for(String code : ColorCodes.values){
                        tex = tex.replace(code, "");
                    }
                }

                Vars.ui.consolefrag.addMessage(Log.removeColors(tex));
            }

            try{
                logWriter.write(tex + "\n");
                logWriter.flush();
            }catch(IOException e){
                //ignore
            }
        }

    }

    public static class Pair<A, B>{
        public A a;
        public B b;

        public Pair(A a, B b){
            this.a = a;
            this.b = b;
        }
    }

    public static class LogUpDialog extends BaseDialog{

        public static Seq<String> params = Seq.with(
                "@ent.level",
                "@ent.time",
                "@ent.loggy",
                "@ent.content",
                "@ent.class",
                "@ent.qualifiedclass",
                "@ent.file",
                "@ent.method",
                "@ent.line"
        );

        public LogUpDialog(){
            super("@logup");

            addCloseButton();

            cont.left();
            cont.defaults().left();
            cont.add("@formatting").growX();
            cont.field(formatting, st -> settings.put("logup-formatting", formatting = st)).growX().row();
            cont.add("@finalOutput").growX();
            cont.field(finalOutput, st -> settings.put("logup-final-output", finalOutput = st)).growX().row();
            if(!Vars.mobile) cont.check("@enableAnsi", bl -> settings.put("logup-enable-ansi", enabled = bl)).growX().row();

            params.each(e -> cont.add(e).growX().row());
        }
    }
}
