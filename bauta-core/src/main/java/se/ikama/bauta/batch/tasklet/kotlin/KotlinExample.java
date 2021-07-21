package se.ikama.bauta.batch.tasklet.kotlin;

import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory;

import javax.script.*;

public class KotlinExample {

    public static void test() {
        String cities[] = {"London", "NewYork", "Sydney", "Bangalore", "Chennai", "Mumbai"};
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("kotlin");
        if (engine == null) {
            System.out.println("Engine missing");
            System.exit(1);
        }
        engine.put("citiesArray", cities);
        try {
            // Evaluate  script using script engine
            engine.eval("println(\"Hello, world\")");
            engine.eval("for (c in (bindings[\"citiesArray\"] as List<String>)) { println(c) }");
            System.out.println("not stuck? ");
        } catch (ScriptException exception) {
            exception.printStackTrace();
        }

    }

    public static void anotherTest() throws ScriptException {
        String kotlinScript = "\"Using Kotlin version: "
                + "${KotlinVersion.CURRENT}\"";
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("kotlin");
        Object result = engine.eval(kotlinScript, engine.getContext());
        System.out.println(result);
    }

}

