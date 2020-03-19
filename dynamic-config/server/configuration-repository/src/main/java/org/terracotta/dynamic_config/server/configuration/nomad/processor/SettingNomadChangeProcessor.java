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
package org.terracotta.dynamic_config.server.configuration.nomad.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.dynamic_config.api.model.Cluster;
import org.terracotta.dynamic_config.api.model.Configuration;
import org.terracotta.dynamic_config.api.model.NodeContext;
import org.terracotta.dynamic_config.api.model.nomad.SettingNomadChange;
import org.terracotta.dynamic_config.api.service.ClusterValidator;
import org.terracotta.dynamic_config.api.service.TopologyService;
import org.terracotta.dynamic_config.server.api.ConfigChangeHandler;
import org.terracotta.dynamic_config.server.api.ConfigChangeHandlerManager;
import org.terracotta.dynamic_config.server.api.DynamicConfigListener;
import org.terracotta.dynamic_config.server.api.InvalidConfigChangeException;
import org.terracotta.nomad.server.NomadException;

import static java.util.Objects.requireNonNull;

/**
 * Supports the processing of {@link SettingNomadChange} for dynamic configuration.
 * <p>
 * This Nomad processor finds the {@code ConfigChangeHandlerManager} registered in the diagnostic services
 * to fire the Nomad change request to the right handler
 */
public class SettingNomadChangeProcessor implements NomadChangeProcessor<SettingNomadChange> {
  private static final Logger LOGGER = LoggerFactory.getLogger(SettingNomadChangeProcessor.class);

  private final TopologyService topologyService;
  private final ConfigChangeHandlerManager manager;
  private final DynamicConfigListener listener;

  public SettingNomadChangeProcessor(TopologyService topologyService, ConfigChangeHandlerManager manager, DynamicConfigListener listener) {
    this.topologyService = requireNonNull(topologyService);
    this.manager = requireNonNull(manager);
    this.listener = requireNonNull(listener);
  }

  @Override
  public void validate(NodeContext baseConfig, SettingNomadChange change) throws NomadException {
    try {
      LOGGER.info("Validating change: {}", change.getSummary());

      Cluster original = baseConfig.getCluster();
      Configuration configuration = change.toConfiguration(original);

      // validate through external handlers
      ConfigChangeHandler configChangeHandler = getConfigChangeHandlerManager(change);
      configChangeHandler.validate(baseConfig, configuration);

      Cluster updated = change.apply(original);
      new ClusterValidator(updated).validate();
    } catch (InvalidConfigChangeException | RuntimeException e) {
      throw new NomadException("Error when trying to apply setting change '" + change.getSummary() + "': " + e.getMessage(), e);
    }
  }

  @Override
  public void apply(SettingNomadChange change) throws NomadException {
    try {
      if (change.canApplyAtRuntime()) {
        LOGGER.debug("Applying change at runtime: {}", change.getSummary());

        Cluster runtime = topologyService.getRuntimeNodeContext().getCluster();
        Configuration configuration = change.toConfiguration(runtime);

        // calling handler to apply at runtime
        getConfigChangeHandlerManager(change).apply(configuration);

        runtime = change.apply(runtime);
        listener.onSettingChanged(change, runtime);

      } else {
        LOGGER.debug("Change will be applied after restart: {}", change.getSummary());

        Cluster upcoming = topologyService.getUpcomingNodeContext().getCluster();

        upcoming = change.apply(upcoming);
        listener.onSettingChanged(change, upcoming);
      }
    } catch (RuntimeException e) {
      throw new NomadException("Error when applying setting change '" + change.getSummary() + "': " + e.getMessage(), e);
    }
  }

  private ConfigChangeHandler getConfigChangeHandlerManager(SettingNomadChange change) {
    return manager.findConfigChangeHandler(change.getSetting())
        .orElseThrow(() -> new IllegalStateException("No " + ConfigChangeHandler.class.getName() + " found for setting " + change.getSetting()));
  }
}