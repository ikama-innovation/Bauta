package se.ikama.bauta.core;

import lombok.Data;

@Data
public class ReadWriteInfo {
    private int readCount;
    private int writeCount;
    private int commitCount;
    private int rollbackCount;
    private int readSkipCount;
    private int processSkipCount;
    private int writeSkipCount;
    private int filterCount;

    public String toRWSString() {
        return "" + readCount + "/" + writeCount + "/" + (readSkipCount + writeSkipCount + processSkipCount);
    }
}
