package store

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

case class RegisterUser(email: String, name: String)
case class UserRegistered(id: Int, email: String, name: String)

class UserRegistrationSerializer extends Serializer {
  val SEPARATOR = "//"
  override def identifier: Int = 33333

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id, email, name) =>
      println(s"Serializing $event")
      s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes()
    case _ => throw new IllegalArgumentException("Only UserRegistered event supported")
  }

  override def includeManifest: Boolean = false

  //if includeManifest return false, manifest parameter pass None
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val str = new String(bytes)
    val str1 = str.substring(1, str.length() -1).split(SEPARATOR)
    val res = UserRegistered(str1(0).toInt, str1(1), str1(2))
    println(s"from $str to $res")
    res
  }
}

class RegistrationActor extends PersistentActor with ActorLogging {
  var currentId = 0

  override def receiveRecover: Receive = {
    case event @ UserRegistered(id, _, _) =>
      log.info(s"Recovered: $event")
      currentId = id
  }

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { e =>
        log.info(s"Persised: $e")
        currentId += 1
      }
  }

  override def persistenceId: String = "user-registration"
}

object CustomSerialization extends App {

  val system = ActorSystem("CustomSerialization", ConfigFactory.load().getConfig("customSerializerDemo"))
  val actor = system.actorOf(Props[RegistrationActor])

  for( i <- 1 to 10) {
    actor ! RegisterUser(s"user_$i@rtjvm.com", s"user $i")
  }

  /*
  ./cql.sh
  select * from akka.messages;
  find a record with user-registration, copy the hex message to a online hex to string converter. It is converted as something like
  [44//user_9@rtjvm.com//user 9]
   */
}
