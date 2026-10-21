package vn.edu.parking.service;

public interface AccountSessionRevocationService {
    void revokeAllForAccount(Long accountId);
}
