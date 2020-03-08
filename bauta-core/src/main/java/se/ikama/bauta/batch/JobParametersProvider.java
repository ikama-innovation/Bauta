package se.ikama.bauta.batch;

import java.util.List;

public interface JobParametersProvider {
    public List<String> getRequiredKeys();
    public List<String> getOptionalKeys();
}
