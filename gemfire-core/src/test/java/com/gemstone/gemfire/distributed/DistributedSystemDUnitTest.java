/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.distributed;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import com.gemstone.gemfire.CancelException;
import com.gemstone.gemfire.GemFireConfigException;
import com.gemstone.gemfire.SystemConnectException;
import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache30.CacheSerializableRunnable;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.distributed.internal.DistributionException;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystemJUnitTest;
import com.gemstone.gemfire.distributed.internal.SerialDistributionMessage;
import com.gemstone.gemfire.distributed.internal.SizeableRunnable;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.distributed.internal.membership.gms.mgr.GMSMembershipManager;
import com.gemstone.gemfire.internal.AvailablePort;
import com.gemstone.gemfire.internal.SocketCreator;

import dunit.DistributedTestCase;
import dunit.Host;
import dunit.VM;

/**
 * Tests the functionality of the {@link DistributedSystem} class.
 *
 * @see InternalDistributedSystemJUnitTest
 *
 * @author David Whitlock
 */
public class DistributedSystemDUnitTest extends DistributedTestCase {

  public DistributedSystemDUnitTest(String name) {
    super(name);
  }
  
  public void setUp() throws Exception {
    super.setUp();
    disconnectAllFromDS();
  }
  
  ////////  Test methods

  /**
   * ensure that waitForMemberDeparture correctly flushes the serial message queue for
   * the given member
   */
  // TODO this needs to use a locator
  public void _testWaitForDeparture() throws Exception {
    disconnectAllFromDS();
    Properties p = getDistributedSystemProperties();
    p.put(DistributionConfig.LOCATORS_NAME, "");
    p.put(DistributionConfig.DISABLE_TCP_NAME, "true");
    InternalDistributedSystem ds = (InternalDistributedSystem)DistributedSystem.connect(p);
    try {
      // construct a member ID that will represent a departed member
      InternalDistributedMember mbr = new InternalDistributedMember("localhost", 12345, "", "");
      final DistributionManager mgr = (DistributionManager)ds.getDistributionManager();
      // schedule a message in order to create a queue for the fake member
      final FakeMessage msg = new FakeMessage(null);
      mgr.getExecutor(DistributionManager.SERIAL_EXECUTOR, mbr).execute(new SizeableRunnable(100) {
        public void run() {
          msg.doAction(mgr, false);
        }
        public String toString() {
          return "Processing fake message";
        }
      });
      try {
        assertTrue("expected the serial queue to be flushed", mgr.getMembershipManager().waitForDeparture(mbr));
      } catch (InterruptedException e) {
        fail("interrupted");
      } catch (TimeoutException e) {
        fail("timed out - increase this test's member-timeout setting");
      }
    } finally {
      ds.disconnect();
    }
  }

  static class FakeMessage extends SerialDistributionMessage {
    volatile boolean[] blocked;
    volatile boolean processed;
    
    FakeMessage(boolean[] blocked) {
      this.blocked = blocked;
    }
    public void doAction(DistributionManager dm, boolean block) {
      processed = true;
      if (block) {
        synchronized(blocked) {
          blocked[0] = true;
          blocked.notify();
          try {
            blocked.wait(60000);
          } catch (InterruptedException e) {}
        }
      }
    }
    public int getDSFID() {
      return 0; // never serialized
    }
    protected void process(DistributionManager dm) {
      // this is never called
    }
    public String toString() {
      return "FakeMessage(blocking="+(blocked!=null)+")";
    }
  }
  
  /**
   * Tests that we can get a DistributedSystem with the same
   * configuration twice.
   */
  public void testGetSameSystemTwice() {
    Properties config = new Properties();

//     int unusedPort = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
//     config.setProperty("mcast-port", String.valueOf(unusedPort));
    // a loner is all this test needs
    config.setProperty("mcast-port", "0");
    config.setProperty("locators", "");
    // set a flow-control property for the test (bug 37562)
    config.setProperty("mcast-flow-control", "3000000,0.20,3000");
    
    DistributedSystem system1 = DistributedSystem.connect(config);
    DistributedSystem system2 = DistributedSystem.connect(config);
    assertSame(system1, system2);
    system1.disconnect();
  }

