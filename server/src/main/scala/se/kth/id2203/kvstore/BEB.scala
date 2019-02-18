import se.kth.edx.id2203.core.ExercisePrimitives._
import se.kth.edx.id2203.core.Ports._
import se.sics.kompics.network._
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.{ComponentDefinition => _, Port => _, KompicsEvent}

import scala.collection.immutable.Set
import scala.collection.mutable.ListBuffer

class BasicBroadcast(init: Init[BasicBroadcast]) extends ComponentDefinition {

  //subscriptions
  val pLink = requires[PerfectLink];
  val beb = provides[BestEffortBroadcast];

  //configuration
  val (self, topology) = init match {
    case Init(s: Address, t: Set[Address]@unchecked) => {
      println(s"Initializing BEB with set $t")
      (s, t)
    }
  };

  //handlers
  beb uponEvent {
    case x: BEB_Broadcast => handle {
     for (q <- topology) {
        trigger(PL_Send(q, x) -> pLink);
     }
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, BEB_Broadcast(payload)) => handle {
        trigger(BEB_Deliver(src, payload) -> beb);
    }
  }
}