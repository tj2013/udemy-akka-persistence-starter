package eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(content: String)
  case class Event(id:Int, content: String)

  class RecoverActor extends PersistentActor with ActorLogging {
    var lastedPersistedEventId = 0

    override def receiveRecover: Receive = {
      case Event(id, content) =>
        if (content.contains("44"))
          throw new RuntimeException(s"Can not recover $content")
        log.info(s"Recover $content")
        context.become(online(id+1))
    }

    def online(lastestPersistedEventId:Int) : Receive = {
      case RecoveryCompleted =>
        log.info("Finished recovery")
      case Command(content) =>
        persist(Event(lastestPersistedEventId, content)) { event =>
          log.info(s"Persist $event, recovery is ${if (this.recoveryFinished) "" else "Not"} finished")
          //not useful here before until recovered finished, this method won't be called
          context.become(online(lastestPersistedEventId+1)) //receiveRecover won't be changed by this method
        }
    }

    override def receiveCommand: Receive = online(0)

    override def persistenceId: String = "recover-actor"

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error(s"Fail to recover")
      super.onRecoveryFailure(cause, event) //the actor will be stopped after onRecoveryFailure is called
    }

    //override def recovery: Recovery = Recovery(toSequenceNr = 30) //recover upto 30, useful for debugging
    //override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
    //override def recovery: Recovery = Recovery.none
  }

  val system = ActorSystem("RecoveryDemo")
  val actor = system.actorOf(Props[RecoverActor], "recover-actor")

  for(i <- 1 to 100) {
    actor ! Command(s"Message $i")
  }

  //All message sent during recovery ar stashed (wait for recovery first)


}
