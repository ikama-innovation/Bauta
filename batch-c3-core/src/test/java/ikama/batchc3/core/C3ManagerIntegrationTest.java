package ikama.batchc3.core;

import org.junit.BeforeClass;

public class C3ManagerIntegrationTest {

    private static C3Config c3Config;

    @BeforeClass
    public static void initalizeGreetingConfig() {
        c3Config = new C3Config();
        //greetingConfig.put(C3ConfigParams.USER_NAME, "World");
        //greetingConfig.put(C3ConfigParams.MORNING_MESSAGE, "Good Morning");
        //greetingConfig.put(C3ConfigParams.AFTERNOON_MESSAGE, "Good Afternoon");
        //greetingConfig.put(C3ConfigParams.EVENING_MESSAGE, "Good Evening");
        //greetingConfig.put(C3ConfigParams.NIGHT_MESSAGE, "Good Night");
    }
/*
    @Test
    public void givenMorningTime_ifMorningMessage_thenSuccess() {
        String expected = "Hello World, Good Morning";
        C3Manager C3Manager = new C3Manager(greetingConfig);
        String actual = C3Manager.greet(LocalDateTime.of(2017, 3, 1, 6, 0));
        assertEquals(expected, actual);
    }

    @Test
    public void givenAfternoonTime_ifAfternoonMessage_thenSuccess() {
        String expected = "Hello World, Good Afternoon";
        C3Manager C3Manager = new C3Manager(greetingConfig);
        String actual = C3Manager.greet(LocalDateTime.of(2017, 3, 1, 13, 0));
        assertEquals(expected, actual);
    }

    @Test
    public void givenEveningTime_ifEveningMessage_thenSuccess() {
        String expected = "Hello World, Good Evening";
        C3Manager C3Manager = new C3Manager(greetingConfig);
        String actual = C3Manager.greet(LocalDateTime.of(2017, 3, 1, 19, 0));
        assertEquals(expected, actual);
    }

    @Test
    public void givenNightTime_ifNightMessage_thenSuccess() {
        String expected = "Hello World, Good Night";
        C3Manager C3Manager = new C3Manager(greetingConfig);
        String actual = C3Manager.greet(LocalDateTime.of(2017, 3, 1, 21, 0));
        assertEquals(expected, actual);
    }
    */

}
