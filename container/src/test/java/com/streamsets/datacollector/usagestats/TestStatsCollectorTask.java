/*
 * Copyright 2018 StreamSets Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.datacollector.usagestats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.streamsets.datacollector.bundles.SupportBundleManager;
import com.streamsets.datacollector.json.ObjectMapperFactory;
import com.streamsets.datacollector.main.BuildInfo;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.util.Configuration;
import com.streamsets.pipeline.lib.executor.SafeScheduledExecutorService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TestStatsCollectorTask {

  private File createTestDir() {
    File dir = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(dir.mkdir());
    return dir.getAbsoluteFile();
  }

  @Test
  public void testGetters() {
    File testDir = createTestDir();
    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();
    config.set(StatsCollectorTask.ROLL_FREQUENCY_CONFIG, 1);

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    Assert.assertEquals(buildInfo, task.getBuildInfo());

    Assert.assertEquals(runtimeInfo, task.getRuntimeInfo());

    Assert.assertEquals(TimeUnit.HOURS.toMillis(1), task.getRollFrequencyMillis());

    Assert.assertNull(task.getStatsInfo());

  }

  @Test
  public void testClusterSlave() {
    File testDir = createTestDir();
    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    Mockito.when(runtimeInfo.isClusterSlave()).thenReturn(true);

    task.init();

    Assert.assertTrue(task.isOpted());
    Assert.assertFalse(task.isActive());
    Assert.assertNotNull(task.getStatsInfo());

    task.stop();
  }

  @Test
  public void testFirstRunAndCommonInitializationAndStopLogic() {
    File testDir = createTestDir();
    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertFalse(task.isOpted());
    Assert.assertFalse(task.isActive());
    Assert.assertNotNull(task.getStatsInfo());

    Mockito.verify(runnable, Mockito.times(1)).run();
    Mockito.verify(scheduler, Mockito.times(1)).scheduleAtFixedRate(
        Mockito.eq(runnable),
        Mockito.eq(60L),
        Mockito.eq(60L * 60 * 24),
        Mockito.eq(TimeUnit.SECONDS)
    );

    Future future = Mockito.mock(ScheduledFuture.class);
    Mockito.doReturn(future).when(task).getFuture();

    Assert.assertEquals(1, task.getStatsInfo().getActiveStats().getUpTime().getMultiplier());

    task.stop();
    Mockito.verify(future, Mockito.times(1)).cancel(Mockito.eq(false));

    Mockito.verify(runnable, Mockito.times(2)).run();

    Assert.assertEquals(0, task.getStatsInfo().getActiveStats().getUpTime().getMultiplier());
  }

  @Test
  public void testInitialOptingOut() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertFalse(task.isOpted());
    Assert.assertFalse(task.isActive());
    task.setActive(false);
    Assert.assertTrue(task.isOpted());
    Assert.assertFalse(task.isActive());

    task.stop();
  }

  @Test
  public void testInitialOptingIn() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertFalse(task.isOpted());
    Assert.assertFalse(task.isActive());
    task.setActive(false);
    Assert.assertTrue(task.isOpted());
    Assert.assertFalse(task.isActive());

    task.stop();
  }

  @Test
  public void testOptedNo() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, false));
    }

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertTrue(task.isOpted());
    Assert.assertFalse(task.isActive());
    Assert.assertNotNull(task.getStatsInfo());

    task.stop();

  }

  @Test
  public void testOptedYesNoPriorStats() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, true));
    }

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertTrue(task.isOpted());
    Assert.assertTrue(task.isActive());

    task.stop();
  }

  @Test
  public void testOptedInvalid1() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
    }

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertFalse(task.isOpted());
    Assert.assertFalse(task.isActive());

    task.stop();
  }

  @Test
  public void testOptedInvalid2() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, null);
    }

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertFalse(task.isOpted());
    Assert.assertFalse(task.isActive());

    task.stop();
  }

  @Test
  public void testOptedInvalid3() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableList.of());
    }

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertFalse(task.isOpted());
    Assert.assertFalse(task.isActive());

    task.stop();
  }

  @Test
  public void testOptedInvalid4() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      os.write("foo".getBytes());
    }

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertFalse(task.isOpted());
    Assert.assertFalse(task.isActive());

    task.stop();
  }


  @Test
  public void testOptedYesPriorStats() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, true));
    }

    try (OutputStream os = new FileOutputStream(task.getStatsFile())) {
      StatsInfo statsInfo = new StatsInfo();
      statsInfo.getActiveStats().setDataCollectorVersion("v2");
      ObjectMapperFactory.get().writeValue(os, statsInfo);
    }

    task = Mockito.spy(task);
    Runnable runnable = Mockito.mock(Runnable.class);
    Mockito.doReturn(runnable).when(task).getRunnable();

    task.init();

    Assert.assertTrue(task.isOpted());
    Assert.assertTrue(task.isActive());

    //getRunnable() is mocked out to nothing, that is why we get v2 that we read from file
    Assert.assertEquals("v2", task.getStatsInfo().getActiveStats().getDataCollectorVersion());
    task.stop();
  }

  @Test
  public void testRunnable() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    SupportBundleManager supportBundleManager = Mockito.mock(SupportBundleManager.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, true));
    }

    try (OutputStream os = new FileOutputStream(task.getStatsFile())) {
      StatsInfo statsInfo = new StatsInfo();
      statsInfo.getActiveStats().setDataCollectorVersion("v0");
      statsInfo.getCollectedStats().add(new StatsBean());
      ObjectMapperFactory.get().writeValue(os, statsInfo);
    }

    task = Mockito.spy(task);

    task.init();

    Assert.assertTrue(task.isOpted());
    Assert.assertTrue(task.isActive());

    //verifying we rolled the read stats
    Assert.assertEquals("v1", task.getStatsInfo().getActiveStats().getDataCollectorVersion());

    task.stop();
  }

  @Ignore("Must integrate new UPLOAD, then test")
  @Test
  public void testRunnableReportStatsException() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);
    ScheduledFuture future = Mockito.mock(ScheduledFuture.class);
    Mockito.doReturn(future).when(scheduler).scheduleAtFixedRate(
        Mockito.any(),
        Mockito.anyLong(),
        Mockito.anyLong(),
        Mockito.any()
    );

    SupportBundleManager supportBundleManager = Mockito.mock(SupportBundleManager.class);
    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, true));
    }

    try (OutputStream os = new FileOutputStream(task.getStatsFile())) {
      StatsInfo statsInfo = new StatsInfo();
      statsInfo.getActiveStats().setDataCollectorVersion("v0");
      ObjectMapperFactory.get().writeValue(os, statsInfo);
    }

    task = Mockito.spy(task);

    // one time
    task.init();
    Assert.assertTrue(task.isOpted());
    Assert.assertTrue(task.isActive());
    Assert.assertEquals(1, task.getStatsInfo().getCollectedStats().size());
    Mockito.verify(scheduler, Mockito.times(1)).scheduleAtFixedRate(
        Mockito.any(),
        Mockito.eq(60L),
        Mockito.eq(60L),
        Mockito.eq(TimeUnit.SECONDS)
    );

    // two times
    task.getRunnable().run();
    Assert.assertTrue(task.isOpted());
    Assert.assertTrue(task.isActive());

    // count resets because it works now
    task.getRunnable().run();
    Assert.assertTrue(task.isOpted());
    Assert.assertTrue(task.isActive());
    Assert.assertEquals(0, task.getStatsInfo().getCollectedStats().size());

    task.getStatsInfo().getCollectedStats().add(new StatsBean());

    // It will retry 5 times, once a minute, before backing off for 1 day.  After 1 day, it will retry the 5 times again
    // and if it continues to fail, it will back off for 2 days.  After that, 4 days.  Finally, it will give up and
    // switch off.  Here, we run through all of this, but it manages to temporarily succeed after on the 4th try of the
    // 3rd day (which resets all the retries and back offs).
    int doOnceOnly = 0;
    for (int k = 0; k < 4; k++) {
      System.out.println("AAA k = " + k);

      // one through five times
      for (int i = 0; i < 5; i++) {
        System.out.println("AAA k = " + k + " i = " + i);
        if (doOnceOnly == 0 && k == 2 && i == 3) {

          // count resets because it works now
          task.getRunnable().run();
          Assert.assertTrue(task.isOpted());
          Assert.assertTrue(task.isActive());
          Assert.assertEquals(0, task.getStatsInfo().getCollectedStats().size());

          task.getStatsInfo().getCollectedStats().add(new StatsBean());

          k = 0;
          i = -1;
          doOnceOnly = 1;
        } else {
          task.getRunnable().run();
          Assert.assertTrue(task.isOpted());
          Assert.assertTrue(task.isActive());
          Assert.assertEquals(1, task.getStatsInfo().getCollectedStats().size());
        }
      }

      // six times - we now try to back off
      task.getRunnable().run();
      Assert.assertTrue(task.isOpted());
      // Keep backing off while it's less than 3
      if (k < 3) {
        Assert.assertTrue(task.isActive());
        Assert.assertEquals(1, task.getStatsInfo().getCollectedStats().size());
        int expectedTimes = k <= 1 ? doOnceOnly + 1 : 1;
        Mockito.verify(scheduler, Mockito.times(expectedTimes)).scheduleAtFixedRate(
            Mockito.any(),
            Mockito.eq(60L * 60L * 24L * (int)Math.pow(2, k)),
            Mockito.eq(60L),
            Mockito.eq(TimeUnit.SECONDS)
        );
      // Otherwise, we'll just give up: switch it off
      } else {
        Assert.assertFalse(task.isActive());
        Assert.assertEquals(0, task.getStatsInfo().getCollectedStats().size());
        Mockito.verify(scheduler, Mockito.times(0)).scheduleAtFixedRate(
            Mockito.any(),
            Mockito.eq(60L * 60L * 24L * (int)Math.pow(2, k)),
            Mockito.eq(60L),
            Mockito.eq(TimeUnit.SECONDS)
        );
        Mockito.verify(scheduler, Mockito.times(2)).scheduleAtFixedRate(
            Mockito.any(),
            Mockito.eq(60L),
            Mockito.eq(60L),
            Mockito.eq(TimeUnit.SECONDS)
        );
      }
    }

    task.stop();
  }

  @Test
  public void testSetActiveNoChange() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    SupportBundleManager supportBundleManager = Mockito.mock(SupportBundleManager.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, true));
    }

    try (OutputStream os = new FileOutputStream(task.getStatsFile())) {
      StatsInfo statsInfo = new StatsInfo();
      statsInfo.getActiveStats().setDataCollectorVersion("v1");
      ObjectMapperFactory.get().writeValue(os, statsInfo);
    }

    task = Mockito.spy(task);

    task.init();

    Mockito.reset(task);

    task.setActive(task.isActive());

    Mockito.verify(task, Mockito.never()).saveStats();

    task.stop();
  }

  @Test
  public void testSetActiveFromTrueToFalse() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    SupportBundleManager supportBundleManager = Mockito.mock(SupportBundleManager.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, true));
    }

    try (OutputStream os = new FileOutputStream(task.getStatsFile())) {
      StatsInfo statsInfo = new StatsInfo();
      statsInfo.getActiveStats().setDataCollectorVersion("v1");
      ObjectMapperFactory.get().writeValue(os, statsInfo);
      statsInfo.getCollectedStats().add(new StatsBean());
    }

    task = Mockito.spy(task);

    task.init();

    Mockito.reset(task);

    long start = task.getStatsInfo().getActiveStats().getStartTime();
    Thread.sleep(1);
    task.setActive(false);

    Assert.assertTrue(task.getStatsInfo().getActiveStats().getStartTime() > start);

    Assert.assertFalse(task.isActive());

    try (InputStream is = new FileInputStream(task.getOptFile())) {
      Map map = ObjectMapperFactory.get().readValue(is, Map.class);
      Assert.assertNotNull(map.get(StatsCollectorTask.STATS_ACTIVE_KEY));
      Assert.assertFalse((Boolean) map.get(StatsCollectorTask.STATS_ACTIVE_KEY));
    }
    Mockito.verify(task, Mockito.times(1)).saveStats();

    Assert.assertTrue(task.getStatsInfo().getCollectedStats().isEmpty());
    task.stop();
  }

  @Test
  public void testSetActiveFromFalseToTrue() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    try (OutputStream os = new FileOutputStream(task.getOptFile())) {
      ObjectMapperFactory.get().writeValue(os, ImmutableMap.of(task.STATS_ACTIVE_KEY, false));
    }

    task = Mockito.spy(task);

    task.init();

    Mockito.reset(task);

    long start = task.getStatsInfo().getActiveStats().getStartTime();
    Thread.sleep(1);
    task.setActive(true);

    Assert.assertTrue(task.getStatsInfo().getActiveStats().getStartTime() > start);

    Assert.assertTrue(task.isActive());

    try (InputStream is = new FileInputStream(task.getOptFile())) {
      Map map = ObjectMapperFactory.get().readValue(is, Map.class);
      Assert.assertNotNull(map.get(StatsCollectorTask.STATS_ACTIVE_KEY));
      Assert.assertTrue((Boolean) map.get(StatsCollectorTask.STATS_ACTIVE_KEY));
    }
    Mockito.verify(task, Mockito.times(1)).saveStats();

    Assert.assertTrue(task.getStatsInfo().getCollectedStats().isEmpty());
    task.stop();
  }

  @Test
  public void testReportStats() throws Exception {
    File testDir = createTestDir();

    BuildInfo buildInfo = Mockito.mock(BuildInfo.class);
    Mockito.when(buildInfo.getVersion()).thenReturn("v1");

    RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
    Mockito.when(runtimeInfo.getId()).thenReturn("id");
    Mockito.when(runtimeInfo.getDataDir()).thenReturn(testDir.getAbsolutePath());

    Configuration config = new Configuration();

    SafeScheduledExecutorService scheduler = Mockito.mock(SafeScheduledExecutorService.class);

    StatsCollectorTask task = new StatsCollectorTask(buildInfo, runtimeInfo, config, scheduler);

    List<StatsBean> stats = new ArrayList<>();

    Assert.assertTrue(task.reportStats(stats));

    //TODO
  }

  private static final Logger LOG = LoggerFactory.getLogger(TestStatsCollectorTask.class);

  public static final class UsageServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      boolean ok;
      String str = req.getContentType();
      if (str == null) {
        LOG.error("Missing content-type header");
        ok = false;
      } else {
        if (str.toLowerCase().startsWith("application/json")) {
          str = req.getHeader("x-requested-by");
          if (str == null) {
            LOG.error("Missing x-requested-by header");
            ok = false;
          } else {
            try {
              UUID.fromString(str);
              try {
                List<StatsBean> list = ObjectMapperFactory.get().readValue(
                    req.getReader(),
                    new TypeReference<List<StatsBean>>() {
                    }
                );
                if (list == null) {
                  LOG.error("Missing payload");
                  ok = false;
                } else {
                  if (list.isEmpty()) {
                    LOG.error("No stats in list");
                    ok = false;
                  } else {
                    ok = true;
                  }
                }
              } catch (IOException ex) {
                LOG.error("Invalid payload: " + ex);
                ok = false;
              }
            } catch (Exception ex) {
              LOG.error("Invalid x-requested-by header, should be SDC ID (a UUID): {}", ex, ex);
              ok = false;
            }
          }
        } else {
          LOG.error("Invalid content-type: {}", str);
          ok = false;
        }
      }
      resp.setStatus((ok) ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST);
    }
  }

  @Test
  public void testHttp() throws Exception {
    Server server = new Server(0);
    ServletContextHandler context = new ServletContextHandler();
    Servlet servlet = new UsageServlet();
    context.addServlet(new ServletHolder(servlet), StatsCollectorTask.USAGE_PATH_DEFAULT);
    context.setContextPath("/");
    server.setHandler(context);
    try {
      server.start();

      RuntimeInfo runtimeInfo = Mockito.mock(RuntimeInfo.class);
      Mockito.when(runtimeInfo.getId()).thenReturn(UUID.randomUUID().toString());

      Configuration config = new Configuration();
      config.set(StatsCollectorTask.USAGE_BASE_URL, server.getURI().toString());

      StatsCollectorTask collector = new StatsCollectorTask(null, runtimeInfo, config, null);

      List<StatsBean> list = Arrays.asList(new StatsBean());

      Assert.assertTrue(collector.reportStats(list));

    } finally {
      server.stop();
    }
  }


}
