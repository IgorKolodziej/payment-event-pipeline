package com.team.pipeline.infrastructure.redpanda

import cats.effect.IO
import com.team.pipeline.config.KafkaConfig
import com.team.pipeline.ports.EventSource
import fs2.Stream
import fs2.kafka.AutoOffsetReset
import fs2.kafka.ConsumerSettings
import fs2.kafka.KafkaConsumer

final class RedpandaEventSource(config: KafkaConfig) extends EventSource:
  override def events: Stream[IO, EventSource.InputLine] =
    KafkaConsumer
      .stream(RedpandaEventSource.consumerSettings(config))
      .subscribeTo(config.topic)
      .records
      .map(_.record.value)
      .zipWithIndex
      .map { case (value, index) =>
        RedpandaEventSource.inputLine(value, index)
      }

object RedpandaEventSource:
  private[redpanda] def consumerSettings(
      config: KafkaConfig
  ): ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withBootstrapServers(config.bootstrapServers)
      .withGroupId(config.groupId)
      .withClientId(config.clientId)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withEnableAutoCommit(false)

  private[redpanda] def inputLine(value: String, index: Long): EventSource.InputLine =
    EventSource.InputLine(
      lineNumber = index + 1,
      value = value
    )
