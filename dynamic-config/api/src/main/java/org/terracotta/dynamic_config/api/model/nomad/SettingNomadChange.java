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
package org.terracotta.dynamic_config.api.model.nomad;

import org.terracotta.dynamic_config.api.model.Cluster;
import org.terracotta.dynamic_config.api.model.Configuration;
import org.terracotta.dynamic_config.api.model.Node;
import org.terracotta.dynamic_config.api.model.NodeContext;
import org.terracotta.dynamic_config.api.model.Operation;
import org.terracotta.dynamic_config.api.model.Scope;
import org.terracotta.dynamic_config.api.model.Setting;
import org.terracotta.dynamic_config.api.model.Stripe;
import org.terracotta.dynamic_config.api.model.UID;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.terracotta.dynamic_config.api.model.ClusterState.ACTIVATED;
import static org.terracotta.dynamic_config.api.model.Requirement.CLUSTER_RESTART;
import static org.terracotta.dynamic_config.api.model.Requirement.NODE_RESTART;

/**
 * Nomad change that supports any dynamic config change
 *
 * @author Mathieu Carbou
 */
public class SettingNomadChange extends FilteredNomadChange {

  private final Operation operation;
  private final Setting setting;
  private final String name;
  private final String value;

  protected SettingNomadChange(Applicability applicability,
                               Operation operation,
                               Setting setting,
                               String name,
                               String value) {
    super(applicability);
    this.operation = requireNonNull(operation);
    this.setting = requireNonNull(setting);
    this.name = name;
    this.value = value;
  }

  @Override
  public String getSummary() {
    String s = operation == Operation.SET ?
        name == null ? (operation + " " + setting + "=" + value) : (operation + " " + setting + "." + name + "=" + value) :
        name == null ? (operation + " " + setting) : (operation + " " + setting + "." + name);
    switch (getApplicability().getLevel()) {
      case STRIPE:
      case NODE:
        return s + " (on " + getApplicability() + ")";
      default:
        return s;
    }
  }

  @Override
  public Cluster apply(Cluster original) {
    Cluster updated = original.clone();
    Configuration configuration = toConfiguration(updated);
    configuration.validate(ACTIVATED, getOperation());
    configuration.apply(updated);
    return updated;
  }

  @Override
  public boolean canApplyAtRuntime(NodeContext currentNode) {
    Setting setting = getSetting();
    boolean requiresClusterRestart = setting.requires(CLUSTER_RESTART);
    boolean requiresThisNodeRestart = setting.requires(NODE_RESTART) && getSetting().isScope(Scope.NODE) && getApplicability().isApplicableTo(currentNode);
    return !requiresClusterRestart && !requiresThisNodeRestart;
  }

  public String getName() {
    return name;
  }

  public String getValue() {
    return value;
  }

  public Setting getSetting() {
    return setting;
  }

