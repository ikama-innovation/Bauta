package ikama.bauta.batch.tasklet.oracle;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.tasklet.SystemProcessExitCodeMapper;

/**
 *
 */
public class SqlplusSystemProcessExitCodeMapper implements SystemProcessExitCodeMapper {

    @Override
    public ExitStatus getExitStatus(int exitCode) {
        if (exitCode == 0) {
            return ExitStatus.COMPLETED;
        } else {
            throw new RuntimeException("SQLPlus failed with exit code " + exitCode);
        }
    }

}
