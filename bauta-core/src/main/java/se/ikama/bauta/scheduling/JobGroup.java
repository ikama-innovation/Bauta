package se.ikama.bauta.scheduling;

import lombok.Data;

import java.util.List;

@Data
public class JobGroup{
    Long id;
    String name;
    String regex;
    List<String> jobNames;
}
