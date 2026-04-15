package com.infinitematters.bookkeeping.users;

public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String email) {
        super("A user with email '" + email + "' already exists");
    }
}
