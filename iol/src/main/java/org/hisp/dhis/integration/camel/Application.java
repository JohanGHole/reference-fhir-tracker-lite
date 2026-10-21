/*
 * Copyright (c) 2004-2026, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.camel;

import java.util.concurrent.TimeUnit;

import org.hisp.dhis.integration.sdk.Dhis2ClientBuilder;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  /**
   * DHIS2 client referenced from the routes as {@code client: "#dhis2Client"}. Supports either a
   * personal access token or basic authentication, mirroring the other DHIS2 reference
   * implementations.
   */
  @Bean
  public Dhis2Client dhis2Client(
      @Value("${dhis2.api.url}") String url,
      @Value("${dhis2.api.pat:#{null}}") String pat,
      @Value("${dhis2.api.username:#{null}}") String username,
      @Value("${dhis2.api.password:#{null}}") String password,
      @Value("${dhis2.api.readTimeoutMs:10000}") int readTimeoutMs) {

    Dhis2ClientBuilder builder;
    if (StringUtils.hasText(pat)) {
      builder = Dhis2ClientBuilder.newClient(url, pat);
    } else if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
      builder = Dhis2ClientBuilder.newClient(url, username, password);
    } else {
      throw new IllegalStateException(
          "Missing DHIS2 credentials: set either `dhis2.api.pat` or both `dhis2.api.username` and `dhis2.api.password`");
    }
    return builder.withReadTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build();
  }
}
