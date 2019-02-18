package se.kth.id2203.kvstore;

import se.kth.id2203.networking._;
//import se.kth.id2203.ports._;
import se.kth.id2203.overlay.Routing;
import se.sics.kompics.sl._;
//Remember to execute the following imports first
import se.sics.kompics.network._
import se.kth.edx.id2203.core.Ports._
import se.kth.edx.id2203.core.ExercisePrimitives._
import se.sics.kompics.{ComponentDefinition => _, Port => _,KompicsEvent}

import scala.collection.mutable.Map
import scala.language.implicitConversions


//The following events are to be used internally by the Atomic Register implementation below
case class READ(rid: Int) extends KompicsEvent;
case class VALUE(rid: Int, ts: Int, wr: Int, value: Option[Any]) extends KompicsEvent;
case class WRITE(rid: Int, ts: Int, wr: Int, writeVal: Option[Any]) extends KompicsEvent;
case class ACK(rid: Int) extends KompicsEvent;

/**
* This augments tuples with comparison operators implicitly, which you can use in your code. 
* examples: (1,2) > (1,4) yields 'false' and  (5,4) <= (7,4) yields 'true' 
*/

class ReadImposeWriteConsultMajority(init: Init[ReadImposeWriteConsultMajority]) extends ComponentDefinition {
  implicit def addComparators[A](x: A)(implicit o: math.Ordering[A]): o.Ops = o.mkOrderingOps(x);

  //subscriptions

  val nnar = provides[AtomicRegister];

  val pLink = requires[Network];
  val beb = requires[BestEffortBroadcast];

  //state and initialization

  val (self: Address, n: Int, selfRank: Int) = init match {
    case Init(selfAddr: Address, n: Int) => (selfAddr, n, AddressUtils.toRank(selfAddr))
    };

    var (ts, wr) = (0, selfRank);
    var value: Option[Any] = None;
    var acks = 0;
    var readval: Option[Any] = None;
    var writeval: Option[Any] = None;
    var rid = 0;
    var readlist: Map[Address, ((Int, Int), Option[Any])] = Map.empty;
    var reading = false;

    //handlers

    nnar uponEvent {
      case AR_Read_Request() => handle {
        val currTime = System.currentTimeMillis();
        rid = rid + 1;
        println(s"$currTime: Process $self does AR_Read_Req current value $value, rid = $rid");
        
        /* WRITE YOUR CODE HERE  */
        acks = 0;
        readlist = Map.empty;
        reading = true;
        trigger(BEB_Broadcast(READ(rid)) -> beb);
      };
      case AR_Write_Request(wval) => handle { 
        rid = rid + 1;
        
        /* WRITE YOUR CODE HERE  */
        writeval = Some(wval);
        val currTime = System.currentTimeMillis();
        println(s"$currTime: Process $self does AR_Write_Req wval: $wval, writeval = $writeval, rid = $rid");
        acks = 0;
        readlist = Map.empty;
        trigger(BEB_Broadcast(READ(rid)) -> beb);
      }
    }

    beb uponEvent {
      case BEB_Deliver(src, READ(readID)) => handle {
        //      println(s"Process $self does BEB_Deliver READ from $src");
        trigger(PL_Send(src, VALUE(readID, ts, wr, value)) -> pLink);
      }
      case BEB_Deliver(src, w: WRITE) => handle {
        //      println(s"Process $self does BEB_Deliver WRITE from $src");
        var tsp = w.ts;
        var wrp = w.wr;
        var comp = ((w.ts, w.wr) > (ts, wr));
        var newval = w.writeVal;
        println(s"Process $self write? ts' = $tsp, wr' = $wrp > ts = $ts, wr = $wr: $comp ($newval)");
        if ((w.ts, w.wr) > (ts, wr)){
          ts = w.ts;
          wr = w.wr;
          //          println(s"Process $self writes new value $w.writeVal, overwriting $value");
          value = w.writeVal;
        }
        trigger(PL_Send(src, ACK(w.rid)) -> pLink);
        
      }
    }

    pLink uponEvent {
      case PL_Deliver(src, v: VALUE) => handle {
        if (v.rid == rid) {
          //        println(s"Process $self does PL_Deliver VALUE from $src");
          var test = ((v.ts, v.wr), v.value);
          readlist = readlist ++ Map(src -> test);
          //println(s"readlist = $readlist, test = $test, v = $v, src = $src");
          if (readlist.size > n/2) {
            //            println(s"Process $self found readlist long enough during PL_Deliver from $src");
            // Get max by ts and tie break by rr
            var maxRead = readlist.maxBy(_._2._1)._2;
            readval = maxRead._2;
            var (maxts, rr) = maxRead._1;
            println(s"\n$self Readval = $readval \nfrom: $readlist\n");
            //           (var maxts, rr, readval) = readlist.maxBy(AddressUtils.toRank(_._1));
            
            readlist = Map.empty;
            var bcastval: Option[Any] = None;
            if (reading) {
              bcastval = Some(readval.getOrElse(None));
              //                println(s"reading=true, bcastval = $bcastval, readval = $readval");
            } else {
              rr = AddressUtils.toRank(self);
              maxts = maxts+1;
              bcastval = Some(writeval.get);
              //                println(s"reading=false, bcastval = $bcastval, readval = $readval, rr = $rr, maxts = $maxts");
            }
            trigger(BEB_Broadcast(WRITE(rid, maxts, rr, bcastval))-> beb);
          }
          
        }
      }
      case PL_Deliver(src, v: ACK) => handle {
        //      println(s"Process $self does PL_Deliver ACK from $src");
        if (v.rid == rid) {
          acks = acks+1;
          if (acks > n/2) {
            acks = 0;
            val currTime = System.currentTimeMillis();
            if (reading) {
              reading = false;
              println(s"$currTime: Process $self does READ RESPONSE = $readval, rid = $rid");
              trigger(AR_Read_Response(readval) -> nnar);
            } else {
              println(s"$currTime: Process $self does WRITE RESPONSE = $value, rid = $rid");
              trigger(AR_Write_Response() -> nnar);
            }
          }
          
        }
      }
    }
  }