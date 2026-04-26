package com.team.pipeline.tools

import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import com.team.pipeline.application.parsing.EventParser
import com.team.pipeline.config.AppConfig
import com.team.pipeline.config.KafkaConfig
import com.team.pipeline.infrastructure.file.JsonlInput
import fs2.kafka.Acks
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerSettings

object PublishSampleEvents extends IOApp.Simple:
  override def run: IO[Unit] =
    for
      config <- AppConfig.load
      count <- publish(config)
      _ <- IO.println(
        s"Published $count events from ${config.app.inputFile} to ${config.kafka.topic} at ${config.kafka.bootstrapServers}"
      )
    yield ()

  private[tools] def publish(config: AppConfig): IO[Long] =
    KafkaProducer
      .stream(producerSettings(config.kafka))
      .flatMap { producer =>
        JsonlInput
          .read(config.app.inputFile)
          .map(line => toRecord(config.kafka.topic)(line.value))
          .evalMap(record => producer.produce(ProducerRecords.one(record)).flatten.as(1L))
      }
      .compile
      .fold(0L)(_ + _)

  private[tools] def producerSettings(
      config: KafkaConfig
  ): ProducerSettings[IO, Option[String], String] =
    ProducerSettings[IO, Option[String], String]
      .withBootstrapServers(config.bootstrapServers)
      .withClientId(s"${config.clientId}-publisher")
      .withAcks(Acks.All)
      .withEnableIdempotence(true)

  private[tools] def toRecord(topic: String)(line: String): ProducerRecord[Option[String], String] =
    ProducerRecord(topic, customerKey(line), line)

  private[tools] def customerKey(line: String): Option[String] =
    EventParser.parseLine(line).toOption.map(event => event.customerId.toString)
