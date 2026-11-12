package com.codeguard.queue;

import com.codeguard.review.*;
import com.codeguard.pullrequest.PullRequest;
import com.codeguard.repository.CodeRepository;
import com.codeguard.github.GithubInstallation;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewQueueTest {
    @Test void publisherRequiresAckAndNoReturnAndUsesPersistentIdOnlyMessages() {
        var rabbit = mock(RabbitTemplate.class);
        var id = UUID.randomUUID();
        doAnswer(call -> {
            Message message = call.getArgument(2);
            assertThat(message.getBody()).isEqualTo(id.toString().getBytes(StandardCharsets.UTF_8));
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq(ReviewQueueConfig.EXCHANGE), eq("review"), any(Message.class), any(CorrelationData.class));
        var publisher = new RabbitReviewPublisher(rabbit);
        assertThat(publisher.publish(id, ReviewOutbox.Kind.REVIEW)).isTrue();
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", ReviewQueueConfig.DEAD_EXCHANGE, "review"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null)); return null;
        }).when(rabbit).send(eq(ReviewQueueConfig.DEAD_EXCHANGE), eq("review"), any(Message.class), any(CorrelationData.class));
        assertThat(publisher.publish(id, ReviewOutbox.Kind.DEAD_LETTER)).isFalse();
    }
    @Test void failedConfirmationRetainsOutboxAndSuccessMarksQueued() {
        var jobs = mock(ReviewJobStore.class); var outbox = mock(ReviewOutboxStore.class); var publisher = mock(ReviewMessagePublisher.class);
        var now = Instant.now().plusSeconds(1);
        var repo = new CodeRepository(1, new GithubInstallation(1, "team"), "team/repo", false, "Java");
        var job = new ReviewJob(new PullRequest(repo, 1, "Fix", "dev", "a".repeat(40), "fix", "main", "open"));
        var message = new ReviewOutbox(job.getId(), ReviewOutbox.Kind.REVIEW, now);
        when(jobs.lockById(job.getId())).thenReturn(Optional.of(job));
        when(outbox.findByJobIdAndKind(job.getId(), ReviewOutbox.Kind.REVIEW)).thenReturn(Optional.of(message));
        var delivery = new OutboxDelivery(jobs, outbox, publisher, Clock.fixed(now, ZoneOffset.UTC));
        delivery.deliver(job.getId(), ReviewOutbox.Kind.REVIEW);
        assertThat(message.getSentAt()).isNull(); assertThat(message.getAvailableAt()).isEqualTo(now.plusSeconds(5));
        assertThat(job.getStatus()).isEqualTo(ReviewStatus.PENDING);
        when(publisher.publish(job.getId(), ReviewOutbox.Kind.REVIEW)).thenReturn(true);
        var later = new OutboxDelivery(jobs, outbox, publisher, Clock.fixed(now.plusSeconds(5), ZoneOffset.UTC));
        later.deliver(job.getId(), ReviewOutbox.Kind.REVIEW); later.deliver(job.getId(), ReviewOutbox.Kind.REVIEW);
        assertThat(job.getStatus()).isEqualTo(ReviewStatus.QUEUED);
        assertThat(message.getSentAt()).isNotNull();
        verify(publisher, times(2)).publish(job.getId(), ReviewOutbox.Kind.REVIEW);
    }
    @Test void listenerRejectsMalformedPayloadAndDispatchesOnlyUuid() {
        var worker = mock(ReviewWorker.class); var listener = new ReviewQueueListener(worker);
        assertThatThrownBy(() -> listener.receive(new Message("not-a-job".getBytes(StandardCharsets.US_ASCII)))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        var id = UUID.randomUUID();
        listener.receive(new Message(id.toString().getBytes(StandardCharsets.US_ASCII)));
        verify(worker).process(id); verifyNoMoreInteractions(worker);
    }
}
