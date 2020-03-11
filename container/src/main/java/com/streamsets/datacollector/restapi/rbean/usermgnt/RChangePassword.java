/**
 * Copyright 2020 StreamSets Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.datacollector.restapi.rbean.usermgnt;

import com.streamsets.datacollector.restapi.rbean.lang.RBean;
import com.streamsets.datacollector.restapi.rbean.lang.RString;

public class RChangePassword extends RBean<RChangePassword> {

  // USER ID
  private RString id = new RString();

  private RString oldPassword = new RString();
  private RString newPassword = new RString();

  @Override
  public RString getId() {
    return id;
  }

  @Override
  public void setId(RString id) {
    this.id = id;
  }

  public RString getOldPassword() {
    return oldPassword;
  }

  public RChangePassword setOldPassword(RString oldPassword) {
    this.oldPassword = oldPassword;
    return this;
  }

  public RString getNewPassword() {
    return newPassword;
  }

  public RChangePassword setNewPassword(RString newPassword) {
    this.newPassword = newPassword;
    return this;
  }
}
