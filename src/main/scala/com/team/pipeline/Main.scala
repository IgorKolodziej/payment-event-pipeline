package com.team.pipeline

import cats.effect.{IO, IOApp}
import com.team.pipeline.config.AppConfig

import java.nio.file.Files

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      config <- AppConfig.load
      _ <- IO.blocking(Files.createDirectories(config.app.outputDir))
      _ <- IO.println(
        s"Payment Event Processing Pipeline started. input=${config.app.inputFile}, output=${config.app.outputDir}"
      )
    yield ()
