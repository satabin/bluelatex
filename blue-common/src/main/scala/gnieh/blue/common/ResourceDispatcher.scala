package gnieh.blue
package common

import akka.actor._

import scala.concurrent.Future

final case class Join(username: String, resourceid: String)
final case class Part(username: String, resourceid: Option[String])
final case class Forward(resourceid: String, msg: Any)
final case class Create(username: String, resourceid: String, props: Props)
case object Stop

/** A dispacther that instantiates an actor per resource name.
 *  The instance lives as long as at least one user is using this resource.
 *  Join/Part messages are sent automatically, a user only needs to take care
 *  to send `Forward` messages
 *
 *  @author Lucas Satabin
 */
abstract class ResourceDispatcher extends Actor {

  override def preStart(): Unit = {
    // subscribe to the dispatcher events
    context.system.eventStream.subscribe(self, classOf[Join])
    context.system.eventStream.subscribe(self, classOf[Part])
  }

  override def postStop(): Unit = {
    // unsubscribe from the dispatcher events
    context.system.eventStream.unsubscribe(self, classOf[Join])
    context.system.eventStream.unsubscribe(self, classOf[Part])
  }

  implicit def executor = context.system.dispatcher

  final def receive = running(Map(), Map())

  // users: count the number of users using a given resource by name
  // resources: the set of resources a user is connected to
  def running(users: Map[String, Set[String]], resources: Map[String, Set[String]]): Receive = {
    case Create(username, resourceid, props) =>
      // create the actor and watches it
      context.watch(context.actorOf(props, name = resourceid))
      context.become(running(users.updated(resourceid, Set()), resources))
      // and then rejoin
      self ! Join(username, resourceid)

    case join @ Join(username, resourceid) =>
      // create the actor if nobody uses this resource
      if(!users.contains(resourceid) || users(resourceid).size == 0) {
        for(props <- props(username, resourceid)) {
          Create(username, resourceid, props)
        }
      }

      if(!users(resourceid).contains(username)) {
        // resent the Join message to the resource
        context.actorSelection(resourceid) ! join
      }

      users.addBinding(resourceid, username)
      resources.addBinding(username, resourceid)

    case msg @ Part(username, None) =>
      // for all resources the user is connected to, leave it
      for(resourceid <- resources.get(username).getOrElse(Set())) {
        part(users, resources, username, resourceid, msg)
      }

    case msg @ Part(username, Some(resourceid)) =>
      // notify only the corresponding resource
      part(users, resources, username, resourceid, msg)

    case Forward(resourceid, msg) if users.contains(resourceid) =>
      // forward the actual message to the resource instance
      context.actorSelection(resourceid).tell(msg, sender)

    case Forward(resourceid, msg) =>
      // execute the handler for unknown target resource
      unknownReceiver(resourceid, msg)

    case Stop =>
      context.become(stopping(users, resources))
      // dispatch the Stop message to all managed actors
      context.actorSelection("*") ! Stop
      // and stop the managed actors
      context.actorSelection("*") ! PoisonPill

    case Terminated(actor) =>
      // a managed actor was terminated, remove it from the set of managed resources
      terminated(users, resources, false, actor.path.name)

  }

  def stopping(users: Map[String, Set[String]], resources: Map[String, Set[String]]): Receive = {

    case Terminated(actor) =>
      // a managed actor was terminated, remove it from the set of managed resources
      terminated(users, resources, true, actor.path.name)
      // when all our managed actors terminated, kill ourselves
      if(users.isEmpty)
        self ! PoisonPill

    case Forward(resourceid, msg) =>
      // any forward message is sent to the unknown resource hook
      unknownReceiver(resourceid, msg)

  }

  /** Implement this method to specify how an actor for a yet unknown resource is created */
  def props(username: String, resourceid: String): Future[Props]

  private def part(
    users: Map[String, Set[String]],
    resources: Map[String, Set[String]],
    username: String,
    resourceid: String,
    msg: Part): Unit = {
    val actor = context.actorSelection(resourceid)

    val users1 = if(users.contains(resourceid) && users(resourceid).size == 1) {
      // this was the last user on this resource, kill it
      // after having performed proper cleanup if needed
      actor ! Stop
      actor ! PoisonPill
      users - resourceid
    } else if(users.contains(resourceid) && users(resourceid).contains(username)) {
      // just tell the user left
      actor ! msg
      users.updated(resourceid, users(resourceid) - username)
    } else {
      users
    }
    val resources1 = if(resources.contains(username) && resources(username).size == 1) {
      // this was the last resource the user is connected to
      resources - username
    } else if(resources.contains(username)) {
      resources.updated(username, resources(username) - resourceid)
    } else {
      resources
    }
    context.become(running(users1, resources1))
  }

  private def terminated(
    users: Map[String, Set[String]],
    resources: Map[String, Set[String]],
    isStopping: Boolean,
    resourceid: String): Unit = {
    // remove this resource from the map of resource to connected users
    val users1 = users - resourceid
    // remove this resource from the resources each user if connected to
    val resources1 = resources.foldLeft(resources) { case (map, (key, set)) =>
      map.updated(key, set - resourceid)
    }
    if(isStopping)
      context.become(stopping(users1, resources1))
    else
      context.become(running(users1, resources1))
  }

  /** This method is called when the resource to forward the message is unknown.
   *  Override this to handle such undelivered message as you wish. By default dows nothing */
  protected def unknownReceiver(resourceid: String, msg: Any): Unit = {
  }

}
