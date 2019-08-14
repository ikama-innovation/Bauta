package ikama.bauta.core;

import ikama.bauta.core.BautaConfig;
import org.junit.BeforeClass;

public class BautaManagerIntegrationTest {

    private static BautaConfig bautaConfig;

    @BeforeClass
    public static void initalizeGreetingConfig() {
        bautaConfig = new BautaConfig();
        //greetingConfig.put(BautaConfigParams.USER_NAME, "World");
        //greetingConfig.put(BautaConfigParams.MORNING_MESSAGE, "Good Morning");
        //greetingConfig.put(BautaConfigParams.AFTERNOON_MESSAGE, "Good Afternoon");
        //greetingConfig.put(BautaConfigParams.EVENING_MESSAGE, "Good Evening");
        //greetingConfig.put(BautaConfigParams.NIGHT_MESSAGE, "Good Night");
    }
/*
    @Test
    public void givenMorningTime_ifMorningMessage_thenSuccess() {
        String expected = "Hello World, Good Morning";
        BautaManager BautaManager = new BautaManager(greetingConfig);
        String actual = BautaManager.greet(LocalDateTime.of(2017, 3, 1, 6, 0));
        assertEquals(expected, actual);
    }

    @Test
    public void givenAfternoonTime_ifAfternoonMessage_thenSuccess() {
        String expected = "Hello World, Good Afternoon";
        BautaManager BautaManager = new BautaManager(greetingConfig);
        String actual = BautaManager.greet(LocalDateTime.of(2017, 3, 1, 13, 0));
        assertEquals(expected, actual);
    }

    @Test
    public void givenEveningTime_ifEveningMessage_thenSuccess() {
        String expected = "Hello World, Good Evening";
        BautaManager BautaManager = new BautaManager(greetingConfig);
        String actual = BautaManager.greet(LocalDateTime.of(2017, 3, 1, 19, 0));
        assertEquals(expected, actual);
    }

    @Test
    public void givenNightTime_ifNightMessage_thenSuccess() {
        String expected = "Hello World, Good Night";
        BautaManager BautaManager = new BautaManager(greetingConfig);
        String actual = BautaManager.greet(LocalDateTime.of(2017, 3, 1, 21, 0));
        assertEquals(expected, actual);
    }
    */

}
