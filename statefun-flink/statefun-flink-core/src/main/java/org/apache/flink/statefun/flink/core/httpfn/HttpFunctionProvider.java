/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.statefun.flink.core.httpfn;

import java.util.Map;
import okhttp3.OkHttpClient;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.StatefulFunctionProvider;

public class HttpFunctionProvider implements StatefulFunctionProvider {
  private final Map<FunctionType, HttpFunctionSpec> supportedTypes;
  private final OkHttpClient client;

  public HttpFunctionProvider(Map<FunctionType, HttpFunctionSpec> supportedTypes) {
    this.supportedTypes = supportedTypes;
    final long timeoutMs = 30_000;
    // TODO: add various timeouts to HttpFunctionSpec
    this.client =
        OkHttpUtils.newClient(
            timeoutMs, timeoutMs, 2 * timeoutMs, timeoutMs, Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Override
  public HttpFunction functionOfType(FunctionType type) {
    HttpFunctionSpec spec = supportedTypes.get(type);
    if (spec == null) {
      throw new IllegalArgumentException("Unsupported type " + type);
    }
    return new HttpFunction(spec, client);
  }
}