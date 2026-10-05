/*
 * Copyright 2023 EPAM Systems.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.digital.data.platform.dgtldcmnt.config;

import com.epam.digital.data.platform.integration.formprovider.client.FormValidationClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link FormValidationClient} feign client.
 *
 * <p>Replaces {@code ddm-starter-validation}, whose {@code ValidationAutoConfiguration} was the only
 * thing this service used from that starter. The starter itself is no longer a dependency — the
 * feign client comes straight from {@code ddm-form-validation-client}.
 */
@Configuration
@ConditionalOnProperty(value = "form-submission-validation.url")
@EnableFeignClients(clients = FormValidationClient.class)
public class FormProviderFeignConfig {
}
