package ru.rdnn

import io.getquill.context.ZioJdbc.QIO
import zio.{ULayer, URIO, ZIO, ZLayer}

import javax.sql.DataSource

trait DataService {
  /* функция - должна возвращать список UserAccount*/
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]]
  /* функция - должна обновлять UserAccount */
  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource, Throwable, Unit]
  /* функция - должна производить транзакцию с одного счета на другой */
//  def provideTransaction: ZIO[DataSource, Throwable, Unit]
}

class DataServiceImpl(repository: DataRepository) extends DataService {
  
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]] = {
    repository.listUserAccounts
  }

  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource, Throwable, Unit] = {
    repository.updateUserAccount(userAccount)
  }

    /* 
    реализовать функцию provideTransaction, которая должно осуществлять 
    транзакцию с одного счета на другой 
    * функция в качестве параметров должна принимать счет откуда и куда переводить и сумму
    * если счета не существуют, то должна возвращаться ошибка
    * если счет не имеет достаточного баланса, то должна возвращаться ошибка
    * если транзакция не может быть выполнена, то должна возвращаться ошибка
    * если транзакция выполнена успешно, то должна возвращаться успешная транзакция
    * если транзакция выполнена неуспешно, то должна возвращаться ошибка
    */

}


object DataService {
  def listUserAccounts: ZIO[DataSource with DataService, Throwable, List[UserAccount]] =
    ZIO.service[DataService].flatMap(_.listUserAccounts)
    
  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.updateUserAccount(userAccount))
    
  val live: ZLayer[DataRepository, Nothing, DataService] = 
    ZLayer.fromFunction(new DataServiceImpl(_))
}
