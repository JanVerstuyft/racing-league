package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class LiveState {
    @Id
    private Long tierId;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "compressed_state", columnDefinition = "BYTEA")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    private byte[] compressedState;
}
