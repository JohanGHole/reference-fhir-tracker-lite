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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

/**
 * Tier 3: end-to-end against a real, empty DHIS2 started with Testcontainers and seeded from
 * {@code config/dhis2/metadata.json}. Slow (DHIS2 needs a few minutes on first boot), so it only
 * runs with {@code mvn verify -Pfunctional}.
 */
@Tag("functional")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamelSpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReceivePatientFunctionalTestCase {

  private static final String DHIS2_IMAGE = "dhis2/core:42.5.1";
  private static final String ORG_UNIT_ID = "DemoClinic1";

  private static GenericContainer<?> dhis2Container;

  @Autowired private Dhis2Client dhis2Client;

  @Value("${local.server.port}")
  private int serverPort;

  @BeforeAll
  public static void beforeAll() {
    RestAssured.registerParser("application/fhir+json", Parser.JSON);
    startDhis2();
  }

  @DynamicPropertySource
  static void dhis2Properties(DynamicPropertyRegistry registry) {
    startDhis2();
    registry.add(
        "dhis2.api.url",
        () ->
            String.format(
                "http://%s:%s/api", dhis2Container.getHost(), dhis2Container.getFirstMappedPort()));
  }

  private static synchronized void startDhis2() {
    if (dhis2Container != null) {
      return;
    }
    Network network = Network.newNetwork();
    PostgreSQLContainer<?> db =
        new PostgreSQLContainer<>(
                DockerImageName.parse("ghcr.io/baosystems/postgis:13-3.4")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("dhis")
            .withUsername("dhis")
            .withPassword("dhis")
            .withNetwork(network)
            .withNetworkAliases("dhis2-db");
    db.start();

    dhis2Container =
        new GenericContainer<>(DHIS2_IMAGE)
            .withCopyFileToContainer(
                org.testcontainers.utility.MountableFile.forHostPath("../config/dhis2/dhis.conf"),
                "/opt/dhis2/dhis.conf")
            .withNetwork(network)
            .withExposedPorts(8080)
            .dependsOn(db)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPath("/dhis-web-login/")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(6)));
    dhis2Container.start();
  }

  /** Same steps as config/dhis2/bootstrap.sh, done through the DHIS2 Java SDK. */
  private void bootstrapDhis2() throws Exception {
    String metadata = Files.readString(Path.of("../config/dhis2/metadata.json"));
    Map<String, Object> report =
        dhis2Client
            .post("metadata")
            .withResource(metadata)
            .withParameter("importStrategy", "CREATE_AND_UPDATE")
            .withParameter("atomicMode", "ALL")
            .transfer()
            .returnAs(Map.class);
    assertEquals("OK", report.get("status"), () -> "Metadata import failed: " + report);

    String adminId =
        (String) dhis2Client.get("me").withFields("id").transfer().returnAs(Map.class).get("id");
    for (String collection : List.of("organisationUnits", "teiSearchOrganisationUnits")) {
      dhis2Client
          .post("users/{id}/{collection}/{ou}", adminId, collection, ORG_UNIT_ID)
          .transfer()
          .close();
    }
  }

  @Test
  public void testPatientRoundTripsIntoDhis2AndDuplicatesAreRejected() throws Exception {
    bootstrapDhis2();
    String kari = ReceivePatientRouteTestCase.readString("fhir/patient-kari-nordmann.json");

    String trackedEntity =
        given()
            .contentType("application/fhir+json")
            .body(kari)
            .when()
            .post("http://localhost:" + serverPort + "/api/Patient")
            .then()
            .statusCode(201)
            .extract()
            .path("trackedEntity");

    Map<String, Object> stored =
        dhis2Client
            .get("tracker/trackedEntities/{id}", trackedEntity)
            .withParameter("program", "DemoProgrm1")
            .withParameter("fields", "attributes[attribute,value],enrollments[program]")
            .transfer()
            .returnAs(Map.class);
    List<Map<String, Object>> attributes = (List<Map<String, Object>>) stored.get("attributes");
    assertTrue(
        attributes.contains(Map.of("attribute", "AttrNatIdNo", "value", "13116900216")),
        () -> "Unexpected attributes: " + attributes);

    // Same national ID again: DHIS2's unique-attribute validation must turn this into a 409.
    given()
        .contentType("application/fhir+json")
        .body(kari)
        .when()
        .post("http://localhost:" + serverPort + "/api/Patient")
        .then()
        .statusCode(409)
        .body("issue[0].code", equalTo("business-rule"))
        .body("issue[0].diagnostics", containsString("AttrNatIdNo"));
  }
}
