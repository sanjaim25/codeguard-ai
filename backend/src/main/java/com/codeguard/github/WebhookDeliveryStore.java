package com.codeguard.github;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WebhookDeliveryStore extends JpaRepository<WebhookDelivery, UUID> {
    Optional<WebhookDelivery> findByDeliveryId(String deliveryId);
}
