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
package com.streamsets.pipeline.stage.processor.startPipeline;

import com.streamsets.pipeline.lib.startPipeline.PipelineIdConfig;
import com.streamsets.pipeline.lib.startPipeline.StartPipelineConfig;

import java.util.ArrayList;

class TestStartPipelineProcessorBuilder {

  private StartPipelineConfig conf = new StartPipelineConfig();

  TestStartPipelineProcessorBuilder() {
    conf.pipelineIdConfigList = new ArrayList<>();
  }

  TestStartPipelineProcessorBuilder baseUrl(String baseUrl) {
    conf.baseUrl = baseUrl;
    return this;
  }

  TestStartPipelineProcessorBuilder pipelineIdConfig(String pipelineId, String runtimeParameters) {
    PipelineIdConfig pipelineIdConfig = new PipelineIdConfig();
    pipelineIdConfig.pipelineId = pipelineId;
    pipelineIdConfig.runtimeParameters = runtimeParameters;
    conf.pipelineIdConfigList.add(pipelineIdConfig);
    return this;
  }

  StartPipelineProcessor build() {
    return new StartPipelineProcessor(conf);
  }

}
