package store

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object CassandraDemo extends App {

  val system = ActorSystem("Cassandra", ConfigFactory.load().getConfig("cassandrademo"))

  val actor = system.actorOf(Props[SimplePersistActor])

  for (i <- 1 to 10) {
    actor ! s"I love akka $i"
  }

  actor ! "print"
  actor ! "snap"

  for (i <- 11 to 20) {
    actor ! s"I love akka $i"
  }

  /*

  ./cql.sh
  select * from akka.messages;
  select * from akka_snapshot.snapshots;
   */
}
