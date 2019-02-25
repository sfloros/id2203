package se.kth.id2203.failuredetector

import se.kth.id2203.broadcast._
import se.kth.id2203.networking.NetAddress
import se.kth.edx.id2203.core.ExercisePrimitives._
import se.sics.kompics.network._
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}
import se.sics.kompics.{KompicsEvent, Start, ComponentDefinition => _, Port => _}

class EventuallyPerfectFailureDetector extends Port {
  indication[Suspect];
}

case class Suspect(process: NetAddress) extends KompicsEvent;
case class Restore(process: NetAddress) extends KompicsEvent;

case class CheckTimeout(timeout: ScheduleTimeout) extends Timeout(timeout);

case class HeartbeatReply(seq: Int) extends KompicsEvent;
case class HeartbeatRequest(seq: Int) extends KompicsEvent;

//Define EPFD Implementation
class EPFD() extends ComponentDefinition {

  //EPFD subscriptions
  val timer = requires[Timer];
  val pLink = requires(PerfectLink);
  val epfd = provides[EventuallyPerfectFailureDetector];


  // EPDF component state and initialization

  //configuration parameters

  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val topology = List[NetAddress]();
  val delta = cfg.getValue[Long]("id2203.project.keepAlivePeriod");

  //mutable state
  var period = delta;
  // What should the alive value be?
  var alive = Set[NetAddress]();
  var suspected = Set[NetAddress]();
  var seqnum = 0;


  def startTimer(delay: Long): Unit = {
  val scheduledTimeout = new ScheduleTimeout(period);
  scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
  trigger(scheduledTimeout -> timer);
}


//EPFD event handlers
ctrl uponEvent {
  case _: Start => handle {
      seqnum = 0;
      alive = Set[NetAddress]();
       suspected = Set[NetAddress]();
       startTimer(period);
  }
}

timer uponEvent {
  case CheckTimeout(_) => handle {
    if (!alive.intersect(suspected).isEmpty) {
        period = period + delta;

    }
    seqnum = seqnum + 1;

      for (p <- topology) {
        if (!alive.contains(p) && !suspected.contains(p)) {

          suspected = suspected + p;
          trigger(Suspect(p) -> epfd);

        } else if (alive.contains(p) && suspected.contains(p)) {
          suspected = suspected - p;
          trigger(Restore(p) -> epfd);
        }
        trigger(PL_Send(p, HeartbeatRequest(seqnum)) -> pLink);
      }
      alive = Set[NetAddress]();
      startTimer(period); // Do we keep the same period?
    }
  }


  pLink uponEvent {
  case PL_Deliver(src, HeartbeatRequest(seq)) => handle {

    trigger(PL_Send(src, HeartbeatRequest(seq)) -> pLink);

  }
  case PL_Deliver(src, HeartbeatReply(seq)) => handle {

      if(seq == seqnum || suspected.contains(src)) {
          alive = alive + src;
      }

  }
}
};
