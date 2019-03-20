package eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable

object PersistentEx extends App{

  case class Vote(voterId: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {
    val voters : mutable.Set[String] = new mutable.HashSet[String]()
    val poll : mutable.Map[String, Int] = new mutable.HashMap[String, Int]()

    override def receiveRecover: Receive = {
      case vote @ Vote(voterId, candidate) =>
        log.info(s"Recovered vote $vote")
        handleStateChange(voterId, candidate)
    }

    override def receiveCommand: Receive = {
      case vote @ Vote(voterId, candidate) =>
        if (!voters.contains(vote.voterId)) {
          persist(vote) { _ =>
            log.info(s"Voter $voterId for candidate $candidate")
            handleStateChange(voterId, candidate)
          }
        } else {
          log.warning(s"Voter $voterId trying to vote again")
        }
      case "print" => log.info(s"Current state: voters: $voters, poll: $poll")
    }

    def handleStateChange(voterId: String, candidate: String) : Unit = {
        voters.add(voterId)
        val votes = poll.getOrElse(candidate, 0)
        poll.put(candidate, votes + 1)
    }

    override def persistenceId: String = "simple-voting-station"

  }

  val system = ActorSystem("PersistentEx")
  val votingStation = system.actorOf(Props[VotingStation])

  val votesMap = Map[String, String] (
    "A" -> "X1",
    "B" -> "X2",
    "C" -> "X3",
    "D" -> "X2"
  )

  /*
  votesMap.keys.foreach { voterId =>
    votingStation ! Vote(voterId, votesMap(voterId))
  }
*/
  votingStation ! "print"

  votingStation ! Vote("A", "X2") //this happens after recovery
}
