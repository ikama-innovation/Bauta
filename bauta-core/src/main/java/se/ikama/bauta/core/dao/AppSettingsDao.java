package se.ikama.bauta.core.dao;

import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AppSettingsDao {
    @Autowired
    DataSource dataSource;
       
    public String getSetting(String key) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        List<String> value = template.queryForList("select VAL from APP_SETTINGS where KEY=?", String.class, key);
        if (value.size() == 1) return value.iterator().next();
        else return null;
    }
    public boolean getBooleanSetting(String key) {
	return StringUtils.equalsIgnoreCase(getSetting(key), "true");
    }
    public void setBooleanSetting(String key, boolean value) {
	setSetting(key, (value ? "true" : "false"));
    }
    
    public void setSetting(String key, String value) {
	JdbcTemplate template = new JdbcTemplate(dataSource);
        String existingValue = getSetting(key);
	if (existingValue != null) {
	    template.update("update APP_SETTINGS set VAL=? where KEY=?", value, key);
	}
	else {
	    template.update("insert into APP_SETTINGS (KEY, VAL) values (?,?)", key, value);
	}
    }
}
