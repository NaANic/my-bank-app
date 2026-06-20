package ru.yandex.practicum.mybank.notifications;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import ru.yandex.practicum.mybank.notifications.domain.Notification;
import ru.yandex.practicum.mybank.notifications.domain.NotificationRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 1, topics = {"notifications"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class NotificationsKafkaIntegrationTest extends AbstractNotificationsIntegrationTest {

    @Autowired
    NotificationRepository repository;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void consumesKafkaMessageAndPersists() throws Exception {
        repository.deleteAll();

        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        String payload = "{\"login\":\"alice\",\"kind\":\"cash_deposit\",\"message\":\"Deposit: 100\"}";
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("notifications", "alice", payload)).get();
        }

        Notification saved = null;
        for (int i = 0; i < 50 && saved == null; i++) {
            List<Notification> all = repository.findAll();
            if (!all.isEmpty()) {
                saved = all.get(0);
            } else {
                Thread.sleep(200);
            }
        }

        assertThat(saved).isNotNull();
        assertThat(saved.getLogin()).isEqualTo("alice");
        assertThat(saved.getKind()).isEqualTo("cash_deposit");
        assertThat(saved.getMessage()).contains("100");
    }
}
