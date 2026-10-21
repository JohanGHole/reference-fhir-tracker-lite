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
package org.hisp.dhis.integration.camel.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

/**
 * Tier 2: runs the real Spring Boot app and routes against an in-process fake DHIS2 Web API (a
 * Jetty-backed Camel route). Covers the HTTP contract and every error branch without Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamelSpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReceivePatientRouteTestCase {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int FAKE_DHIS2_PORT = TestSocketUtils.findAvailableTcpPort();

  private static final AtomicReference<String> lastTrackerImport = new AtomicReference<>();
  private static volatile int fakeDhis2Status;
  private static volatile String fakeDhis2Body;
  private static CamelContext fakeDhis2;

  @Value("${local.server.port}")
  private int serverPort;

  @DynamicPropertySource
  static void dhis2Properties(DynamicPropertyRegistry registry) {
    registry.add("dhis2.api.url", () -> "http://localhost:" + FAKE_DHIS2_PORT + "/api");
  }

  @BeforeAll
  public static void startFakeDhis2() throws Exception {
    // OperationOutcomes are returned as application/fhir+json
    RestAssured.registerParser("application/fhir+json", Parser.JSON);

    fakeDhis2 = new DefaultCamelContext();
    fakeDhis2.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("jetty:http://localhost:" + FAKE_DHIS2_PORT + "/api/tracker")
                .routeId("fakeDhis2Tracker")
                .process(
                    exchange -> {
                      lastTrackerImport.set(exchange.getMessage().getBody(String.class));
                      exchange.getMessage().removeHeaders("*");
                      exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, fakeDhis2Status);
                      exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                      exchange.getMessage().setBody(fakeDhis2Body);
                    });
          }
        });
    fakeDhis2.start();
  }

  @AfterAll
  public static void stopFakeDhis2() {
    fakeDhis2.close();
  }

  @BeforeEach
  public void beforeEach() throws Exception {
    lastTrackerImport.set(null);
    fakeDhis2Status = 200;
    fakeDhis2Body = readString("dhis2/tracker-import-report-ok.json");
  }

  @Test
  public void testValidPatientIsImportedIntoDhis2() throws Exception {
    given()
        .contentType("application/fhir+json")
        .body(readString("fhir/patient-kari-nordmann.json"))
        .when()
        .post(patientUrl())
        .then()
        .statusCode(201)
        .body("trackedEntity", equalTo("TeKariNord1"));

    Map<String, Object> expected =
        OBJECT_MAPPER.readValue(
            readString("expected/tracker-payload-kari-nordmann.json")
                .replace("<TODAY>", LocalDate.now().toString()),
            Map.class);
    assertEquals(expected, OBJECT_MAPPER.readValue(lastTrackerImport.get(), Map.class));
  }

  @Test
  public void testPatientWithoutNationalIdIsRejectedBeforeReachingDhis2() throws Exception {
    given()
        .contentType("application/fhir+json")
        .body(readString("fhir/patient-no-national-id.json"))
        .when()
        .post(patientUrl())
        .then()
        .statusCode(422)
        .body("resourceType", equalTo("OperationOutcome"))
        .body("issue[0].code", equalTo("required"));

    assertNull(lastTrackerImport.get());
  }

  @Test
  public void testNonPatientResourceIsRejected() throws Exception {
    given()
        .contentType("application/fhir+json")
        .body(readString("fhir/observation-not-a-patient.json"))
        .when()
        .post(patientUrl())
        .then()
        .statusCode(400)
        .body("issue[0].code", equalTo("invalid"))
        .body("issue[0].diagnostics", containsString("Observation"));

    assertNull(lastTrackerImport.get());
  }

  @Test
  public void testMalformedJsonIsRejected() {
    given()
        .contentType("application/fhir+json")
        .body("{ this is not json")
        .when()
        .post(patientUrl())
        .then()
        .statusCode(400)
        .body("issue[0].code", equalTo("structure"));

    assertNull(lastTrackerImport.get());
  }

  @Test
  public void testDhis2ValidationErrorIsSurfacedAsConflict() throws Exception {
    fakeDhis2Status = 409;
    fakeDhis2Body = readString("dhis2/tracker-import-report-duplicate.json");

    given()
        .contentType("application/fhir+json")
        .body(readString("fhir/patient-kari-nordmann.json"))
        .when()
        .post(patientUrl())
        .then()
        .statusCode(409)
        .body("issue[0].code", equalTo("business-rule"))
        .body("issue[0].diagnostics", containsString("E1064"));
  }

  @Test
  public void testDhis2ServerErrorIsSurfacedAsBadGateway() throws Exception {
    fakeDhis2Status = 500;
    fakeDhis2Body = "{\"status\":\"ERROR\",\"message\":\"boom\"}";

    given()
        .contentType("application/fhir+json")
        .body(readString("fhir/patient-kari-nordmann.json"))
        .when()
        .post(patientUrl())
        .then()
        .statusCode(502)
        .body("issue[0].code", equalTo("exception"));
  }

  private String patientUrl() {
    return "http://localhost:" + serverPort + "/api/Patient";
  }

  static String readString(String classpathResource) throws java.io.IOException {
    try (var inputStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathResource)) {
      return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}
