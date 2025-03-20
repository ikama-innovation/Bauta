package se.ikama.bauta.util;

import java.time.LocalDateTime;

public class DateUtils {

    public static boolean isSameDay(LocalDateTime d1, LocalDateTime d2) {
        if (d1 == null || d2 == null) {
            return false;
        }
        return d1.getYear() == d2.getYear() 
            && d1.getMonthValue() == d2.getMonthValue()
            && d1.getDayOfMonth() == d2.getDayOfMonth();
    }

}
