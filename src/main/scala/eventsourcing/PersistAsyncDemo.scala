package eventsourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  case class Command(content: String)
  case class Event(content: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef) = Props(new CriticalStreamProcessor(eventAggregator))

  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case message => log.info(s"Recover $message")
    }

    override def receiveCommand: Receive = {
      case Command(content) =>
        eventAggregator ! s"Processing $content"
        persistAsync(Event(content)) { e =>
          eventAggregator ! e
        }
        //some actual computation
        val newMsg = content + " processed"
        persistAsync(Event(newMsg)) { e =>
          eventAggregator ! e
        }
    }

    override def persistenceId: String = "critical-stream-processor"
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"$message")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")

  val aggregator = system.actorOf(Props[EventAggregator])

  val processor = system.actorOf(CriticalStreamProcessor.props(aggregator))

  processor ! Command("command1")
  processor ! Command("command2")

  //If CriticalStreamProcessor.receiveCommand use persist, the log is
  //Processing command1
  //Event(command1)
  //Event(command1 processed)
  //Processing command2
  //Event(command2)
  //Event(command2 processed)

  //If CriticalStreamProcessor.receiveCommand use persistAsync, the log is
  //Processing command1
  //Processing command2
  //Event(command1)
  //Event(command1 processed)
  //Event(command2)
  //Event(command2 processed)

  //persistAsync means between persistAsync call and its callback, new command to the actor is not stashed, the actor
  //can handle it.
  //persist and persistAsync are both async call but new commands between persist and its callback are stashed

  /*
    persist vs persistAsync
    1) perf, for persist the entire
      case Command(content) =>
      including callbacks has to be completed before the actor can handle a new command
    2) persist used when command order is required

   */
}
