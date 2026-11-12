package com.codeguard.review;

import com.codeguard.github.GithubInstallation;
import com.codeguard.pullrequest.PullRequest;
import com.codeguard.repository.CodeRepository;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReviewJobTest {
    private ReviewJob job() {
        var repo = new CodeRepository(1, new GithubInstallation(2, "team"), "team/repo", false, "Java");
        return new ReviewJob(new PullRequest(repo, 1, "Fix", "dev", "a".repeat(40), "fix", "main", "open"));
    }
    @Test void claimsOnceAndFencesCompletedAndCancelledJobs() {
        var job = job(); var now = job.getAvailableAt();
        assertThat(job.markQueued()).isTrue();
        var token = job.claim(now, Duration.ofMinutes(2));
        assertThat(token).isNotNull();
        assertThat(job.claim(now, Duration.ofMinutes(2))).isNull();
        job.complete(token, now.plusSeconds(1));
        assertThat(job.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(job.cancel(now)).isFalse();
        assertThatThrownBy(() -> job.complete(token, now)).isInstanceOf(IllegalStateException.class);
        var cancelled = job(); var cancelledAt = cancelled.getAvailableAt();
        var oldToken = cancelled.claim(cancelledAt, Duration.ofMinutes(2));
        assertThat(cancelled.cancel(cancelledAt)).isTrue();
        assertThatThrownBy(() -> cancelled.complete(oldToken, cancelledAt)).isInstanceOf(IllegalStateException.class);
    }
    @Test void retriesAreDelayedAndBounded() {
        var job = job(); var now = job.getAvailableAt();
        for (int attempt = 1; attempt <= 3; attempt++) {
            var token = job.claim(now, Duration.ofMinutes(2));
            job.fail(token, now.plusSeconds(1), "PROVIDER_UNAVAILABLE", true, 3);
            if (attempt < 3) {
                assertThat(job.getStatus()).isEqualTo(ReviewStatus.PENDING);
                assertThat(job.claim(now, Duration.ofMinutes(2))).isNull();
                assertThat(job.getAvailableAt()).isEqualTo(now.plusSeconds(1 + (5L << (attempt - 1))));
                now = job.getAvailableAt();
            }
        }
        assertThat(job.getStatus()).isEqualTo(ReviewStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(3);
    }
    @Test void expiredLeaseRecoveryRejectsStaleWorkers() {
        var job = job(); var now = job.getAvailableAt();
        var oldToken = job.claim(now, Duration.ofSeconds(5));
        assertThat(job.recoverExpired(now.plusSeconds(4), 3)).isFalse();
        assertThat(job.recoverExpired(now.plusSeconds(5), 3)).isTrue();
        var newToken = job.claim(now.plusSeconds(5), Duration.ofSeconds(5));
        assertThatThrownBy(() -> job.complete(oldToken, now.plusSeconds(6))).isInstanceOf(IllegalStateException.class);
        job.complete(newToken, now.plusSeconds(6));
        assertThat(job.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
    }
    @Test void nonRetryableFailuresStopImmediately() {
        var job = job(); var now = job.getAvailableAt();
        job.fail(job.claim(now, Duration.ofMinutes(1)), now, "POLICY_BLOCKED", false, 3);
        assertThat(job.getStatus()).isEqualTo(ReviewStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(1);
    }
}
