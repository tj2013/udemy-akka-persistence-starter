package part4

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object EventAdapter extends App {

  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC)
  case class AddGuitar(guitar: Guitar, quantity: Int)
  case class GuitarAdded(id: String, model: String, make: String, quantity: Int)
  case class GuitarAddedV2(id: String, model: String, make: String, quantity: Int, guitarType: String = ACOUSTIC)

  class InventoryManager extends PersistentActor with ActorLogging {

    val inventory : mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()

    def addGuitar(guitar: Guitar, quantity: Int) = {
      val existing = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existing + quantity)
    }

    override def receiveRecover: Receive = {
      case event @ GuitarAddedV2(id, model, make, quantity, guitarType) =>
        log.info(s"Recover $event")
        val guitar = Guitar(id, model, make, guitarType)
        addGuitar(guitar, quantity)
    }

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, quantity, guitarType)) { _ =>
          log.info(s"added $quantity $guitar to inventory")
          addGuitar(guitar, quantity)
      }
      case "print" =>
        log.info(s"current inventory is $inventory")
    }

    override def persistenceId: String = "guitar-inventory-manager"
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(id, model, make, quantity) =>
        EventSeq.single(GuitarAddedV2(id, model, make, quantity, ACOUSTIC))
      case other => EventSeq.single(other)

    }

    /*
      Journal -> Serializer -> ReadEventAdapter
     */
  }

  //There is also WriteEventAdapter, has def toJournal

  val system = ActorSystem("eventadapters", ConfigFactory.load().getConfig("eventAdapterDemo"))
  val actor = system.actorOf(Props[InventoryManager])

  val guitars = for(i <- 1 to 10) yield Guitar(s"$i", s"Model$i", s"Make$i")
/*
  guitars.foreach(guitar =>
    actor ! AddGuitar(guitar, 5)
  )
*/
  //after save a few event, we want to add electric guitar
  //recover will fail because the saved event are not compatible with new schema

  /* Solution1
     Recover handle both GuitarAdded and GuitarAddV2
   */

  // Solution2 - use EventAdapter
}
