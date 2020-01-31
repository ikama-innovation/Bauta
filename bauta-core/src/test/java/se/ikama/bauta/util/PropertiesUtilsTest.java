package se.ikama.bauta.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class PropertiesUtilsTest {
    @Test
    public void testCommaSeparated() {
        Properties p = PropertiesUtils.fromCommaSeparatedString("key1=value1,key2=value2");
        Assert.assertEquals(p.getProperty("key1"), "value1");
        Assert.assertEquals(p.getProperty("key2"), "value2");
        p = PropertiesUtils.fromCommaSeparatedString("key1 =value1 , key2 = value2 ");
        Assert.assertEquals(p.getProperty("key1"), "value1");
        Assert.assertEquals(p.getProperty("key2"), "value2");
        p = PropertiesUtils.fromCommaSeparatedString("key1=value1");
        Assert.assertEquals(p.getProperty("key1"), "value1");
    }

}
