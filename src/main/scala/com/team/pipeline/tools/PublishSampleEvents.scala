package com.team.pipeline.tools

import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import com.team.pipeline.application.parsing.EventParser
import com.team.pipeline.config.AppConfig
import com.team.pipeline.config.KafkaConfig
import com.team.pipeline.domain.CustomerId.*
import com.team.pipeline.infrastructure.file.JsonlInput
import fs2.Stream
import fs2.kafka.Acks
import fs2.kafka.KafkaProducer
import fs2.kafka.ProducerRecord
import fs2.kafka.ProducerRecords
import fs2.kafka.ProducerSettings
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS

object PublishSampleEvents extends IOApp.Simple:
  final case class PublisherSettings(delay: FiniteDuration)

  override def run: IO[Unit] =
    for
      config <- AppConfig.load
      settings <- publisherSettings
      count <- publish(config, settings)
      _ <- IO.println(
        s"Published $count events from ${config.app.inputFile} to ${config.kafka.topic} " +
          s"at ${config.kafka.bootstrapServers} (${delaySummary(settings)})"
      )
    yield ()

  private[tools] def publish(
      config: AppConfig,
      settings: PublisherSettings = PublisherSettings(FiniteDuration(0, MILLISECONDS))
  ): IO[Long] =
    KafkaProducer
      .stream(producerSettings(config.kafka))
      .flatMap { producer =>
        paced(
          JsonlInput
            .read(config.app.inputFile)
            .map(line => toRecord(config.kafka.topic)(line.value)),
          settings
        )
          .evalMap(record => producer.produce(ProducerRecords.one(record)).flatten.as(1L))
      }
      .compile
      .fold(0L)(_ + _)

  private[tools] def publisherSettings: IO[PublisherSettings] =
    IO.envForIO
      .get("PUBLISH_DELAY_MILLIS")
      .map(parseDelay)

  private[tools] def parseDelay(raw: Option[String]): PublisherSettings =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None =>
        PublisherSettings(FiniteDuration(0, MILLISECONDS))
      case Some(value) =>
        value.toLongOption match
          case Some(millis) if millis >= 0 =>
            PublisherSettings(FiniteDuration(millis, MILLISECONDS))
          case Some(_) =>
            throw IllegalArgumentException(
              "PUBLISH_DELAY_MILLIS must be greater than or equal to 0"
            )
          case None =>
            throw IllegalArgumentException(
              s"PUBLISH_DELAY_MILLIS must be an integer number of milliseconds (e.g. 0, 250, 1000), but got: '$value'"
            )

  private[tools] def paced[A](stream: Stream[IO, A], settings: PublisherSettings): Stream[IO, A] =
    if settings.delay.length > 0 then stream.meteredStartImmediately(settings.delay)
    else stream

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
    EventParser.parseLine(line).toOption.map(event => event.customerId.value.toString)

  private def delaySummary(settings: PublisherSettings): String =
    if settings.delay.length > 0 then s"delay=${settings.delay.toMillis} ms"
    else "no delay"
