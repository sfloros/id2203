/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.overlay;

import se.kth.id2203.bootstrapping._;
import se.kth.id2203.networking._;
import se.kth.id2203.broadcast._;
import se.sics.kompics.sl._;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import util.Random;
import se.kth.edx.id2203.core.ExercisePrimitives._
import se.sics.kompics.{ComponentDefinition => _, Port => _,KompicsEvent}

import scala.collection.mutable.Set


/**
 * The V(ery)S(imple)OverlayManager.
 * <p>
 * Keeps all nodes in a single partition in one replication group.
 * <p>
 * Note: This implementation does not fulfill the project task. You have to
 * support multiple partitions!
 * <p>
 * @author Lars Kroll <lkroll@kth.se>
 */
class VSOverlayManager extends ComponentDefinition {

  //******* Ports ******
  val route = provides(Routing);
  val boot = requires(Bootstrapping);
  val net = requires[Network];
  val timer = requires[Timer];
  // New stuff:
  //val nnar =  provides(AtomicRegister);
  val beb = requires[BestEffortBroadcast];

  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val delta = cfg.getValue[Int]("id2203.project.delta");
  private var lut: Option[LookupTable] = None;
  //******* Handlers ******

  beb uponEvent {
    case BEB_Deliver(src, payload) => handle {
      log.info("Broadcast was sent");
    }
  }

  boot uponEvent {
    case GetInitialAssignments(nodes) => handle {
      log.info(s"Generating LookupTable... delta: $delta");
      var (lut, standby) = LookupTable.generate(nodes, delta);
      logger.debug(s"Generated assignments:\n$lut");
      trigger (new InitialAssignments(lut, standby) -> boot);

        // BEB here
       //  val beb = create(classOf[BasicBroadcast], Init[BasicBroadcast](self, lut.lookup(self)));
      //   Connect NNAR and BEB here
     //    connect[BasicBroadcast](beb -> net);

    }
    case Booted(assignment: LookupTable) => handle {
      log.info("Got NodeAssignment, overlay ready.");
      lut = Some(assignment);

      // BEB Broadcast for the lookup of nodes, what argument to use here
      trigger(Set_Topology(assignment.lookup(self)) -> beb);

      // Start the EPFD here

      // Start SC here
    }
  }

  net uponEvent {
    case NetMessage(header, RouteMsg(key, msg)) => handle {
      val nodes = lut.get.lookup(key);
      assert(!nodes.isEmpty);
      val i = Random.nextInt(nodes.size);
      val target = nodes.drop(i).head;
      log.info(s"Forwarding message for key $key to $target");
      trigger(NetMessage(header.src, target, msg) -> net);
    }
    case NetMessage(header, msg: Connect) => handle {
      lut match {
        case Some(l) => {
          log.debug(s"Accepting connection request from ${header.src}");
          val size = l.getNodes().size;
          trigger (NetMessage(self, header.src, msg.ack(size)) -> net);
        }
        case None => log.info("Rejecting connection request from ${header.src}, as system is not ready, yet.");
      }
    }
  }

  route uponEvent {
    case RouteMsg(key, msg) => handle {
      val nodes = lut.get.lookup(key);
      assert(!nodes.isEmpty);
      val i = Random.nextInt(nodes.size);
      val target = nodes.drop(i).head;
      log.info(s"Routing message for key $key to $target");
      trigger (NetMessage(self, target, msg) -> net);
    }
  }
}
