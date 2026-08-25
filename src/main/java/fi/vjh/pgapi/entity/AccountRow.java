package fi.vjh.pgapi.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "accounts")
public class AccountRow {

    @Id
    private UUID id;
    private String ownerName;
    private long balanceInCents;

    public AccountRow() {}

    public AccountRow(UUID id, String ownerName, long balance) {
        this.id = id;
        this.ownerName = ownerName;
        this.balanceInCents = balance;
    }

}
