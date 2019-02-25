import se.kth.edx.id2203.core.ExercisePrimitives.AddressUtils._
import se.kth.edx.id2203.core.ExercisePrimitives.AddressUtils
import se.kth.edx.id2203.core.Ports._
import se.sics.kompics.network._
import se.sics.kompics.sl._
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}
import se.sics.kompics.{KompicsEvent, Start}

import scala.collection.mutable;

class BallotLeaderElection extends Port {
    indication[BLE_Leader];
}

case class BLE_Leader(leader: Address, ballot: Long) extends KompicsEvent;
// TODO continue the BallotLeaderElection implementation
