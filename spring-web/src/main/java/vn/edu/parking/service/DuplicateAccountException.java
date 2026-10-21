package vn.edu.parking.service;

public class DuplicateAccountException extends IllegalArgumentException {
    public DuplicateAccountException() { super("Account already exists"); }
}
