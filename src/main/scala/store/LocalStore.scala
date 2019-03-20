package store

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object LocalStore extends App {



  val system = ActorSystem("Localstore", ConfigFactory.load().getConfig("localstore"))

  val actor = system.actorOf(Props[SimplePersistActor])

  for (i <- 1 to 10) {
    actor ! s"I love akka $i"
  }

  actor ! "print"
  actor ! "snap"

  for (i <- 11 to 20) {
    actor ! s"I love akka $i"
  }
}
