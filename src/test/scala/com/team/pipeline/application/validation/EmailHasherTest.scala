package com.team.pipeline.application.validation

import munit.FunSuite

class EmailHasherTest extends FunSuite:

  test("hashing is deterministic for the same email and salt") {
    val hasher = EmailHasher.sha256("salt")
    val h1 = hasher.hash("alice@example.com")
    val h2 = hasher.hash("alice@example.com")
    assert(h1 == h2)
  }

  test("hashing normalizes email casing and whitespace") {
    val hasher = EmailHasher.sha256("salt")
    val h1 = hasher.hash(" Alice@Example.com ")
    val h2 = hasher.hash("alice@example.com")
    assert(h1 == h2)
  }

  test("different salts produce different hashes") {
    val h1 = EmailHasher.sha256("salt-1").hash("alice@example.com")
    val h2 = EmailHasher.sha256("salt-2").hash("alice@example.com")
    assert(h1 != h2)
  }
