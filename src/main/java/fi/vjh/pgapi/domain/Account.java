package fi.vjh.pgapi.domain;

import java.util.Objects;
import java.util.UUID;

public class Account {
    private final UUID id;
    private final String ownerName;
    private long balanceInCents;

    public Account(UUID id, String ownerName, long balanceInCents) {
        this.id = Objects.requireNonNull(id);
        this.ownerName = ownerName;
        this.balanceInCents = balanceInCents;
    }

    public Account(String ownerName, long balanceInCents) {
        this.id = UUID.randomUUID();
        this.ownerName = ownerName;
        this.balanceInCents = balanceInCents;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getBalance() {
        return balanceInCents;
    }

    public long getBalanceInCents() {
        return balanceInCents;
    }

    public void deposit(long amountCents) {
        balanceInCents += amountCents;
    }

    public void withdraw(long amountCents) {
        balanceInCents -= amountCents;
    }
}
