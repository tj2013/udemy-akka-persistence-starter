package store

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object PostgresDemo extends App {

  val system = ActorSystem("Postgres", ConfigFactory.load().getConfig("postgresdemo"))

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
      build.sbt add dependency
      Modify configuration
      in docker-compose.yml, see
      volumes:
      - "./sql:/docker-entrypoint-initdb.d"
      it mount the ./sql directory to directory /docker-entrypoint-initdb.d in container, when docker starts,
      all script inside this directory is executed (the postgresql image will make it happen)
      Open 2 shells, one to launch docker container, the other inspect db
      In Mac, launch docker.app, wait for it says "is running"
      docker-compose up
      ./psql.sh
      select * from public.journal;
      should be an empty table
      run this app
      select * from public.journal;
      should see 20 rows
      select * from public.snapshot;
      should see 1 row
      rerun app, make sure it can recover messages
     */
}
