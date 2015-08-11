/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache.tier.sockets;

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.gemstone.gemfire.cache.CacheException;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.*;
import com.gemstone.gemfire.cache30.CacheSerializableRunnable;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.internal.AvailablePort;
import com.gemstone.gemfire.internal.cache.HARegion;
import com.gemstone.gemfire.internal.cache.PoolFactoryImpl;

import dunit.DistributedTestCase;
import dunit.Host;
import dunit.VM;

/**
 * 
 * @author Deepkumar Varma
 *
 * The test is written to verify that the rootRegion() in GemfireCache.java
 * doesn't return any metaRegions or HA Regions.
 * 
 */

public class Bug37805DUnitTest extends DistributedTestCase{

  private VM server1VM;

  private VM durableClientVM;

  private String regionName;

  private int PORT1;

  public Bug37805DUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
    Host host = Host.getHost(0);
    this.server1VM = host.getVM(0);
    this.durableClientVM = host.getVM(1);
    regionName = Bug37805DUnitTest.class.getName() + "_region";
    CacheServerTestUtil.disableShufflingOfEndpoints();
  }
  
  public void tearDown2() throws Exception {
    // Stop server 1
    this.server1VM.invoke(CacheServerTestUtil.class, "closeCache");
    CacheServerTestUtil.resetDisableShufflingOfEndpointsFlag();
  }
  
  public void testFunctionality() {
 // Step 1: Starting the servers

    PORT1 = ((Integer)this.server1VM.invoke(CacheServerTestUtil.class,
        "createCacheServer", new Object[] { regionName, new Boolean(true),
            })).intValue();
    final int durableClientTimeout = 600; 
    
    
    // Step 2: Starting Client and creating durableRegion
    final String durableClientId = getName() + "_client";

    this.durableClientVM.invoke(CacheServerTestUtil.class, "createCacheClient",
        new Object[] {
            getClientPool(getServerHostName(durableClientVM.getHost()), PORT1, true, 0),
            regionName,
            getDurableClientDistributedSystemProperties(durableClientId,
                durableClientTimeout), Boolean.TRUE });

    // Send clientReady message
    this.durableClientVM.invoke(new CacheSerializableRunnable(
        "Send clientReady") {
      public void run2() throws CacheException {
        CacheServerTestUtil.getCache().readyForEvents();
      }
    });
    
    this.server1VM.invoke(Bug37805DUnitTest.class, "checkRootRegions");
    
    
    this.durableClientVM.invoke(CacheServerTestUtil.class, "closeCache");
  }
  
  public static void checkRootRegions() {
    Set rootRegions = CacheServerTestUtil.getCache().rootRegions();
    if(rootRegions != null) {
      for(Iterator itr = rootRegions.iterator(); itr.hasNext(); ){
        Region region = (Region)itr.next();
        if (region instanceof HARegion)
          fail("region of HARegion present");
      }
    }
    //assertNull(rootRegions);
    //assertEquals(0,((Collection)CacheServerTestUtil.getCache().rootRegions()).size());
  }
  
  private Pool getClientPool(String host, int server1Port,
      boolean establishCallbackConnection, int redundancyLevel) {
    PoolFactory pf = PoolManager.createFactory();
    pf.addServer(host, server1Port)
      .setSubscriptionEnabled(establishCallbackConnection)
      .setSubscriptionRedundancy(redundancyLevel);
    return ((PoolFactoryImpl)pf).getPoolAttributes();
  }

  private Properties getDurableClientDistributedSystemProperties(
      String durableClientId, int durableClientTimeout) {
    Properties properties = new Properties();
    properties.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    properties.setProperty(DistributionConfig.LOCATORS_NAME, "");
    properties.setProperty(DistributionConfig.DURABLE_CLIENT_ID_NAME,
        durableClientId);
    properties.setProperty(DistributionConfig.DURABLE_CLIENT_TIMEOUT_NAME,
        String.valueOf(durableClientTimeout));
    return properties;
  }
}
