package se.ikama.bauta.security;

import lombok.Data;

@Data
public class SecurityConfig {
    private Boolean enabled;
    private User users[];
}
