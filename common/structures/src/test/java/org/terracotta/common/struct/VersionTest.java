/*
 * Copyright Terracotta, Inc.
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
package org.terracotta.common.struct;

import org.junit.Test;
import org.terracotta.common.struct.json.StructJsonModule;
import org.terracotta.json.DefaultJsonFactory;
import org.terracotta.json.Json;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.terracotta.testing.ExceptionMatcher.throwing;

/**
 * @author Mathieu Carbou
 */
public class VersionTest {

  private final Json json = new DefaultJsonFactory().withModule(new StructJsonModule()).create();

  @Test
  public void test_bad_versions() {
    Stream.of("", " ", ".", "-", "&", " . ", "1..", "1.-", "-SNAPSHOT")
        .forEach(v -> assertThat(
            v,
            () -> System.out.println(Version.valueOf(v)),
            is(throwing(instanceOf(IllegalArgumentException.class)))));
  }

  @Test
  public void test_compare_version() {
    assertThat(Version.valueOf("10.7").compareTo(Version.valueOf("11.1")), is(equalTo(-1)));
    assertThat(Version.valueOf("10.0.0.5").compareTo(Version.valueOf("11.0.0.1")), is(equalTo(-1)));
    assertThat(Version.valueOf("10.0.bar-SNAPSHOT").compareTo(Version.valueOf("10.0.foo-SNAPSHOT")), is(equalTo(-4)));
    assertThat(Version.valueOf("10.7.0.3-SNAPSHOT").compareTo(Version.valueOf("10.7.0.3-SNAPSHOT")), is(equalTo(0)));
    assertThat(Version.valueOf("10.7.0.3").compareTo(Version.valueOf("10.7.0.3")), is(equalTo(0)));
    assertThat(Version.valueOf("10.7.0.3").compareTo(Version.valueOf("10.7.0.3-SNAPSHOT")), is(equalTo(1)));
    assertThat(Version.valueOf("10.7.0.3").compareTo(Version.valueOf("10.7.0")), is(equalTo(1)));
    assertThat(Version.valueOf("10.7.0").compareTo(Version.valueOf("10.7.0.3")), is(equalTo(-1)));
    assertThat(Version.valueOf("11").compareTo(Version.valueOf("10.7.0.3")), is(equalTo(1)));
    assertThat(Version.valueOf("11.foo").compareTo(Version.valueOf("11.bar")), is(equalTo(4)));
    assertThat(Version.valueOf("11.foo").compareTo(Version.valueOf("11-SNAPSHOT")), is(equalTo(1)));
    assertThat(Version.valueOf("10.7.1").compareTo(Version.valueOf("10.7.2.3")), is(equalTo(-1)));
    assertThat(Version.valueOf("11-SNAPSHOT").compareTo(Version.valueOf("11-SNAPSHOT")), is(equalTo(0)));
    assertThat(Version.valueOf("10.0-SNAPSHOT").compareTo(Version.valueOf("10.0-SNAPSHOT")), is(equalTo(0)));
    assertThat(Version.valueOf("10.0").compareTo(Version.valueOf("10.0")), is(equalTo(0)));
    assertThat(Version.valueOf("10.0").compareTo(Version.valueOf("10.0.foo")), is(equalTo(-1)));
    assertThat(Version.valueOf("10.0.foo").compareTo(Version.valueOf("10.0.foo")), is(equalTo(0)));
    assertThat(Version.valueOf("10.0.foo-SNAPSHOT").compareTo(Version.valueOf("10.0.foo-SNAPSHOT")), is(equalTo(0)));
    assertThat(Version.valueOf("10.0.foo").compareTo(Version.valueOf("10.0.foo-SNAPSHOT")), is(equalTo(1)));
    assertThat(Version.valueOf("10.0.foo-SNAPSHOT").compareTo(Version.valueOf("10.0.foo")), is(equalTo(-1)));
    assertThat(Version.valueOf("11-SNAPSHOT").compareTo(Version.valueOf("11")), is(equalTo(-1)));
  }

  @Test
  public void test_json() {
    for (String v : new String[]{"5.8.6-pre7", "0.0.9", "3.9.5-internal5", "10.7.0.3", "10.7.0.3-SNAPSHOT", "10.7.0.3-foo-SNAPSHOT", "10.7.0.3.foo-SNAPSHOT"}) {
      assertThat(Version.valueOf(v).toString(), is(equalTo(v)));
      String json = this.json.toString(Version.valueOf(v));
      assertThat(json, is(equalTo("\"" + v + "\"")));
      assertThat(this.json.parse(json, Version.class), is(equalTo(Version.valueOf(v))));
    }
  }
}
