package store

import akka.actor.ActorLogging
import akka.persistence._

class SimplePersistActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case RecoveryCompleted => log.info("Recover done!")
      case SnapshotOffer(metadata, payload: Int) =>
        log.info(s"Recover snapshot $payload")
        nMessage = payload
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Save snapshot succeed $metadata")
      case SaveSnapshotFailure(_, cause) =>
        log.warning(s"Save snapshot failed $cause")
      case message =>
        log.info(s"Recover $message")
        nMessage += 1

    }

    override def receiveCommand: Receive = {
      case "print" => log.info(s"I have persisted $nMessage messages")
      case "snap" => saveSnapshot(nMessage)
      case message => {
        persist(message) { e =>
          log.info(s"Persisted $message")
          nMessage += 1
        }
      }
    }

    override def persistenceId: String = "simple-persistent-actor"

    var nMessage = 0

  }