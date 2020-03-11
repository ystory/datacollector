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
package com.streamsets.datacollector.restapi.rbean.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamsets.datacollector.restapi.rbean.lang.RChar;

import java.util.function.Function;

public class RCharJacksonDeserializer extends AbstractRValueJacksonDeserializer<Character, RChar> {

  public RCharJacksonDeserializer() {
    super(RChar.class);
  }

  @Override
  protected Function<JsonNode, Character> getConvertIfNotNull() {
    return n -> n.asText().isEmpty() ? null : n.asText().charAt(0);
  }

}
