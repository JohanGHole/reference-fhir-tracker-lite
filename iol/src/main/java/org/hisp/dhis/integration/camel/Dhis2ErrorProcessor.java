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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;
import org.hisp.dhis.integration.sdk.api.RemoteDhis2ClientException;
import org.springframework.stereotype.Component;

/**
 * Translates a DHIS2 error response into the variables consumed by {@code direct:operationOutcome}.
 *
 * <p>The camel-dhis2 producer may wrap the SDK exception (e.g. in a RuntimeCamelException), so the
 * cause chain is searched rather than relying on the top-level exception type.
 */
@Component("dhis2ErrorProcessor")
public class Dhis2ErrorProcessor implements Processor {

  @Override
  public void process(Exchange exchange) {
    Throwable caught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
    RemoteDhis2ClientException remote =
        ObjectHelper.getException(RemoteDhis2ClientException.class, caught);

    int dhis2Status = remote != null ? remote.getHttpStatusCode() : -1;
    boolean rejected = dhis2Status == 409;

    exchange.setVariable("issueCode", rejected ? "business-rule" : "exception");
    exchange.setVariable(
        "diagnostics",
        remote != null && remote.getBody() != null
            ? remote.getBody()
            : String.valueOf(caught != null ? caught.getMessage() : "Unknown DHIS2 error"));
    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, rejected ? 409 : 502);
  }
}
