package com.coxmegascale;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PointFixtureLoggerTest
{
    @Test
    public void disabledDoesNotCreateFiles() throws Exception
    {
        Path root = Files.createTempDirectory("cox-fixtures");
        PointFixtureLogger logger = new PointFixtureLogger(false, root);
        logger.startRaid(metadata("normal", "raid_start"), 0, 0);
        logger.close();
        assertFalse(Files.exists(root.resolve("cox-assistant")));
    }

    @Test
    public void writesAnonymousVersionedPointTimeline() throws Exception
    {
        Path root = Files.createTempDirectory("cox-fixtures");
        PointFixtureLogger logger = new PointFixtureLogger(true, root);
        logger.startRaid(metadata("cm", "raid_start"), 100, 40);
        logger.room("room_entry", "Mystics", 120, 55, "room_entry_observed");
        logger.room("points_update", "Mystics", 105, 45, "local_death_observed");
        logger.raidComplete(200, 90);
        logger.close();

        String contents = readOnlyFixture(root);
        assertTrue(contents.contains("\"schemaVersion\":2"));
        assertTrue(contents.contains("\"startReason\":\"raid_start\""));
        assertTrue(contents.contains("\"layoutCode\":\"ABCDEF\""));
        assertTrue(contents.contains("\"scaleSource\":\"detected\""));
        assertTrue(contents.contains("\"thievingSource\":\"compatible_party_sum\""));
        assertTrue(contents.contains("\"fishingSource\":\"manual_override\""));
        assertTrue(contents.contains("\"groupDelta\":20"));
        assertTrue(contents.contains("\"personalDelta\":15"));
        assertTrue(contents.contains("\"pointSource\":\"local_death_observed\""));
        assertTrue(contents.contains("\"completed\":true"));
        assertTrue(contents.contains("\"endReason\":\"completed\""));
        assertTrue(contents.contains("\"sequence\":0"));
        assertTrue(contents.contains("\"sequence\":3"));
        assertFalse(contents.contains("ExamplePlayer"));
        assertFalse(contents.contains("username"));
        assertFalse(contents.contains("displayName"));
        assertFalse(contents.contains("memberId"));
        assertFalse(contents.contains("world"));
        assertFalse(contents.contains("timestamp"));
        assertFalse(contents.contains("sessionId"));
    }

    @Test
    public void repeatedStartAndRescoutStayInOneFile() throws Exception
    {
        Path root = Files.createTempDirectory("cox-fixtures");
        PointFixtureLogger logger = new PointFixtureLogger(true, root);
        logger.startRaid(metadata("normal", "raid_start"), 0, 0);
        logger.startRaid(metadata("normal", "raid_start"), 0, 0);
        logger.room("room_entry", "Tekton", 50, 45, "room_entry_observed");
        logger.room("room_exit", "Tekton", 75, 65, "room_exit_observed");
        logger.endSession("raid_exit");
        logger.close();

        List<Path> files = fixtureFiles(root);
        assertEquals(1, files.size());
        String contents = new String(Files.readAllBytes(files.get(0)), StandardCharsets.UTF_8);
        assertEquals(1, occurrences(contents, "\"event\":\"raid_start\""));
        assertTrue(contents.contains("\"completed\":false"));
        assertTrue(contents.contains("\"endReason\":\"raid_exit\""));
    }

    @Test
    public void doesNotCreateStartOnlyScoutFile() throws Exception
    {
        Path root = Files.createTempDirectory("cox-fixtures");
        PointFixtureLogger logger = new PointFixtureLogger(true, root);
        logger.startRaid(metadata("normal", "enabled_mid_raid"), 0, 0);
        logger.endSession("raid_exit");
        logger.close();
        assertTrue(fixtureFiles(root).isEmpty());
    }

    @Test
    public void completedRaidAndNextRaidUseSeparateFiles() throws Exception
    {
        Path root = Files.createTempDirectory("cox-fixtures");
        PointFixtureLogger logger = new PointFixtureLogger(true, root);
        logger.startRaid(metadata("full", "raid_start"), 0, 0);
        logger.room("room_entry", "Tekton", 50, 45, "room_entry_observed");
        logger.raidComplete(50, 45);
        logger.startRaid(metadata("cm", "raid_start"), 0, 0);
        logger.room("room_entry", "Tekton", 50, 45, "room_entry_observed");
        logger.close();

        assertEquals(2, fixtureFiles(root).size());
        assertTrue(Files.list(root.resolve("cox-assistant/point-fixtures/full")).findAny().isPresent());
        assertTrue(Files.list(root.resolve("cox-assistant/point-fixtures/cm")).findAny().isPresent());
    }

    private static PointFixtureLogger.Metadata metadata(String mode, String startReason)
    {
        return new PointFixtureLogger.Metadata(mode, startReason, "ABCDEF", 100, "detected",
            8_900, "compatible_party_sum", 99, "manual_override");
    }

    private static String readOnlyFixture(Path root) throws Exception
    {
        List<Path> files = fixtureFiles(root);
        assertEquals(1, files.size());
        return new String(Files.readAllBytes(files.get(0)), StandardCharsets.UTF_8);
    }

    private static List<Path> fixtureFiles(Path root) throws Exception
    {
        Path fixtureRoot = root.resolve("cox-assistant/point-fixtures");
        if (!Files.exists(fixtureRoot))
        {
            return java.util.Collections.emptyList();
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(fixtureRoot))
        {
            return paths.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .collect(Collectors.toList());
        }
    }

    private static int occurrences(String text, String needle)
    {
        int count = 0;
        for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + needle.length()))
        {
            count++;
        }
        return count;
    }
}
