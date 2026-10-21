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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tier 1: exercises the DataSonnet mapping on its own. No Spring, no HTTP, no Docker; runs in well
 * under a second, so it is the inner loop when editing {@code patient-to-tracker.ds}.
 */
public class PatientToTrackerDataSonnetTestCase {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String ENROLLED_AT = "2026-01-31";

  private CamelContext camelContext;
  private DatasonnetExpression mapping;

  @BeforeEach
  public void beforeEach() throws IOException {
    camelContext = new DefaultCamelContext();
    // The mapping reads its UIDs via cml.properties(); feed it the same values as application.yaml.
    Properties properties = new Properties();
    properties.putAll(
        Map.of(
              "fhir.nationalId.system", "urn:oid:2.16.578.1.12.4.1.4.1",
              "dhis2.orgUnit.id", "DemoClinic1",
              "dhis2.trackedEntityType.id", "DemoPerson1",
              "dhis2.program.id", "DemoProgrm1",
              "dhis2.attribute.givenName", "AttrGivenNm",
              "dhis2.attribute.familyName", "AttrFamilyN",
              "dhis2.attribute.sex", "AttrSexCode",
              "dhis2.attribute.birthDate", "AttrBirthDt",
              "dhis2.attribute.nationalId", "AttrNatIdNo",
              "dhis2.attribute.fhirId", "AttrFhirIdN"));
    camelContext.getPropertiesComponent().setInitialProperties(properties);
    camelContext.start();

    mapping = new DatasonnetExpression("resource:classpath:datasonnet/patient-to-tracker.ds");
    mapping.setBodyMediaType("application/x-java-object");
    mapping.setOutputMediaType("application/x-java-object");
    mapping.setResultType(Map.class);
  }

  @AfterEach
  public void afterEach() {
    camelContext.close();
  }

  @Test
  public void testMapsPatientToTrackedEntityWithEnrollment() throws IOException {
    Map<String, Object> actual = evaluate("fhir/patient-kari-nordmann.json");

    Map<String, Object> expected =
        OBJECT_MAPPER.readValue(
            readString("expected/tracker-payload-kari-nordmann.json").replace("<TODAY>", ENROLLED_AT),
            Map.class);
    assertEquals(expected, actual);
  }

  @Test
  public void testPrefersOfficialNameAndJoinsGivenNames() throws IOException {
    List<Map<String, Object>> attributes = attributesOf(evaluate("fhir/patient-kari-nordmann.json"));
    assertTrue(attributes.contains(Map.of("attribute", "AttrGivenNm", "value", "Kari Anne")));
  }

  @Test
  public void testOmitsAttributesThatAreAbsentInTheSource() throws IOException {
    List<Map<String, Object>> attributes = attributesOf(evaluate("fhir/patient-no-national-id.json"));
    assertTrue(attributes.stream().noneMatch(a -> "AttrNatIdNo".equals(a.get("attribute"))));
    assertEquals(5, attributes.size());
  }

  private Map<String, Object> evaluate(String patientResource) throws IOException {
    DefaultExchange exchange = new DefaultExchange(camelContext);
    exchange.getMessage().setBody(OBJECT_MAPPER.readValue(readString(patientResource), Map.class));
    exchange.setProperty("enrolledAt", ENROLLED_AT);
    return new ValueBuilder(mapping).evaluate(exchange, Map.class);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> attributesOf(Map<String, Object> payload) {
    Map<String, Object> trackedEntity =
        ((List<Map<String, Object>>) payload.get("trackedEntities")).get(0);
    Map<String, Object> enrollment =
        ((List<Map<String, Object>>) trackedEntity.get("enrollments")).get(0);
    return (List<Map<String, Object>>) enrollment.get("attributes");
  }

  static String readString(String classpathResource) throws IOException {
    try (InputStream inputStream = resource(classpathResource)) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static InputStream resource(String classpathResource) {
    return Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathResource);
  }
}
