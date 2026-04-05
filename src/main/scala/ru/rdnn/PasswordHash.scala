package ru.rdnn

import org.mindrot.jbcrypt.BCrypt
import zio.ZIO

object PasswordHash {

  def hash(plain: String): ZIO[Any, AppError, String] =
    ZIO.attempt(BCrypt.hashpw(plain, BCrypt.gensalt())).mapError(e => AppError.DbError(e))

  def verify(plain: String, hash: String): ZIO[Any, AppError, Boolean] =
    ZIO.attempt(BCrypt.checkpw(plain, hash)).mapError(e => AppError.DbError(e))
}
