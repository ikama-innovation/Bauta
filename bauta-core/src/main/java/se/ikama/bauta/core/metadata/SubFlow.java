package se.ikama.bauta.core.metadata;

import lombok.Data;

@Data
public abstract class SubFlow {
    protected  String id;
    protected String nextId;
}
