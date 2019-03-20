package part4

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random


object QueryDemo  extends App {

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case e => log.info(s"Recovered $e")
    }

    override def receiveCommand: Receive = {
      case m =>
        persist(m) { _ =>
          println(s"Persisted $m")
        }
    }

    override def persistenceId: String = "persistence-query-id-1"
  }

  val system = ActorSystem("querydemo", ConfigFactory.load().getConfig("persistenceQuery"))

  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val persistenceIds = readJournal.persistenceIds()

  implicit val materializer = ActorMaterializer()(system)
  persistenceIds.runForeach { id =>
      println(s"Found persistence id $id")
  } //infinite, run the lambda whenever there is update

  val actor = system.actorOf(Props[SimplePersistentActor])

//  import system.dispatcher
//  system.scheduler.scheduleOnce(5 seconds) {
//    actor ! "Hello"
//  }

  val event = readJournal.eventsByPersistenceId("persistence-query-id-1", 0, Long.MaxValue)

  event.runForeach { event =>
    println(s"Read event $event")
  }

  val genres = Array("pop", "rock", "hip-hop", "jazz", "disco")
  case class Song(artist: String, title: String, genre: String)

  //command
  case class Playlist(songs: List[Song])

  //event
  case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {

    var latestPlaylistId = 0

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchased(id, _) =>
        log.info(s"Recovered: $event")
        latestPlaylistId = id
    }

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestPlaylistId, songs)) { _ =>
          log.info(s"User purchased: $songs")
          latestPlaylistId += 1
        }
    }

    override def persistenceId: String = "music-store-checkout"
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "musicStore"

    override def toJournal(event: Any): Any = event match {
      case event @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(event, genres)
      case event => event
    }
  }

  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")

  val r = new Random
  for (_ <- 1 to 10) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", s"My Love Song $i", randomGenre)
    }

    checkoutActor ! Playlist(songs.toList)
  }

  val rockPlaylists = readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlaylists.runForeach { event =>
    println(s"Found a playlist with a rock song: $event")
  }
}
