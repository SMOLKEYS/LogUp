package log;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.mod.*;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static arc.Core.*;

public class LogUp extends Mod{

    private static DateTimeFormatter dateTimeFormatter;
    private static final Seq<String> logBuffer = new Seq<>();

    @SuppressWarnings("unchecked")
    private static Seq<Pair<String, Func<StackTraceElement, Object>>> map = Seq.with(
            new Pair<>(field("class"), func(st -> {
                int last = st.getClassName().lastIndexOf('.');
                return st.getClassName().substring(last + 1);
            })),
            new Pair<>(field("qualifiedclass"), func(StackTraceElement::getClassName)),
            new Pair<>(field("file"), func(StackTraceElement::getFileName)),
            new Pair<>(field("method"), func(StackTraceElement::getMethodName)),
            new Pair<>(field("line"), func(StackTraceElement::getLineNumber))
    );

    public static boolean letter = true;
    public static String formatting = "[%level%] [%time%] [%class%.%method%(%file%:%line%)]";

    public LogUp(){
        dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME;

        Log.logger = new LoggyLogger();

        Events.run(EventType.ClientLoadEvent.class, () -> {
            logBuffer.each(Vars.ui.consolefrag::addMessage);
        });

        Log.info("Loaded LogUp logger.");
    }

    private static String field(String f){
        return "%" + f + "%";
    }

    private static Func<StackTraceElement, Object> func(Func<StackTraceElement, Object> f){
        return f;
    }

    public static String caller(){
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        //index of the first found Log call, as behind it is usually the class that made the call
        int logClassIndex = Structs.indexOf(stack, s -> s.getClassName().equals(Log.class.getName()));

        AtomicReference<String> l = new AtomicReference<>(formatting);
        int i = 0;
        for(StackTraceElement el : stack){
            i++;
            if(el.getClassName().equals(Log.class.getName()) || i <= logClassIndex) continue;

            map.each(p -> {
                l.set(l.get().replace(p.a, p.b.get(el).toString()));
            });

            return l.get();
        }

        return "";
    }

    public static class LoggyLogger extends Log.DefaultLogHandler{

        //append to existing log
        public String priorLog = settings.getDataDirectory().child("last_log.txt").readString();
        public Writer logWriter = settings.getDataDirectory().child("last_log.txt").writer(false);

        public LoggyLogger(){
            try{
                logWriter.write("LogUp is enabled! This may invalidate your log.\n\n" + priorLog);
                logWriter.flush();
            }catch(IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void log(Log.LogLevel level, String text){
            String lev = level.toString();
            String tex = Strings.format(
                    "@ @",
                    caller()
                            .replace("%level%", (letter ? String.valueOf(lev.charAt(0)) : lev).toUpperCase(Locale.ROOT))
                            .replace("%time%", dateTimeFormatter.format(LocalTime.now())),
                    text
            );

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
                e.printStackTrace();
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

}
