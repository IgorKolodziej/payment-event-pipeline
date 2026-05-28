package com.team.pipeline.reporting

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import io.circe.Encoder
import io.circe.syntax._

object JsonWriters {
  def writeJsonToFile[A: Encoder](value: A, outPath: Path): Unit = {
    val parent = outPath.getParent
    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
    val json = value.asJson.spaces2
    Files.write(outPath, json.getBytes(StandardCharsets.UTF_8))
  }
}
