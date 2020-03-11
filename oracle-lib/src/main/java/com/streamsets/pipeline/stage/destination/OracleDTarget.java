/*
 * Copyright 2019 StreamSets Inc.
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

package com.streamsets.pipeline.stage.destination;

import com.streamsets.pipeline.api.ConfigDefBean;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.api.HideConfigs;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.service.ServiceDependency;
import com.streamsets.pipeline.api.service.sshtunnel.SshTunnelService;
import com.streamsets.pipeline.lib.jdbc.EncryptionGroups;
import com.streamsets.pipeline.lib.jdbc.HikariPoolConfigBean;
import com.streamsets.pipeline.stage.config.OracleHikariPoolConfigBean;
import com.streamsets.pipeline.stage.destination.jdbc.JdbcDTarget;

@GenerateResourceBundle
@StageDef(version = 1,
    label = "Oracle Producer",
    description = "Insert, update, and delete data to Oracle.",
    upgraderDef = "upgrader/OracleDTarget.yaml",
    icon = "oracle.png",
    onlineHelpRefUrl = "index.html?contextID=task_jnt_qdv_33b",
    services = @ServiceDependency(service = SshTunnelService.class))
@ConfigGroups(value = EncryptionGroups.class)
@HideConfigs(value = {
    "hikariConfigBean.readOnly",
    "hikariConfigBean.autoCommit",
    "hikariConfigBean.connectionString",
    "hikariConfigBean.useCredentials",
})
public class OracleDTarget extends JdbcDTarget {

  @ConfigDefBean
  public OracleHikariPoolConfigBean hikariConfigBean;

  @Override
  protected HikariPoolConfigBean getHikariConfigBean() {
    return hikariConfigBean;
  }

}
