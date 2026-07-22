package com.coxmegascale;

import com.coxmegascale.calc.RaidMath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RaidSummaryLoggerTest
{
    @Test
    public void writesOneReadableJsonFileForCompletedRaid() throws Exception
    {
        Path root = Files.createTempDirectory("cox-raid-summary");
        RaidMath math = new RaidMath();
        RaidSummaryLogger logger = new RaidSummaryLogger(root);
        RaidSummaryLogger.Snapshot snapshot = new RaidSummaryLogger.Snapshot(
            Instant.parse("2026-07-21T20:00:00Z"),
            Instant.parse("2026-07-21T20:30:00Z"),
            "full",
            "ABC123",
            100,
            10,
            Arrays.asList("Raider One", "Raider \"Two\""),
            867_500,
            86_750,
            math.calculatePurpleChance(867_500),
            math.calculatePurpleChance(86_750),
            1_000,
            4_000,
            3_000,
            Arrays.asList("Mystics", "Olm"),
            Arrays.asList(
                new RaidSummaryLogger.Room("Mystics", 60_000, 10_000, 2_500),
                new RaidSummaryLogger.Room("Olm", 300_000, 100_000, 10_000)),
            Collections.singletonList(new RaidSummaryLogger.Death("Olm", 1_000)));

        logger.write(snapshot);
        logger.close();

        Path directory = root.resolve("cox-assistant").resolve("raid-logs");
        List<Path> files = Files.list(directory).collect(Collectors.toList());
        assertEquals(1, files.size());
        assertTrue(files.get(0).getFileName().toString().endsWith(".json"));
        String json = new String(Files.readAllBytes(files.get(0)), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"durationMillis\": 1800000"));
        assertTrue(json.contains("\"participants\": [\"Raider One\", \"Raider \\\"Two\\\"\"]"));
        assertTrue(json.contains("\"personalSharePercent\": 25.000000"));
        assertTrue(json.contains("\"chanceAtLeastOnePercent\": 77.466801"));
        assertTrue(json.contains("\"name\": \"Olm\""));
        assertTrue(json.contains("\"pointsLost\": 1000"));
    }
}
