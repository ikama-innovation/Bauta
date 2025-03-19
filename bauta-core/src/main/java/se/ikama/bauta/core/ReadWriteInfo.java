package se.ikama.bauta.core;

import lombok.Data;

@Data
public class ReadWriteInfo {
    private long readCount;
    private long writeCount;
    private long commitCount;
    private long rollbackCount;
    private long readSkipCount;
    private long processSkipCount;
    private long writeSkipCount;
    private long filterCount;

    public String toRWSString() {
        return "" + readCount + "/" + writeCount + "/" + (readSkipCount + writeSkipCount + processSkipCount);
    }
}
