package com.codeguard.github;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100) private String deliveryId;
    @Column(nullable = false, length = 50) private String eventType;
    @Column(nullable = false, length = 64) private String bodySha256;
    @Column(nullable = false, length = 30) private String outcome;
    private UUID jobId;
    protected WebhookDelivery() {}
    public WebhookDelivery(String deliveryId, String eventType, String bodySha256, String outcome, UUID jobId) {
        this.deliveryId = deliveryId; this.eventType = eventType; this.bodySha256 = bodySha256; this.outcome = outcome; this.jobId = jobId;
    }
    public String getOutcome() { return outcome; }
    public UUID getJobId() { return jobId; }
    public String getBodySha256() { return bodySha256; }
}
