package se.ikama.bauta.util;

import java.util.Properties;

public class PropertiesUtils {
    public static Properties fromCommaSeparatedString(String s) {
        Properties properties = new Properties();
        String[] entries = s.split(",");
        for (final String entry : entries) {
            if (entry.length() > 0) {
                final int index = entry.indexOf('=');
                if (index > 0) {
                    final String name = entry.substring(0, index).trim();
                    final String value = entry.substring(index + 1).trim();
                    properties.setProperty(name, value);
                } else {
                    // no value is empty string which is how java.util.Properties works
                    properties.setProperty(entry, "");
                }
            }
        }
        return properties;
    }
}
