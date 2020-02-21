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

package com.streamsets.pipeline.stage.processor.lookup;

import com.streamsets.pipeline.api.ConfigDefBean;
import com.streamsets.pipeline.api.ConfigGroups;
import com.streamsets.pipeline.api.GenerateResourceBundle;
import com.streamsets.pipeline.lib.jdbc.EncryptionGroups;
import com.streamsets.pipeline.lib.jdbc.HikariPoolConfigBean;
import com.streamsets.pipeline.stage.config.MySQLHikariPoolConfigBean;
import com.streamsets.pipeline.stage.processor.jdbclookup.JdbcLookupDProcessor;

//@StageDef(version = 1,
//    label = "MySQL Lookup",
//    description = "Lookup values via MySQL to enrich records.",
//    icon = "mysql.png",
//    upgrader = upgrader/MySQLLookupDProcessor.yaml,
//    onlineHelpRefUrl = "index.html?contextID=task_iql_bhv_33b")
@ConfigGroups(value = EncryptionGroups.class)
@GenerateResourceBundle
public class MySQLLookupDProcessor extends JdbcLookupDProcessor {

  @ConfigDefBean
  public MySQLHikariPoolConfigBean hikariConfigBean;

  @Override
  protected HikariPoolConfigBean getHikariConfigBean() {
    return hikariConfigBean;
  }

}
