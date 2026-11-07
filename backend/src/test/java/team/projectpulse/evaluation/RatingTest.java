package team.projectpulse.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.projectpulse.rubric.Criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-EVA-submit-evaluation: a score is an integer within the criterion's range. Every write of a score, on create and on
 * update, goes through {@link Rating#setActualScore}.
 */
class RatingTest {

    private final Criterion criterion = new Criterion("Quality of work", "How do you rate the quality of this teammate's work? (1-10)", 10.0);


    @ParameterizedTest
    @ValueSource(doubles = {0.0, 7.0, 10.0})
    void testWholeNumberWithinRangeIsAccepted(double score) {
        assertThat(new Rating(this.criterion, score).getActualScore()).isEqualTo(score);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 7.5, 9.99})
    void testFractionalScoreIsRefused(double score) {
        assertThatThrownBy(() -> new Rating(this.criterion, score))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Actual score must be a whole number.");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 11.0, 10.5})
    void testScoreOutOfRangeIsRefused(double score) {
        assertThatThrownBy(() -> new Rating(this.criterion, score))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Actual score must be between 0 and 10.0");
    }

    @Test
    void testRefusedScoreLeavesTheExistingScoreInPlace() {
        Rating rating = new Rating(this.criterion, 7.0);

        assertThatThrownBy(() -> rating.setActualScore(7.5)).isInstanceOf(IllegalArgumentException.class);

        assertThat(rating.getActualScore()).isEqualTo(7.0);
    }

}