  public Operation getOperation() {
    return operation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SettingNomadChange)) return false;
    if (!super.equals(o)) return false;
    SettingNomadChange that = (SettingNomadChange) o;
    return getOperation() == that.getOperation() &&
        getSetting() == that.getSetting() &&
        Objects.equals(getName(), that.getName()) &&
        Objects.equals(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getOperation(), getSetting(), getName(), getValue());
  }

  @Override
  public String toString() {
    return "SettingNomadChange{" +
        "operation=" + operation +
        ", setting=" + setting +
        ", name='" + name + '\'' +
        ", value='" + value + '\'' +
        ", applicability=" + getApplicability() +
        '}';
  }

  public Configuration toConfiguration(Cluster cluster) {
    switch (operation) {
      case SET:
        return name == null ?
            Configuration.valueOf(namespace(cluster) + setting + "=" + value) :
            Configuration.valueOf(namespace(cluster) + setting + "." + name + "=" + value);
      case UNSET:
        return name == null ?
            Configuration.valueOf(namespace(cluster) + setting.toString()) :
            Configuration.valueOf(namespace(cluster) + setting + "." + name);
      default:
        throw new AssertionError(operation);
    }
  }

  private String namespace(Cluster cluster) {
    switch (getApplicability().getLevel()) {
      case CLUSTER:
        return "";
      case STRIPE: {
        int stripeId = getApplicability().getStripe(cluster)
            .map(stripe -> cluster.getStripeId(stripe.getUID())
                .orElseThrow(() -> new IllegalArgumentException("Stripe UID: " + stripe.getUID() + " not found in cluster: " + cluster.toShapeString())))
            .orElseThrow(() -> new IllegalArgumentException("Stripe not found in cluster: " + cluster.toShapeString() + " with applicability: " + getApplicability()));
        return "stripe." + stripeId + ".";
      }
      case NODE: {
        int stripeId = getApplicability().getStripe(cluster)
            .map(stripe -> cluster.getStripeId(stripe.getUID())
                .orElseThrow(() -> new IllegalArgumentException("Stripe UID: " + stripe.getUID() + " not found in cluster: " + cluster.toShapeString())))
            .orElseThrow(() -> new IllegalArgumentException("Stripe not found in cluster: " + cluster.toShapeString() + " with applicability: " + getApplicability()));
        int nodeId = getApplicability().getNode(cluster)
            .map(node -> getNodeId(cluster, node.getUID()))
            .orElseThrow(() -> new IllegalArgumentException("Node not found in cluster: " + cluster.toShapeString() + " with applicability: " + getApplicability()));
        return "stripe." + stripeId + ".node." + nodeId + ".";
      }
      default:
        throw new AssertionError(getApplicability().getLevel());
    }
  }

  public static SettingNomadChange set(Applicability applicability, Setting type, String name, String value) {
    return new SettingNomadChange(applicability, Operation.SET, type, name, value);
  }

  public static SettingNomadChange unset(Applicability applicability, Setting type, String name) {
    return new SettingNomadChange(applicability, Operation.UNSET, type, name, null);
  }

  public static SettingNomadChange set(Applicability applicability, Setting type, String value) {
    return new SettingNomadChange(applicability, Operation.SET, type, null, value);
  }

  public static SettingNomadChange unset(Applicability applicability, Setting type) {
    return new SettingNomadChange(applicability, Operation.UNSET, type, null, null);
  }

  public static SettingNomadChange fromConfiguration(Configuration configuration, Operation operation, Cluster cluster) {
    Applicability applicability = toApplicability(configuration, cluster);
    switch (operation) {
      case SET:
        return SettingNomadChange.set(applicability, configuration.getSetting(), configuration.getKey(), configuration.getValue().get());
      case UNSET:
        return SettingNomadChange.unset(applicability, configuration.getSetting(), configuration.getKey());
      default:
        throw new IllegalArgumentException("Operation " + operation + " cannot be converted to a Nomad change for an active cluster");
    }
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  private static Applicability toApplicability(Configuration configuration, Cluster cluster) {
    switch (configuration.getLevel()) {
      case NODE:
        return Applicability.node(cluster.getStripe(configuration.getStripeId()).flatMap(stripe -> getNode(stripe, configuration.getNodeId())).get().getUID());
      case STRIPE:
        return Applicability.stripe(cluster.getStripe(configuration.getStripeId()).get().getUID());
      case CLUSTER:
        return Applicability.cluster();
      default:
        throw new AssertionError(configuration.getLevel());
    }
  }

  private static Optional<Node> getNode(Stripe stripe, int nodeId) {
    if (nodeId < 1) {
      throw new IllegalArgumentException("Invalid node ID: " + nodeId);
    }
    if (nodeId > stripe.getNodeCount()) {
      return Optional.empty();
    }
    return Optional.of(stripe.getNodes().get(nodeId - 1));
  }

  public int getNodeId(Cluster cluster, UID nodeUID) {
    for (Stripe stripe : cluster.getStripes()) {
      List<Node> nodes = stripe.getNodes();
      for (int i = 0; i < nodes.size(); i++) {
        if (nodes.get(i).getUID().equals(nodeUID)) {
          return i + 1;
        }
      }
    }
    throw new IllegalArgumentException("Node UID: " + nodeUID + " not found in cluster: " + cluster.toShapeString());
  }
}
