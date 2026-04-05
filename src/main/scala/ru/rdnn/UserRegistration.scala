package ru.rdnn

import ru.rdnn.AppError.{DbError, EmailAlreadyRegistered}
import ru.rdnn.db.Ctx
import ru.rdnn.dbrepositories.{
  Accounts,
  BalanceHistory,
  BalanceHistoryRepository,
  AccountsRepository,
  User,
  UserRepository
}
import zio.{Random, ZIO}

import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

object UserRegistration {

  private val defaultInitialBalance = 1000.0f //TODO temporary solution

  /** Inserts user, one account, and opening balance_history row in a single transaction. */
  def register(
    email: String,
    passwordPlain: String,
    userName: String,
    phone: String
  ): ZIO[
    DataSource with UserRepository with AccountsRepository with BalanceHistoryRepository,
    AppError,
    (UUID, String)
  ] =
    for {
      userRepo     <- ZIO.service[UserRepository]
      accountsRepo <- ZIO.service[AccountsRepository]
      balanceRepo  <- ZIO.service[BalanceHistoryRepository]
      existing     <- userRepo.findUserByEmail(email)
      _            <- ZIO.fail(EmailAlreadyRegistered(email)).when(existing.isDefined)
      passwordHash <- PasswordHash.hash(passwordPlain)
      userId       <- Random.nextUUID
      accountId    <- Random.nextUUID
      accountNumber <- Random.nextLongBetween(1_000_000_000L, 9_999_999_999L).map(n => s"89$n") //!!!
      now = ZonedDateTime.now()
      user = User(
        id = userId,
        user_name = userName,
        email = email,
        password_hash = passwordHash,
        phone = phone,
        created_at = now,
        updated_at = now,
        is_active = true
      )
      account = Accounts(
        id = accountId,
        user_id = userId,
        account_number = accountNumber,
        currency_code = "RUB",
        balance = defaultInitialBalance,
        created_at = now,
        updated_at = now,
        is_active = true
      )
      opening = BalanceHistory(
        id = UUID.randomUUID(),
        account_number = accountNumber,
        old_balance = 0.0f,
        new_balance = defaultInitialBalance,
        amount = defaultInitialBalance,
        created_at = now
      )
      _ <- Ctx.transaction {
        userRepo.insertUser(user) *>
          accountsRepo.insertAccount(account) *>
          balanceRepo.insertNewBalance(opening)
      }.mapError { case e: AppError => e; case t => DbError(t) }
    } yield (userId, accountNumber)

}
