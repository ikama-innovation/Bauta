package se.ikama.bauta.batch;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfirmationValidator implements JobParametersValidator, JobParametersProvider {

    private List<String> requiredKeys = Arrays.asList(new String[]{"confirm"});

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        String confirm = parameters.getString("confirm");
        if (!"yes".equalsIgnoreCase(confirm)) {
            throw new JobParametersInvalidException("You must confirm with a 'yes'!");
        }
    }

    @Override
    public List<String> getRequiredKeys() {
        return requiredKeys;
    }

    @Override
    public List<String> getOptionalKeys() {
        return Collections.EMPTY_LIST;
    }
}
