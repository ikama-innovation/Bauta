package se.ikama.bauta.security;

import lombok.Data;

@Data
public class User {
    private String username;
    private String password;
    private String roles[];
}
