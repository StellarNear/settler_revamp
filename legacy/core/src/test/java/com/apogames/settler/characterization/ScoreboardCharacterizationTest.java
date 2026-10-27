package com.apogames.settler.characterization;

import com.apogames.settler.game.tiwanaku.GameResult;
import com.apogames.settler.game.tiwanaku.Scoreboard;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ScoreboardCharacterizationTest {
    @Test
    public void serializesTheCurrentSevenFieldDelimitedFormat() throws Exception {
        GameResult result = new GameResult("SMALL", 1234L, 87, 1700000000000L, "ABC", 12, 2);

        assertEquals("SMALL|1234|87|1700000000000|ABC|12|2",
                serialize(new Scoreboard(), result));
    }

    @Test
    public void readsHistoricalThreeFourFiveAndCurrentSevenFieldRecords() throws Exception {
        List<GameResult> results = deserialize(new Scoreboard(),
                "MINI|11|90;SMALL|22|80|33;MEDIUM|44|70|55|ABC;BIG|66|60|77|XYZ|8|1");

        assertEquals(4, results.size());
        assertEquals(0L, results.get(0).getDateMillis());
        assertEquals("", results.get(0).getPlayerTrigram());
        assertEquals(0, results.get(2).getTotalClicks());
        assertEquals(8, results.get(3).getTotalClicks());
    }

    @Test(expected = NumberFormatException.class)
    public void malformedNumericFieldCurrentlyAbortsWholeScoreboardRead() throws Exception {
        deserialize(new Scoreboard(), "MINI|11|90;SMALL|not-a-time|80");
    }

    private static String serialize(Scoreboard scoreboard, GameResult result) throws Exception {
        Method method = Scoreboard.class.getDeclaredMethod("serialize", List.class);
        method.setAccessible(true);
        return (String) method.invoke(scoreboard, java.util.Collections.singletonList(result));
    }

    @SuppressWarnings("unchecked")
    private static List<GameResult> deserialize(Scoreboard scoreboard, String serialized) throws Exception {
        Method method = Scoreboard.class.getDeclaredMethod("deserialize", String.class);
        method.setAccessible(true);
        try {
            return (List<GameResult>) method.invoke(scoreboard, serialized);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw exception;
        }
    }
}
