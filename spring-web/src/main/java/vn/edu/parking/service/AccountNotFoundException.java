package vn.edu.parking.service;

public final class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException() { super("Account not found"); }
}
