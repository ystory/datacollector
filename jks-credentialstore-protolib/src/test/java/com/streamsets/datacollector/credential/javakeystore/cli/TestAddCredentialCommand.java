/*
 * Copyright 2017 StreamSets Inc.
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
package com.streamsets.datacollector.credential.javakeystore.cli;

import com.streamsets.datacollector.credential.javakeystore.JavaKeyStoreCredentialStore;
import org.junit.Test;
import org.mockito.Mockito;

public class TestAddCredentialCommand {

  @Test
  public void testExecute() {
    JavaKeyStoreCredentialStore store = Mockito.mock(JavaKeyStoreCredentialStore.class);
    AddCredentialCommand command = new AddCredentialCommand();
    command.name = "name";
    command.credential = "credential";

    command.execute(store);
    Mockito.verify(
        store,
        Mockito.times(1)
    ).store(Mockito.anyListOf(String.class), Mockito.eq("name"), Mockito.eq("credential"));
    Mockito.verifyNoMoreInteractions(store);
  }

}
