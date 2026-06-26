package ru.yandex.practicum.mybank.notifications;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import ru.yandex.practicum.mybank.notifications.domain.NotificationRepository;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 1, topics = {"notifications"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class NotificationsKafkaIntegrationTest extends AbstractNotificationsIntegrationTest {

    @Autowired NotificationRepository repository;
    @Autowired EmbeddedKafkaBroker broker;

    private Producer<String, String> producer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(props);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close(java.time.Duration.ofSeconds(5));
        }
    }

    private void send(String key, String payload) throws Exception {
        producer.send(new ProducerRecord<>("notifications", key, payload)).get();
    }

    // --- happy path ---

    @Test
    void consumesStructuredEventAndPersists() throws Exception {
        String eventId = UUID.randomUUID().toString();
        send("alice", structured(eventId, "alice", "BALANCE_CREDIT", "100.00"));

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> repository.existsByEventId(eventId));

        var saved = repository.findAll().stream()
                .filter(n -> eventId.equals(n.getEventId())).findFirst().orElseThrow();
        assertThat(saved.getLogin()).isEqualTo("alice");
        assertThat(saved.getKind()).isEqualTo("BALANCE_CREDIT");
        assertThat(saved.getMessage()).contains("100.00");
    }

    // --- idempotency ---

    @Test
    void duplicateEventIsIgnored() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = structured(eventId, "bob", "BALANCE_DEBIT", "50.00");
        send("bob", payload);
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> repository.existsByEventId(eventId));

        send("bob", payload); // duplicate
        Thread.sleep(500);    // let consumer process if it would

        long count = repository.findAll().stream()
                .filter(n -> eventId.equals(n.getEventId())).count();
        assertThat(count).isEqualTo(1);
    }

    // --- negative: malformed JSON ---

    @Test
    void malformedJsonIsSkippedWithoutError() throws Exception {
        send("alice", "not-json{{{");
        Thread.sleep(500);
        assertThat(repository.findAll()).isEmpty();
    }

    // --- negative: missing required fields ---

    @Test
    void missingEventIdIsSkipped() throws Exception {
        send("alice", "{\"login\":\"alice\",\"kind\":\"BALANCE_CREDIT\",\"amount\":10}");
        Thread.sleep(500);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void missingLoginIsSkipped() throws Exception {
        String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"kind\":\"BALANCE_CREDIT\",\"amount\":10}";
        send("alice", payload);
        Thread.sleep(500);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void missingKindIsSkipped() throws Exception {
        String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"login\":\"alice\",\"amount\":10}";
        send("alice", payload);
        Thread.sleep(500);
        assertThat(repository.findAll()).isEmpty();
    }

    private static String structured(String eventId, String login, String kind, String amount) {
        return String.format(
                "{\"eventId\":\"%s\",\"login\":\"%s\",\"kind\":\"%s\",\"amount\":%s,\"currency\":\"RUB\",\"createdAt\":\"2026-01-01T00:00:00Z\"}",
                eventId, login, kind, amount);
    }
}
