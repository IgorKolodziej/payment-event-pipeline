package com.team.pipeline.tools

import com.team.pipeline.config.KafkaConfig
import fs2.Stream
import munit.CatsEffectSuite
import org.apache.kafka.clients.producer.ProducerConfig
import scala.concurrent.duration.*

class PublishSampleEventsTest extends CatsEffectSuite:
  test("uses customer id as record key when the line can be parsed") {
    val record = PublishSampleEvents.toRecord("payment-events")(validLine)

    assertEquals(record.topic, "payment-events")
    assertEquals(record.key, Some("10"))
    assertEquals(record.value, validLine)
  }

  test("keeps malformed lines publishable without a key") {
    val record = PublishSampleEvents.toRecord("payment-events")("not-json")

    assertEquals(record.key, None)
    assertEquals(record.value, "not-json")
  }

  test("builds producer settings from Kafka config") {
    val settings = PublishSampleEvents.producerSettings(config)
    val properties = settings.properties

    assertEquals(properties(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG), config.bootstrapServers)
    assertEquals(properties(ProducerConfig.CLIENT_ID_CONFIG), s"${config.clientId}-publisher")
    assertEquals(properties(ProducerConfig.ACKS_CONFIG), "all")
    assertEquals(properties(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG), "true")
  }

  test("parses missing publish delay as zero") {
    assertEquals(
      PublishSampleEvents.parseDelay(None),
      PublishSampleEvents.PublisherSettings(0.millis)
    )
  }

  test("parses positive publish delay") {
    assertEquals(
      PublishSampleEvents.parseDelay(Some("250")),
      PublishSampleEvents.PublisherSettings(250.millis)
    )
  }

  test("rejects negative publish delay") {
    intercept[IllegalArgumentException] {
      PublishSampleEvents.parseDelay(Some("-1"))
    }
  }

  test("fast publisher mode preserves records without pacing") {
    val records = Stream
      .emits(List("first", "second"))
      .covary[cats.effect.IO]

    PublishSampleEvents
      .paced(records, PublishSampleEvents.PublisherSettings(0.millis))
      .compile
      .toList
      .map(result => assertEquals(result, List("first", "second")))
  }

  private val config = KafkaConfig(
    bootstrapServers = "localhost:19092",
    topic = "payment-events",
    groupId = "payment-event-pipeline",
    clientId = "payment-event-pipeline-test"
  )

  private val validLine =
    """{"eventId":100,"timestamp":"2026-04-24T10:00:00Z","customerId":10,"amount":150.00,"currency":"PLN","status":"SUCCESS","paymentMethod":"BLIK","transactionCountry":"PL","merchantId":"M001","merchantCategory":"GROCERY","channel":"MOBILE","deviceId":"device-001"}"""
end PublishSampleEventsTest
