package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
public class ManualPenalty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_result_id", nullable = false)
    private SessionResult sessionResult;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_mapping_id", nullable = false)
    private DriverMapping driverMapping;

    @Column(name = "seconds")
    private Integer seconds;

    @Column(name = "point_deduction")
    private Integer pointDeduction;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManualPenalty that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
