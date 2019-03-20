package eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object SnapshotDemo extends App {

  //command
  case class ReceivedMessage(content: String)
  case class SentMessage(content: String)

  //event
  case class ReceivedMessageRecord(id: Int, content: String)
  case class SentMessageRecord(id: Int, content: String)


  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner, contact))
  }
  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    var currentMessageId = 0
    val MAX_MESSAGES = 10
    val lastMessages = new mutable.Queue[(String, String)]()
    var commandWithoutCheckpoint = 0

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, content) =>
        log.info(s"Recover received $id $content")
        addQueue(contact, content)
      case SentMessageRecord(id, content) =>
        log.info(s"Recover sent message $id $content")
        addQueue(owner, content)
      case SnapshotOffer(metadata, content) =>
        log.info(s"Recovered snapshot: $metadata")
        content.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    override def receiveCommand: Receive = {
      case ReceivedMessage(content) =>
        persist(ReceivedMessageRecord(currentMessageId, content)) { e =>
          log.info(s"Received message: $content")
          addQueue(contact, content)
          currentMessageId += 1
          checkpoint()
        }
      case SentMessage(content) => {
        persist(SentMessageRecord(currentMessageId, content)) { e =>
          log.info(s"Sent message: $content")
          addQueue(owner, content)
          currentMessageId += 1
          checkpoint()
        }
      }
      case SaveSnapshotSuccess(metadata) => log.info(s"Save snapshot succeed $metadata")
      case SaveSnapshotFailure(metadata, reason) =>
        log.info(s"Save snap faile $metadata")

    }

    private def addQueue(sender: String, content: String) = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, content))
    }

    def checkpoint() : Unit = {
      commandWithoutCheckpoint += 1
      if (commandWithoutCheckpoint >= MAX_MESSAGES) {
        log.info(s"Saving checkpoint")
        saveSnapshot(lastMessages) //async
        commandWithoutCheckpoint = 0
      }
    }

    override def persistenceId: String = s"$owner-$contact-simple-chat"
  }

  val system = ActorSystem("SnapshotDemo")
  val chat = system.actorOf(Chat.props("A1", "S1"))

  for (i <- 1 to 100*100) {
    chat ! ReceivedMessage(s"message $i")
    chat ! SentMessage(s"send message $i")
  }
}
