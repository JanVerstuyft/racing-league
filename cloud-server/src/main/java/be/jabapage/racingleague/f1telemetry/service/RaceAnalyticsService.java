package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.TyreStint;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class RaceAnalyticsService {


    @Autowired
    private SessionResultRepository sessionResultRepository;

    private record SectorData(long time, String tyre) {}
    private record WeightedResult(double pace, Map<String, Double> tyreWeights) {}

    public List<RacePaceStats> calculatePureRacePace(Long sessionResultId) {
        SessionResult raceSession = sessionResultRepository.findById(sessionResultId).orElse(null);
        if (raceSession == null) return Collections.emptyList();

        int maxLaps = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .mapToInt(LapResult::getLapNumber)
                .max().orElse(0);

        double absoluteBestS1 = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .filter(LapResult::getIsValid)
                .mapToLong(LapResult::getS1InMS)
                .filter(t -> t > 0)
                .min().orElse(0) / 1000.0;
        double absoluteBestS2 = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .filter(LapResult::getIsValid)
                .mapToLong(LapResult::getS2InMS)
                .filter(t -> t > 0)
                .min().orElse(0) / 1000.0;
        double absoluteBestS3 = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .filter(LapResult::getIsValid)
                .mapToLong(LapResult::getS3InMS)
                .filter(t -> t > 0)
                .min().orElse(0) / 1000.0;
        double absoluteBestLap = absoluteBestS1 + absoluteBestS2 + absoluteBestS3;

        List<RacePaceStats> statsList = new ArrayList<>();
        int seg1End = maxLaps / 3;
        int seg2End = 2 * maxLaps / 3;

        Tier tier = raceSession.getTier();
        if (tier == null && raceSession.getEvent() != null) {
            tier = raceSession.getEvent().getTier();
        }
        if (tier == null) return Collections.emptyList();
        League league = tier.getLeague();
        double thresholdPct = (league.getMinLapsPct() != null ? league.getMinLapsPct() : 60) / 100.0;

        for (DriverResult dr : raceSession.getDriverResults()) {
            List<LapResult> validLaps = dr.getLapResults().stream()
                    .filter(LapResult::getIsValid)
                    .toList();

            if (dr.getLapResults().size() < maxLaps * thresholdPct) continue;

            RacePaceStats stats = new RacePaceStats();
            stats.setDriverName(dr.getDriverName());
            stats.setAi(dr.isAi());
            stats.setTeamName(dr.getTeamName());
            stats.setCountry(dr.getCountry());

            List<LapResult> seg1Laps = validLaps.stream().filter(l -> l.getLapNumber() <= seg1End).toList();
            List<LapResult> seg2Laps = validLaps.stream().filter(l -> l.getLapNumber() > seg1End && l.getLapNumber() <= seg2End).toList();
            List<LapResult> seg3Laps = validLaps.stream().filter(l -> l.getLapNumber() > seg2End).toList();

            Map<String, Double> tyreWeightAggregator = new HashMap<>();
            
            double s1 = processSectorWithSegments(seg1Laps, seg2Laps, seg3Laps, LapResult::getS1InMS, tyreWeightAggregator);
            double s2 = processSectorWithSegments(seg1Laps, seg2Laps, seg3Laps, LapResult::getS2InMS, tyreWeightAggregator);
            double s3 = processSectorWithSegments(seg1Laps, seg2Laps, seg3Laps, LapResult::getS3InMS, tyreWeightAggregator);

            stats.setS1Pace(s1 / 1000.0);
            stats.setS2Pace(s2 / 1000.0);
            stats.setS3Pace(s3 / 1000.0);
            stats.setPureRacePace((s1 + s2 + s3) / 1000.0);

            double totalWeightSum = tyreWeightAggregator.values().stream().mapToDouble(Double::doubleValue).sum();
            Map<String, Double> tyreUsage = new HashMap<>();
            if (totalWeightSum > 0) {
                for (Map.Entry<String, Double> entry : tyreWeightAggregator.entrySet()) {
                    tyreUsage.put(entry.getKey(), (entry.getValue() / totalWeightSum) * 100.0);
                }
            }
            stats.setTyreUsage(tyreUsage);

            statsList.add(stats);
        }

        calculatePerformances(statsList, absoluteBestLap, absoluteBestS1, absoluteBestS2, absoluteBestS3);

        return statsList.stream()
                .sorted(Comparator.comparingDouble(RacePaceStats::getPureRacePace))
                .collect(Collectors.toList());
    }

    private void calculatePerformances(List<RacePaceStats> statsList, double bestLap, double bestS1, double bestS2, double bestS3) {
        if (statsList.isEmpty()) return;

        calculateSinglePerformance(statsList, RacePaceStats::getPureRacePace, RacePaceStats::setSectorPerformance, bestLap);
        calculateSinglePerformance(statsList, RacePaceStats::getS1Pace, RacePaceStats::setS1Performance, bestS1);
        calculateSinglePerformance(statsList, RacePaceStats::getS2Pace, RacePaceStats::setS2Performance, bestS2);
        calculateSinglePerformance(statsList, RacePaceStats::getS3Pace, RacePaceStats::setS3Performance, bestS3);
    }

    private void calculateSinglePerformance(List<RacePaceStats> statsList, java.util.function.ToDoubleFunction<RacePaceStats> getter, java.util.function.BiConsumer<RacePaceStats, Double> setter, double best) {
        double avg = statsList.stream().mapToDouble(getter).filter(v -> v > 0).average().orElse(0);
        
        for (RacePaceStats s : statsList) {
            double val = getter.applyAsDouble(s);
            if (val <= 0 || best <= 0) {
                setter.accept(s, 0.0);
                continue;
            }
            if (avg <= best) {
                setter.accept(s, 10.0);
            } else {
                double perf = 10.0 - 5.0 * (val - best) / (avg - best);
                setter.accept(s, Math.max(0, Math.min(10.0, perf)));
            }
        }
    }

    private double processSectorWithSegments(List<LapResult> s1, List<LapResult> s2, List<LapResult> s3, java.util.function.ToLongFunction<LapResult> sectorGetter, Map<String, Double> tyreWeightAggregator) {
        List<WeightedResult> results = new ArrayList<>();
        
        results.add(calculateWeightedSector(s1.stream().map(l -> new SectorData(sectorGetter.applyAsLong(l), String.valueOf(l.getTyreCompound()))).toList()));
        results.add(calculateWeightedSector(s2.stream().map(l -> new SectorData(sectorGetter.applyAsLong(l), String.valueOf(l.getTyreCompound()))).toList()));
        results.add(calculateWeightedSector(s3.stream().map(l -> new SectorData(sectorGetter.applyAsLong(l), String.valueOf(l.getTyreCompound()))).toList()));

        double totalPace = 0;
        int count = 0;
        for (WeightedResult wr : results) {
            if (wr.pace() > 0) {
                totalPace += wr.pace();
                count++;
                wr.tyreWeights().forEach((k, v) -> tyreWeightAggregator.put(k, tyreWeightAggregator.getOrDefault(k, 0.0) + v));
            }
        }
        
        return count > 0 ? totalPace / count : 0;
    }

    private WeightedResult calculateWeightedSector(List<SectorData> data) {
        List<SectorData> filtered = data.stream().filter(d -> d.time() > 0).collect(Collectors.toList());
        if (filtered.isEmpty()) return new WeightedResult(0, Collections.emptyMap());
        
        filtered.sort(Comparator.comparingLong(SectorData::time));

        int n = filtered.size();
        double n30 = n * 0.3;
        double n60 = n * 0.6;
        
        double totalWeight = 0;
        double weightedSum = 0;
        Map<String, Double> tyreWeights = new HashMap<>();

        for (int i = 0; i < n; i++) {
            double weight = 0;
            int rank = i + 1;

            if (rank <= n30) {
                weight = 1.0;
            } else if (rank <= n60) {
                if (n60 > n30) {
                    weight = (n60 - rank) / (n60 - n30);
                    if (weight < 0) weight = 0;
                } else {
                    weight = 0;
                }
            } else {
                weight = 0;
            }

            if (weight > 0) {
                weightedSum += filtered.get(i).time() * weight;
                totalWeight += weight;
                
                try {
                    String compound = TelemetryProcessingService.TYRE_COMPOUNDS.getOrDefault(Integer.valueOf(filtered.get(i).tyre()), "U");
                    tyreWeights.put(compound, tyreWeights.getOrDefault(compound, 0.0) + weight);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }

        return new WeightedResult(totalWeight > 0 ? weightedSum / totalWeight : 0, tyreWeights);
    }

    public List<ConsistencyStats> calculateConsistency(Long sessionResultId) {
        SessionResult raceSession = sessionResultRepository.findById(sessionResultId).orElse(null);
        if (raceSession == null) return Collections.emptyList();

        int maxLaps = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .mapToInt(LapResult::getLapNumber)
                .max().orElse(0);

        Tier tier = raceSession.getTier();
        if (tier == null && raceSession.getEvent() != null) {
            tier = raceSession.getEvent().getTier();
        }
        if (tier == null) return Collections.emptyList();
        League league = tier.getLeague();
        double thresholdPct = (league.getMinLapsPct() != null ? league.getMinLapsPct() : 60) / 100.0;

        List<ConsistencyStats> statsList = new ArrayList<>();

        for (DriverResult dr : raceSession.getDriverResults()) {
            if (dr.getLapResults().size() < maxLaps * thresholdPct) continue;

            List<LapResult> laps = dr.getLapResults().stream()
                    .filter(LapResult::getIsValid)
                    .sorted(Comparator.comparingInt(LapResult::getLapNumber))
                    .collect(Collectors.toList());

            if (laps.size() < 3) continue;

            ConsistencyStats stats = new ConsistencyStats();
            stats.setDriverName(dr.getDriverName());
            stats.setAi(dr.isAi());
            stats.setTeamName(dr.getTeamName());
            stats.setCountry(dr.getCountry());

            stats.setS1AvgDiff(calculateProcessedSectorDiff(laps.stream().mapToLong(LapResult::getS1InMS).toArray()));
            stats.setS2AvgDiff(calculateProcessedSectorDiff(laps.stream().mapToLong(LapResult::getS2InMS).toArray()));
            stats.setS3AvgDiff(calculateProcessedSectorDiff(laps.stream().mapToLong(LapResult::getS3InMS).toArray()));
            stats.setAvgDiff((stats.getS1AvgDiff() + stats.getS2AvgDiff() + stats.getS3AvgDiff()) / 1000.0);

            statsList.add(stats);
        }

        if (!statsList.isEmpty()) {
            double bestTotal = statsList.stream().mapToDouble(ConsistencyStats::getAvgDiff).min().orElse(0);
            double worstTotal = statsList.stream().mapToDouble(ConsistencyStats::getAvgDiff).max().orElse(1);
            
            double bestS1 = statsList.stream().mapToDouble(ConsistencyStats::getS1AvgDiff).min().orElse(0);
            double worstS1 = statsList.stream().mapToDouble(ConsistencyStats::getS1AvgDiff).max().orElse(1);
            double bestS2 = statsList.stream().mapToDouble(ConsistencyStats::getS2AvgDiff).min().orElse(0);
            double worstS2 = statsList.stream().mapToDouble(ConsistencyStats::getS2AvgDiff).max().orElse(1);
            double bestS3 = statsList.stream().mapToDouble(ConsistencyStats::getS3AvgDiff).min().orElse(0);
            double worstS3 = statsList.stream().mapToDouble(ConsistencyStats::getS3AvgDiff).max().orElse(1);

            for (ConsistencyStats s : statsList) {
                s.setRating(calculateNormalizedRating(s.getAvgDiff(), bestTotal, worstTotal));
                s.setS1Rating(calculateNormalizedRating(s.getS1AvgDiff(), bestS1, worstS1));
                s.setS2Rating(calculateNormalizedRating(s.getS2AvgDiff(), bestS2, worstS2));
                s.setS3Rating(calculateNormalizedRating(s.getS3AvgDiff(), bestS3, worstS3));
            }
        }

        return statsList.stream()
                .sorted(Comparator.comparingDouble(ConsistencyStats::getRating).reversed())
                .collect(Collectors.toList());
    }

    private double calculateNormalizedRating(double val, double best, double worst) {
        if (worst == best) return 100.0;
        double r = 100.0 * (1.0 - (val - best) / (worst - best));
        return Math.max(0, Math.min(100.0, r));
    }

    private double calculateProcessedSectorDiff(long[] times) {
        if (times.length < 2) return 0;

        List<Double> diffs2 = new ArrayList<>();
        for (int i = 1; i < times.length; i++) {
            if (times[i] <= 0 || times[i-1] <= 0) continue;
            double diff = Math.abs(times[i] - times[i-1]);
            if (times[i] < times[i-1]) diff *= 0.5;
            diffs2.add(diff);
        }

        List<Double> diffs3 = new ArrayList<>();
        for (int i = 2; i < times.length; i++) {
            if (times[i] <= 0 || times[i-1] <= 0 || times[i-2] <= 0) continue;
            double d1 = Math.abs(times[i] - times[i-1]) * (times[i] < times[i-1] ? 0.5 : 1.0);
            double d2 = Math.abs(times[i-1] - times[i-2]) * (times[i-1] < times[i-2] ? 0.5 : 1.0);
            diffs3.add((d1 + d2) / 2.0);
        }

        double score2 = processWeightedDiff(diffs2, 0.25, 0.25, 1.0);
        double score3 = processWeightedDiff(diffs3, 0.15, 0.15, 0.75);

        return (score2 + score3);
    }

    private double processWeightedDiff(List<Double> diffs, double p1, double p2, double baseWeight) {
        if (diffs.isEmpty()) return 0;
        Collections.sort(diffs);

        int n = diffs.size();
        double totalWeight = 0;
        double weightedSum = 0;

        for (int i = 0; i < n; i++) {
            double rank = i + 1;
            double weight = 0;

            if (rank <= n * p1) {
                weight = baseWeight;
            } else if (rank <= n * (p1 + p2)) {
                weight = baseWeight * (1.0 - (rank - n * p1) / (n * p2));
            }

            if (weight > 0) {
                weightedSum += diffs.get(i) * weight;
                totalWeight += weight;
            }
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0;
    }

    public List<LongestStintStats> calculateLongestStints(Long sessionResultId) {
        SessionResult raceSession = sessionResultRepository.findById(sessionResultId).orElse(null);
        if (raceSession == null) return Collections.emptyList();

        long bestS1 = Long.MAX_VALUE;
        long bestS2 = Long.MAX_VALUE;
        long bestS3 = Long.MAX_VALUE;

        for (DriverResult dr : raceSession.getDriverResults()) {
            for (LapResult lap : dr.getLapResults()) {
                if (lap.getIsValid()) {
                    if (lap.getS1InMS() > 0) bestS1 = Math.min(bestS1, lap.getS1InMS());
                    if (lap.getS2InMS() > 0) bestS2 = Math.min(bestS2, lap.getS2InMS());
                    if (lap.getS3InMS() > 0) bestS3 = Math.min(bestS3, lap.getS3InMS());
                }
            }
        }

        if (bestS1 == Long.MAX_VALUE || bestS2 == Long.MAX_VALUE || bestS3 == Long.MAX_VALUE) {
            return Collections.emptyList();
        }

        double limitS1 = bestS1 * 1.07;
        double limitS2 = bestS2 * 1.07;
        double limitS3 = bestS3 * 1.07;

        List<LongestStintStats> allStints = new ArrayList<>();

        for (DriverResult dr : raceSession.getDriverResults()) {
            List<LapResult> laps = new ArrayList<>(dr.getLapResults());
            laps.sort(Comparator.comparingInt(LapResult::getLapNumber));

            LongestStintStats bestDriverStint = null;

            for (TyreStint stint : dr.getTyreStints()) {
                int startLap = stint.getEndLap() - stint.getLaps() + 1;
                int endLap = stint.getEndLap();

                List<LapResult> stintLaps = laps.stream()
                        .filter(l -> l.getLapNumber() >= startLap && l.getLapNumber() <= endLap)
                        .collect(Collectors.toList());

                if (stintLaps.isEmpty()) continue;

                LongestStintStats stats = new LongestStintStats();
                stats.setDriverName(dr.getDriverName());
                stats.setAi(dr.isAi());
                stats.setTeamName(dr.getTeamName());
                stats.setCountry(dr.getCountry());
                stats.setLaps(stint.getLaps());
                stats.setTyreCompound(TelemetryProcessingService.TYRE_COMPOUNDS.getOrDefault(stint.getTyreCompound(), "Unknown"));

                List<Long> s1Times = stintLaps.stream()
                        .filter(LapResult::getIsValid)
                        .map(LapResult::getS1InMS)
                        .filter(t -> t > 0 && t <= limitS1)
                        .collect(Collectors.toList());
                List<Long> s2Times = stintLaps.stream()
                        .filter(LapResult::getIsValid)
                        .map(LapResult::getS2InMS)
                        .filter(t -> t > 0 && t <= limitS2)
                        .collect(Collectors.toList());
                List<Long> s3Times = stintLaps.stream()
                        .filter(LapResult::getIsValid)
                        .map(LapResult::getS3InMS)
                        .filter(t -> t > 0 && t <= limitS3)
                        .collect(Collectors.toList());

                double avgS1 = s1Times.stream().mapToLong(Long::longValue).average().orElse(0);
                double avgS2 = s2Times.stream().mapToLong(Long::longValue).average().orElse(0);
                double avgS3 = s3Times.stream().mapToLong(Long::longValue).average().orElse(0);

                stats.setAvgS1(avgS1 / 1000.0);
                stats.setAvgS2(avgS2 / 1000.0);
                stats.setAvgS3(avgS3 / 1000.0);
                stats.setAvgLapTime((avgS1 + avgS2 + avgS3) / 1000.0);

                if (bestDriverStint == null || stats.getLaps() > bestDriverStint.getLaps()) {
                    bestDriverStint = stats;
                }
            }
            if (bestDriverStint != null) {
                allStints.add(bestDriverStint);
            }
        }

        return allStints.stream()
                .sorted(Comparator.comparingInt(LongestStintStats::getLaps).reversed())
                .collect(Collectors.toList());
    }
}
