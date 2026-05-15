package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Entity
public class League {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @Column(name = "hide_ai")
    private Boolean hideAi = false;

    @Column(name = "show_tyre_wear")
    private Boolean showTyreWear = false;

    @Column(name = "show_ers")
    private Boolean showErs = false;

    public boolean isHideAi() {
        return hideAi != null && hideAi;
    }

    public boolean isShowTyreWear() {
        return showTyreWear != null && showTyreWear;
    }

    public boolean isShowErs() {
        return showErs != null && showErs;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Tier> tiers = new ArrayList<>();

    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TeamStanding> teamStandings = new ArrayList<>();

    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.Set<Event> events = new java.util.LinkedHashSet<>();

    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<SessionPointConfig> sessionPointConfigs = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof League that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
