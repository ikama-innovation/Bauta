package se.ikama.bauta.scheduling;


import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Data
@Entity
@Table(name = "SCHEDULING_CONFIGURATION")
public class SchedulingConfiguration {
	@Id
	String name;
	List<JobTrigger> jobTriggers;

	public static SchedulingConfiguration load(InputStream jsonInputStream) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(jsonInputStream, SchedulingConfiguration.class);
	}
	public static String toJson(SchedulingConfiguration sc) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(sc);
	}
}
