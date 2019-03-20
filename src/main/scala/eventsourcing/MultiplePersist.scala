package eventsourcing

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object MultiplePersist extends App {

  //Command
  case class Invoice(recipient: String, date: Date, amount: Int)

  //Event
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)
  case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)

  object Account {
    def props(taxId: String, taxAuthority: ActorRef) = Props(new Account(taxId, taxAuthority))
  }

  class Account(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {
    var lastInvoiceRecordId = 0
    var lastTaxRecordId = 0
    var totalAmount = 0

    override def receiveRecover: Receive = {
      case event => log.info(s"Recover event $event")
    }

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, event) =>
        persist(TaxRecord(taxId, lastTaxRecordId, date, totalAmount / 3 )) {
          record =>
            taxAuthority ! record
            lastTaxRecordId += 1
            persist("I hearby declare it is correct") {
              record => taxAuthority ! record
            }
        }
        persist(lastInvoiceRecordId, recipient, date, account) {
          record =>
            taxAuthority ! record
            lastInvoiceRecordId += 1
        }
        //order of persist are guaranteed because persist are also implemented by Actor
        //callbacks are also called in order
    }

    override def persistenceId: String = "simple-account";
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Receive message $message")
    }
  }

  val system = ActorSystem("MultiplePersist")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "IRS")
  val account = system.actorOf(Account.props("My Tax Id", taxAuthority))

  account ! Invoice("My Company", new Date, 2000)
}