  /**
   * Tests that getting a <code>DistributedSystem</code> with a
   * different configuration after one has already been obtained
   * throws an exception.
   */
  public void testGetDifferentSystem() {
    Properties config = new Properties();

//     int unusedPort = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
//     config.setProperty("mcast-port", String.valueOf(unusedPort));
    // a loner is all this test needs
    config.setProperty("mcast-port", "0");
    config.setProperty("locators", "");
    config.setProperty("mcast-flow-control", "3000000,0.20,3000");


    DistributedSystem system1 = DistributedSystem.connect(config);
    config.setProperty("mcast-address", "224.0.0.1");
    try {
      DistributedSystem.connect(config);
      if (System.getProperty("gemfire.mcast-address") == null) {
        fail("Should have thrown an IllegalStateException");
      }
    }
    catch (IllegalStateException ex) {
      // pass...
    }
    finally {
      system1.disconnect();
    }
  }

  /**
   * Tests getting a system with a different configuration after
   * another system has been closed.
   */
  public void testGetDifferentSystemAfterClose() {
    Properties config = new Properties();

//     int unusedPort = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
//     config.setProperty("mcast-port", String.valueOf(unusedPort));
    // a loner is all this test needs
    config.setProperty("mcast-port", "0");
    config.setProperty("locators", "");

    DistributedSystem system1 = DistributedSystem.connect(config);
    system1.disconnect();
    int time = DistributionConfig.DEFAULT_ACK_WAIT_THRESHOLD + 17;
    config.put(DistributionConfig.ACK_WAIT_THRESHOLD_NAME,
               String.valueOf(time));
    DistributedSystem system2 = DistributedSystem.connect(config);
    system2.disconnect();
  }
  
  
  public void testGetProperties() {
    Properties config = new Properties();

//     int unusedPort = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
//     config.setProperty("mcast-port", String.valueOf(unusedPort));
    // a loner is all this test needs
    int unusedPort = 0;
    config.setProperty("mcast-port", "0");
    config.setProperty("locators", "");

    DistributedSystem system1 = DistributedSystem.connect(config);
    
    assertTrue(config != system1.getProperties());
    assertEquals(unusedPort, Integer.parseInt(system1.getProperties().getProperty("mcast-port")));
    
    system1.disconnect();
    
    assertTrue(config != system1.getProperties());
    assertEquals(unusedPort, Integer.parseInt(system1.getProperties().getProperty("mcast-port")));
  }
  
  
  public void testIsolatedDistributedSystem() throws Exception {
    Properties config = new Properties();
    config.setProperty("mcast-port", "0");
    config.setProperty("locators", "");
    system = (InternalDistributedSystem)DistributedSystem.connect(config);
    try {
      // make sure isolated distributed system can still create a cache and region
      Cache cache = CacheFactory.create(getSystem());
      Region r = cache.createRegion(getUniqueName(), new AttributesFactory().create());
      r.put("test", "value");
      assertEquals("value", r.get("test"));
    } finally {
      getSystem().disconnect();
    }
  }


