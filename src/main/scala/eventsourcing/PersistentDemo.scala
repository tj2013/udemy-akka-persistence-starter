package eventsourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentDemo extends App{

  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])
  case object Shutdown

  class Accountant extends PersistentActor  with ActorLogging {
    var lastInvoiceId = 0
    var totalAmount = 0

    //called when actor started
    override def receiveRecover: Receive = {
      case InvoiceRecorded(id, _, _, amount) =>
        log.info(s"Recovered invoice #$id for amount $amount")
        lastInvoiceId = id
        totalAmount += amount
    }

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        log.info(s"Receiving invoice for $amount")
        val event = InvoiceRecorded(lastInvoiceId, recipient, date, amount)
        persist(event)
        //NEVER call persist or persistAll from Future
        //Time gap, all other messages send to this actors are stashed
        { e =>
          //SAFE to modify mutable state
          //OK to call sender()
          lastInvoiceId += 1
          totalAmount += amount
          log.info(s"Persisted $e as invoice #${e.id}, for total amount $totalAmount")
        } //async blocking call
      case InvoiceBulk(invoices) =>
        val invoiceIds = lastInvoiceId to (lastInvoiceId + invoices.length)
        val events = invoices.zip(invoiceIds).map{pair =>
          InvoiceRecorded(pair._2, pair._1.recipient, pair._1.date, pair._1.amount)}
        persistAll(events) { //for each event persisted, this callback will be called
          e =>
            lastInvoiceId += 1
            totalAmount += e.amount
            log.info(s"Persisted $e as invoice #${e.id}, for total amount $totalAmount")
        }
      case Shutdown => context.stop(self)
    }

    //which actor write an event in persistent store
    //better to be unique
    //during recovering, persistenceId is used to retrieve event from store
    override def persistenceId: String = "simple-accountant"

    //called if persistent fail
    //actor will stop regardless supervision policy
    //Best practice: start actor after a while use backoff supervisor
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    //called if journal fail to persist
    //actor is resumed
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentDemo")
  val accountant = system.actorOf(Props[Accountant])

  //Change application.conf
  //add leveldb as persistence store
  //set target/rtjvm/journal as directory of store
  //create directory rtjvm under target
  //Need to delete directory journal before rerun
  /*
  for(i <- 1 to 10) {
    accountant ! Invoice("My company", new Date, i * 100)
  }
  */
  //val invoices = (1 to 10).map(i => Invoice("My company", new Date, i * 100)).toList
  //accountant ! InvoiceBulk(invoices)

  //Shutdown actor
  //between persist are called and callback is called, other messages are stashed
  //but PoisonPill will arrive to actor earlier and shut it down
  //so those messages are lost
  //The solution is to use a customer-defined message Shutdown, which arrive in the same mailbox as other messages
  // DO NOT use PoisonPill
}
