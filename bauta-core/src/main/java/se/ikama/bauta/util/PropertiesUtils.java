package se.ikama.bauta.util;

import java.util.Map;
import java.util.Properties;

import org.springframework.batch.core.JobParameter;

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

    public static Properties fromMap(Map<String, JobParameter<?>> jp) {
        Properties p = new Properties();
        for (var entry : jp.entrySet()) {
            // TODO: Proper string conversion. Or better: rework usage of strings/properties 
            String stringValue = entry.getValue().getValue().toString();
            p.setProperty(entry.getKey(), stringValue);
        }
        return p;
    }
}
