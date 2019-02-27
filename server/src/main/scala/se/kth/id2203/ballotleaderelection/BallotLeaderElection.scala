package se.kth.id2203.ballotleaderelection

import se.kth.edx.id2203.core.ExercisePrimitives.AddressUtils._
import se.kth.edx.id2203.core.ExercisePrimitives.AddressUtils
import se.kth.id2203.networking.NetAddress
import se.kth.edx.id2203.core.Ports._
import se.sics.kompics.sl._
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}
import se.sics.kompics.{KompicsEvent, Start}

import scala.collection.mutable;

class BallotLeaderElection extends Port {
    indication[BLE_Leader];
}

case class BLE_Leader(leader: NetAddress, ballot: Long) extends KompicsEvent;
// TODO continue the BallotLeaderElection implementation

//Provided Primitives to use in your implementation

  case class CheckTimeout(timeout: ScheduleTimeout) extends Timeout(timeout);

  case class HeartbeatReq(round: Long, highestBallot: Long) extends KompicsEvent;

  case class HeartbeatResp(round: Long, ballot: Long) extends KompicsEvent;

class GossipLeaderElection(init: Init[GossipLeaderElection]) extends ComponentDefinition {
  private val ballotOne = 0x0100000000l;

  def ballotFromNAddress(n: Int, adr: NetAddress): Long = {
    val nBytes = com.google.common.primitives.Ints.toByteArray(n);
    val addrBytes = com.google.common.primitives.Ints.toByteArray(adr.hashCode());
    val bytes = nBytes ++ addrBytes;
    val r = com.google.common.primitives.Longs.fromByteArray(bytes);
    assert(r > 0); // should not produce negative numbers!
    r
  }

  def incrementBallotBy(ballot: Long, inc: Int): Long = {
    ballot + inc.toLong * ballotOne
  }

  private def incrementBallot(ballot: Long): Long = {
    ballot + ballotOne
  }



  val ble = provides[BallotLeaderElection];
  val pl = requires[PerfectLink];
  val timer = requires[Timer];

  val self = init match {
    case Init(s: NetAddress) => s
  }
  val topology = cfg.getValue[List[NetAddress]]("id2203.project.address");
  val delta = cfg.getValue[Long]("id2203.project.keepAlivePeriod");
  val majority = (topology.size / 2) + 1;



  private var period = cfg.getValue[Long]("ble.simulation.delay");
  private val ballots = mutable.Map.empty[NetAddress, Long];

  private var round = 0l;
  private var ballot = ballotFromNAddress(0, self);

  private var leader: Option[(Long, NetAddress)] = None;
  private var highestBallot: Long = ballot;

  private def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(period);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  private def makeLeader(topProcess: (Long, NetAddress)) {
    /* INSERT YOUR CODE HERE */

  }

    private def checkLeader() {
    var topProcess = self
	  var topBallot = ballot
    var top = (self , ballot)
    var union = ballots + ((self , ballot))
    top = union.maxBy(_._2)
    topProcess = top._1
    topBallot = top._2
       if ( topBallot < highestBallot ) {
           while ( ballot <= highestBallot ) {
               ballot = incrementBallotBy(ballot, 1);
           }
           leader = None
       } else {
            if( Option(topBallot , topProcess) != leader ) {

                  highestBallot = topBallot
                  leader=Option(topBallot , topProcess)
                  trigger( BLE_Leader( topProcess, topBallot ) -> ble )

            }
       }
  }

  ctrl uponEvent {
    case _: Start => handle {
      startTimer(period);
    }
  }

  timer uponEvent {
    case CheckTimeout(_) => handle {

        if (ballots.size +1 >=  majority  ) {

             checkLeader()
        }
        ballots.clear
         round = round + 1
        for (p <- topology) {
            if (p != self) {
                trigger(PL_Send(p, HeartbeatReq(round, highestBallot)) -> pl)
            }
        }
      startTimer(period)
    /* INSERT YOUR CODE HERE */
  }
  }

  pl uponEvent {
    case PL_Deliver(src: NetAddress, HeartbeatReq(r, hb)) => handle {
         /* INSERT YOUR CODE HERE */
        if (hb > highestBallot) {
            highestBallot = hb
        }
        trigger(PL_Send(src, HeartbeatResp(r, ballot)) -> pl)

    }
    case PL_Deliver(src: NetAddress, HeartbeatResp(r, b)) => handle {
      /* INSERT YOUR CODE HERE */

        if (r == round) {
                      ballots += ( (src, b) )
        }
        else {
            period = period + delta
        }
    }
  }
}
