/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

namespace java org.apache.airavata.model.appcatalog.credentialsummary
namespace php Airavata.Model.AppCatalog.CredetialSummary
namespace cpp apache.airavata.model.appcatalog.credentialsummary
namespace py apache.airavata.model.appcatalog.credentialsummary

struct CredentialSummary {
    1: required string gatewayId,
    2: required string username,
    3: optional string publicKey,
    4: optional i64 persistedTime,
    5: optional string description
}