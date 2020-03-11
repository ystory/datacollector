/*
 * Copyright 2020 StreamSets Inc.
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

package com.streamsets.pipeline.stage.origin.scheduler;

import com.streamsets.pipeline.api.Config;
import com.streamsets.pipeline.upgrader.YamlStageUpgrader;
import com.streamsets.pipeline.upgrader.YamlStageUpgraderLoader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class TestSchedulerDPushSourceUpgrader {

  private static final String YAML_UPGRADER_PATH = "upgrader/SchedulerDPushSource.yaml";
  private YamlStageUpgrader yamlUpgrader;

  @Before
  public void setUp() throws Exception {
    URL yamlResource = ClassLoader.getSystemClassLoader().getResource(YAML_UPGRADER_PATH);
    YamlStageUpgraderLoader loader = new YamlStageUpgraderLoader("stage", yamlResource);
    yamlUpgrader = loader.get();
  }

  @Test
  public void testV1ToV2Upgrade() {
    List<Config> validV1Configs = new ArrayList<Config>() {
      {
        add(new Config("cronExpression", ""));
      }
    };
    int oldNumberOfFields = validV1Configs.size();
    Set< String > fieldNames = validV1Configs.stream().map(Config::getName).collect(Collectors.toSet());
    fieldNames.add("conf.timeZoneID");
    List<Config> newConfigs = yamlUpgrader.upgrade(
        "lib",
        "stage",
        "instance",
        1,
        2,
        validV1Configs
    );
    Assert.assertEquals(oldNumberOfFields + 1, newConfigs.size());
    // Check that the field names are all correct
    for(Config config: newConfigs) {
      String configName = config.getName();
      assertTrue(fieldNames.contains(configName));
      // Check that no duplicates happened when upgrading
      fieldNames.remove(configName);
    }
  }
}