  /** test the ability to set the port used to listen for tcp/ip connections */
  public void testSpecificTcpPort() throws Exception {
    Properties config = new Properties();
    int tcpPort = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET);
    config.put("locators", "localhost["+getDUnitLocatorPort()+"]");
    config.setProperty("tcp-port", String.valueOf(tcpPort));
    system = (InternalDistributedSystem)DistributedSystem.connect(config);
    DistributionManager dm = (DistributionManager)system.getDistributionManager();
    GMSMembershipManager mgr = (GMSMembershipManager)dm.getMembershipManager();
    int actualPort = mgr.getDirectChannelPort();
    system.disconnect();
    assertEquals(tcpPort, actualPort);
  }

  /** test that loopback cannot be used as a bind address when a locator w/o a bind address is being used */
  public void testLoopbackNotAllowed() throws Exception {
	  // DISABLED for bug #49926
    InetAddress loopback = null;
    for (Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces(); it.hasMoreElements(); ) {
      NetworkInterface nif = it.nextElement();
      for (Enumeration<InetAddress> ait = nif.getInetAddresses(); ait.hasMoreElements(); ) {
        InetAddress a = ait.nextElement();
        Class theClass = SocketCreator.getLocalHost() instanceof Inet4Address? Inet4Address.class : Inet6Address.class;
        if (a.isLoopbackAddress() && (a.getClass().isAssignableFrom(theClass))) {
          loopback = a;
          break;
        }
      }
    }
    if (loopback != null) {
      Properties config = new Properties();
      config.put(DistributionConfig.MCAST_PORT_NAME, "0");
      String locators = InetAddress.getLocalHost().getHostName()+":"+getDUnitLocatorPort();
      config.put(DistributionConfig.LOCATORS_NAME, locators);
      config.setProperty(DistributionConfig.BIND_ADDRESS_NAME, loopback.getHostAddress());
      getLogWriter().info("attempting to connect with " + loopback +" and locators=" + locators);
      try {
        system = (InternalDistributedSystem)DistributedSystem.connect(config);
        system.disconnect();
        fail("expected a configuration exception disallowing use of loopback address");
      } catch (GemFireConfigException e) {
        // expected
      } catch (DistributionException e) {
        // expected
      }
    }
  }

  public void testUDPPortRange() throws Exception {
    Properties config = new Properties();
    int unicastPort = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET);
    config.put("locators", "localhost["+getDUnitLocatorPort()+"]");
    // Minimum 3 ports required in range for UDP, FD_SOCK and TcpConduit.
    config.setProperty(DistributionConfig.MEMBERSHIP_PORT_RANGE_NAME, 
        ""+unicastPort+"-"+(unicastPort+2)); 
    system = (InternalDistributedSystem)DistributedSystem.connect(config);
    DistributionManager dm = (DistributionManager)system.getDistributionManager();
    InternalDistributedMember idm = dm.getDistributionManagerId();
    system.disconnect();
    assertTrue(unicastPort <= idm.getPort() && idm.getPort() <= unicastPort+2);
    assertTrue(unicastPort <= idm.getPort() && idm.getDirectChannelPort() <= unicastPort+2);
  }

  // TODO this needs to use a locator
  public void _testMembershipPortRange() throws Exception {
    Properties config = new Properties();
    int mcastPort = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
    int unicastPort = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET);
    config.setProperty("mcast-port", String.valueOf(mcastPort));
    config.setProperty("locators", "");
    config.setProperty(DistributionConfig.MEMBERSHIP_PORT_RANGE_NAME, 
        ""+unicastPort+"-"+(unicastPort+1));
    try {
      system = (InternalDistributedSystem)DistributedSystem.connect(config);
    } catch (Exception e) {
      assertTrue("The exception must be IllegalArgumentException", e instanceof IllegalArgumentException);
      return;
    }
    fail("IllegalArgumentException must have been thrown by DistributedSystem.connect() as port-range: "
        + config.getProperty(DistributionConfig.MEMBERSHIP_PORT_RANGE_NAME)
        + " must have at least 3 values in range");
  }

  // TODO this needs to use a locator
  public void _testMembershipPortRangeWithExactThreeValues() throws Exception {
    Properties config = new Properties();
    int mcastPort = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
    config.setProperty("mcast-port", String.valueOf(mcastPort));
    config.setProperty("locators", "");
    config.setProperty(DistributionConfig.MEMBERSHIP_PORT_RANGE_NAME, ""
        + (DistributionConfig.DEFAULT_MEMBERSHIP_PORT_RANGE[1] - 2) + "-"
        + (DistributionConfig.DEFAULT_MEMBERSHIP_PORT_RANGE[1]));
    system = (InternalDistributedSystem)DistributedSystem.connect(config);
    Cache cache = CacheFactory.create(system);
    cache.addCacheServer();
    DistributionManager dm = (DistributionManager) system.getDistributionManager();
    InternalDistributedMember idm = dm.getDistributionManagerId();
    system.disconnect();
    assertTrue(idm.getPort() <= DistributionConfig.DEFAULT_MEMBERSHIP_PORT_RANGE[1]);
    assertTrue(idm.getPort() >= DistributionConfig.DEFAULT_MEMBERSHIP_PORT_RANGE[0]);
    assertTrue(idm.getDirectChannelPort() <= DistributionConfig.DEFAULT_MEMBERSHIP_PORT_RANGE[1]);
    assertTrue(idm.getDirectChannelPort() >= DistributionConfig.DEFAULT_MEMBERSHIP_PORT_RANGE[0]);
  }

  // TODO this needs to use a locator
  public void _testConflictingUDPPort() throws Exception {
    final Properties config = new Properties();
    final int mcastPort = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
    final int unicastPort = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET);
    config.setProperty("mcast-port", String.valueOf(mcastPort));
    config.setProperty("locators", "");
    config.setProperty(DistributionConfig.MEMBERSHIP_PORT_RANGE_NAME, 
        ""+unicastPort+"-"+(unicastPort+2));
    system = (InternalDistributedSystem)DistributedSystem.connect(config);
    DistributionManager dm = (DistributionManager)system.getDistributionManager();
    InternalDistributedMember idm = dm.getDistributionManagerId();
    VM vm = Host.getHost(0).getVM(1);
    vm.invoke(new CacheSerializableRunnable("start conflicting system") {
      public void run2() {
        try {
          DistributedSystem system = DistributedSystem.connect(config);
          system.disconnect();
        } catch (SystemConnectException e) {
          return; // 
        }
        fail("expected a SystemConnectException but didn't get one");
      }
    });
    system.disconnect();
  }

  /**
   * Tests that configuring a distributed system with a cache-xml-file
   * of "" does not initialize a cache.  See bug 32254.
   *
   * @since 4.0
   */
  public void testEmptyCacheXmlFile() throws Exception {
    Properties config = new Properties();

//     int unusedPort =
//       AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
//     config.setProperty("mcast-port", String.valueOf(unusedPort));
    // a loner is all this test needs
    config.setProperty("mcast-port", "0");
    config.setProperty("locators", "");
    config.setProperty(DistributionConfig.CACHE_XML_FILE_NAME, "");

    DistributedSystem sys = DistributedSystem.connect(config);

    try {
      try {
        CacheFactory.getInstance(sys);
        fail("Should have thrown a CancelException");
      } 
      catch (CancelException expected) {
      }
      // now make sure we can create the cache
      CacheFactory.create(sys);

    } finally {
      sys.disconnect();
    }
  }
  
  static volatile String problem;
  
  public void testInterruptedWhileConnecting() throws Exception {
    fail("testInterruptedWhileConnecting must be reimplemented for the new GMS");
  }
  public void _testInterruptedWhileConnecting() throws Exception {
//    Runnable r = new Runnable() {
//      public void run() {
//        ClientGmsImpl.SLOW_JOIN_LOCK = new Object();
//        ClientGmsImpl.SLOW_JOIN = true;
//        try {
//          assertTrue("should be disconnected at this point", InternalDistributedSystem.getConnectedInstance() == null);
//          getSystem();
//          problem = "a connection to the distributed system was established but it should have failed";
//        } catch (SystemConnectException e) {
//          if (!e.getMessage().endsWith(ExternalStrings.ClientGmsImpl_JOIN_INTERRUPTED.getRawText())) {
//            problem = "got a system connect exception but it was for the wrong reason";
//            getLogWriter().info("wrong exception thrown: '" + e.getMessage() + "' (wanted '"+
//              ExternalStrings.ClientGmsImpl_JOIN_INTERRUPTED.getRawText()+"')", e);
//          }
//        } finally {
//          ClientGmsImpl.SLOW_JOIN = false;
//          ClientGmsImpl.SLOW_JOIN_LOCK = null;
//        }
//      }
//    };
//    Thread connectThread = new Thread(r, "testInterruptedWhileConnecting connect thread");
//    ClientGmsImpl.SLOW_JOIN = false;
//    connectThread.start();
//    while (ClientGmsImpl.SLOW_JOIN == false) {
//      pause(1000);
//    }
//    pause(5000);
//    connectThread.interrupt();
//    connectThread.join(60000);
//    getLogWriter().info("done waiting for connectThread.  Thread is " +
//      (connectThread.isAlive()? "still alive" : "stopped"));
//    if (ClientGmsImpl.SLOW_JOIN) {
//      problem = "failed to either connect or get an exception - one of these should have happened";
//      dumpMyThreads(getLogWriter());
//    }
//    if (problem != null) {
//      fail(problem);
//    }
  }

  
}
