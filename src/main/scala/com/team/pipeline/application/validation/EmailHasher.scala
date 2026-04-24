package com.team.pipeline.application.validation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Deterministic, pure email hashing.
  *
  * This is intentionally side-effect free; salt is provided at construction time.
  */
trait EmailHasher:
  def hash(email: String): String

object EmailHasher:

  /** Default implementation using SHA-256.
    *
    * Hash format: hex( SHA-256( normalize(email) || salt ) )
    */
  def sha256(salt: String): EmailHasher = new EmailHasher:
    override def hash(email: String): String =
      val normalized = normalizeEmail(email)
      val digest = MessageDigest.getInstance("SHA-256")
      digest.update(normalized.getBytes(StandardCharsets.UTF_8))
      digest.update(salt.getBytes(StandardCharsets.UTF_8))
      val bytes = digest.digest()
      toHex(bytes)

  /** Email normalization for deterministic hashing.
    *
    * Currently: trim + lowercase.
    */
  def normalizeEmail(email: String): String =
    email.trim.toLowerCase

  private def toHex(bytes: Array[Byte]): String =
    val sb = new StringBuilder(bytes.length * 2)
    bytes.foreach { b =>
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
