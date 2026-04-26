package com.team.pipeline.infrastructure.redpanda

import com.team.pipeline.config.KafkaConfig
import com.team.pipeline.ports.EventSource
import fs2.kafka.ConsumerRecord
import munit.FunSuite
import org.apache.kafka.clients.consumer.ConsumerConfig

class RedpandaEventSourceTest extends FunSuite:
  test("builds consumer settings from Kafka config") {
    val settings = RedpandaEventSource.consumerSettings(config)
    val properties = settings.properties

    assertEquals(properties(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG), config.bootstrapServers)
    assertEquals(properties(ConsumerConfig.GROUP_ID_CONFIG), config.groupId)
    assertEquals(properties(ConsumerConfig.CLIENT_ID_CONFIG), config.clientId)
    assertEquals(properties(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), "earliest")
    assertEquals(properties(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG), "false")
  }

  test("maps consumed Kafka records to generic input lines") {
    val record = ConsumerRecord[String, String](
      topic = config.topic,
      partition = 0,
      offset = 41L,
      key = "10",
      value = """{"eventId":100}"""
    )

    assertEquals(
      RedpandaEventSource.inputLine(record),
      EventSource.InputLine(lineNumber = 42, value = """{"eventId":100}""")
    )
  }

  private val config = KafkaConfig(
    bootstrapServers = "localhost:19092",
    topic = "payment-events",
    groupId = "payment-event-pipeline",
    clientId = "payment-event-pipeline-test"
  )
end RedpandaEventSourceTest
