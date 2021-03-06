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
package se.kth.id2203.kvstore;

import se.kth.id2203.networking._;
import se.kth.id2203.overlay.Routing;
import se.sics.kompics.sl._;
import se.sics.kompics.network.Network;
import se.kth.edx.id2203.core.Ports._


class KVService extends ComponentDefinition {
  // TODO: Add interaction with NNAR in this class with triggers and handlers to and from it.
  //******* Ports ******
  val net = requires[Network];
  val route = requires(Routing);
  val nnar = requires[AtomicRegister];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  // May need data structure to keep track of operations sent to NNAR without response yet.

  //******* Handlers ******
  net uponEvent {
    case NetMessage(header, op: Op) => handle {
      log.info("Got operation {}! OP :)", op);
      trigger(NetMessage(self, header.src, op.response(OpCode.NotImplemented)) -> net);
    }
    case NetMessage(header, put: Put) => handle {
      val key = put.key;
      val value = put.value;
      log.info(s"Got operation {}! PUT $key, $value", put);
      // Trigger NNAR write here
      trigger(NetMessage(self, header.src, put.response(OpCode.NotImplemented)) -> net); // Move this reply to NNAR Event handler
    }
    case NetMessage(header, get: Get) => handle {
      val key = get.key;
      log.info("Got operation {}! GET $key", get);
      // Trigger NNAR read here
      trigger(NetMessage(self, header.src, get.response(OpCode.NotImplemented)) -> net); // Move this reply to NNAR Event handler
    }
    case NetMessage(header, cas: Cas) => handle {
      val key = cas.key;
      val referenceValue = cas.referenceValue;
      val value = cas.value;
      log.info("Got operation {}! CAS $key $referenceValue $value", cas);
      trigger(NetMessage(self, header.src, cas.response(OpCode.NotImplemented)) -> net);
    }
  }
  // Insert NNAR Event handler here


}
