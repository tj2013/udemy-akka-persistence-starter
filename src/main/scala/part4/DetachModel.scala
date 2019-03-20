package part4

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object DomainModel {

  case class User(id: String, name: String, email: String) //email is a new field added, because of the detach, very few change is necessary

  case class Coupon(code: String, amount: Int)

  case class ApplyCoupon(coupon: Coupon, user: User)

  case class CouponApplied(code: String, user: User)

}

object DataModel {
  case class WrittenCouponApplied(code: String, userId: String, name: String)
  case class WrittenCouponAppliedV2(code: String, userId: String, name: String, email: String) //add V2 version
}

class ModelAdapter extends EventAdapter {
  import DomainModel._
  import DataModel._

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event @ WrittenCouponApplied(code, userId, userName) =>
      println(s"Converting $event to domail model")
      EventSeq.single(CouponApplied(code, User(userId, userName, "")))
    case event @ WrittenCouponAppliedV2(code, userId, userName, userEmail) =>
      println(s"Converting $event to domail model")
      EventSeq.single(CouponApplied(code, User(userId, userName, userEmail)))
    case other =>
      EventSeq.single(other)
  }

  override def manifest(event: Any): String = "coupon-model-adapter"

  override def toJournal(event: Any): Any = event match {
    case event @ CouponApplied(code, user) =>
      println(s"Converting $event to data model")
      WrittenCouponAppliedV2(code, user.id, user.name, user.email)
  }
}

object DetachModel extends App {

  import DomainModel._

  //Benefit: when domain model changed, Actor does not need any change
  class CouponManager extends PersistentActor with ActorLogging {
    val coupons : mutable.Map[String, User] = new mutable.HashMap[String, User]()

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(s"Recovered $event")
        coupons.put(code, user)
    }

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) { e =>
            log.info(s"Persisted $e")
            coupons.put(coupon.code, user)
          }
        }
    }

    override def persistenceId: String = "coupon-manager"
  }

  val system = ActorSystem("detach-modle-demo", ConfigFactory.load().getConfig("detachingModels"))
  val actor = system.actorOf(Props[CouponManager])

  for(i <- 5 to 9) {
    val coupon = Coupon(s"mega_$i", 100 * i)
    val user = User(s"id_$i", s"name_$i", s"email_$i")
    actor ! ApplyCoupon(coupon, user)
  }

}
