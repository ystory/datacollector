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
package com.streamsets.pipeline.lib.startJob;

import com.streamsets.pipeline.api.ErrorCode;
import com.streamsets.pipeline.api.GenerateResourceBundle;

@GenerateResourceBundle
public enum StartJobErrors implements ErrorCode {
  START_JOB_01("Control Hub Login failed, status code '{}': {}"),
  START_JOB_02("Reset failed for Job ID: {}, status code '{}': {}"),
  START_JOB_03("Failed to Start Job for Job ID: {}, status code '{}': {}"),
  START_JOB_04("Failed to Start Job Template for Job ID: {}, status code '{}': {}"),
  START_JOB_05("Failed to parse runtime parameters for Job ID: {}, error: {}"),
  START_JOB_06("Configuration value is required for Job ID, at index: {}"),
  ;

  private final String msg;
  StartJobErrors(String msg) {
    this.msg = msg;
  }

  @Override
  public String getCode() {
    return name();
  }

  @Override
  public String getMessage() {
    return msg;
  }

}
