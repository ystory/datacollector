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

import com.streamsets.datacollector.config.PipelineConfiguration;
import com.streamsets.datacollector.task.Task;
import com.streamsets.pipeline.api.ErrorCode;

/**
 * Task that handles stats collection for the data collector
 */
public interface StatsCollector extends Task {

  /**
   * Returns if stats collection has been explicitly opted or not.
   *
   * @return if stats collection has been explicitly opted or not.
   */
  boolean isOpted();

  /**
   * Returns if stats collection has been opted IN.
   *
   * @return if stats collection has been opted IN.
   */
  boolean isActive();

  /**
   * Sets if stats collections is opted IN or OUT.
   *
   * @param active true opts IN, false opts OUT.
   */
  void setActive(boolean active);

  /**
   * Tracks pipeline creation.
   *
   * @param pipelineId pipeline configuration to gather pipeline info for stats.
   */
  void createPipeline(String pipelineId);

  /**
   * Tracks pipeline previews.
   *
   * @param pipelineId pipeline configuration to gather pipeline info for stats.
   */
  void previewPipeline(String pipelineId);

  /**
   * Starts tracking stats for a pipeline when a pipeline starts.
   *
   * @param pipeline pipeline configuration to gather pipeline info for stats.
   */
  void startPipeline(PipelineConfiguration pipeline);

  /**
   * Stops tracking stats for a pipeline when a pipeline stops.
   *
   * @param pipeline pipeline configuration to gather pipeline info for stats.
   */
  void stopPipeline(PipelineConfiguration pipeline);

  /**
   * Track that given error code was used.
   */
  void errorCode(ErrorCode errorCode);

  /**
   * To be called at the end of each batch using the number of records of the origin batch.
   * <p/>
   *
   * @param count records emitted by the origin.
   */
  void incrementRecordCount(long count);

  /**
   * Returns the StatsInfo of the data collector.
   *
   * @return the StatsInfo of the data collector.
   */
  StatsInfo getStatsInfo();

}
