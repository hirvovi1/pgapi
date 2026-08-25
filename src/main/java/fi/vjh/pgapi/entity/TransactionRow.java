package fi.vjh.pgapi.entity;

import fi.vjh.pgapi.domain.TransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "transactions")
public class TransactionRow {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private UUID idempotencyKey;

    private UUID accountIdFrom;
    private UUID accountIdTo;
    private long amountInCents;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    // Hibernate vaatii tyhjän konstruktorin
    public TransactionRow() {}

    public TransactionRow(UUID id, UUID idempotencyKey, UUID accountIdFrom, UUID accountIdTo, long amountInCents, TransactionStatus status) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.accountIdFrom = accountIdFrom;
        this.accountIdTo = accountIdTo;
        this.amountInCents = amountInCents;
        this.status = status;
    }

}
