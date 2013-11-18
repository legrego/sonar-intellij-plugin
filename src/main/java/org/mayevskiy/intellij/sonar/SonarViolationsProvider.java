package org.mayevskiy.intellij.sonar;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.mayevskiy.intellij.sonar.sonarserver.SonarService;
import org.sonar.wsclient.services.Violation;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: Oleg Mayevskiy
 * Date: 22.04.13
 * Time: 13:40
 */
@State(
    name = "SonarViolationsProvider",
    storages = {
        @Storage(id = "other", file = "$PROJECT_FILE$")
    }
)
public class SonarViolationsProvider implements PersistentStateComponent<SonarViolationsProvider> {

  public Map<String, Collection<Violation>> mySonarViolations;

  @Transient
  private Project project;

  // fixes Could not save project: java.lang.InstantiationException
  public SonarViolationsProvider() {
    mySonarViolations = new ConcurrentHashMap<String, Collection<Violation>>();
  }

  @SuppressWarnings("UnusedDeclaration")
  public SonarViolationsProvider(Project project) {
    this();
  }

  @NotNull
  @Override
  public SonarViolationsProvider getState() {
    return this;
  }

  @Override
  public void loadState(SonarViolationsProvider state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void syncWithSonar(final Project project) {
    clearState();
    Collection<SonarSettingsBean> allSonarSettingsBeans = SonarSettingsComponent.getSonarSettingsBeans(project);
    this.mySonarViolations = getViolationsFromSonar(allSonarSettingsBeans);
  }

  private Map<String, Collection<Violation>> getViolationsFromSonar(Collection<SonarSettingsBean> allSonarSettingsBeans) {
    Map<String, Collection<Violation>> violationsMap = this.mySonarViolations;
    if (null == violationsMap) {
      violationsMap = new ConcurrentHashMap<String, Collection<Violation>>();
    }
    SonarService sonarService = ServiceManager.getService(SonarService.class);
    for (SonarSettingsBean sonarSettingsBean : allSonarSettingsBeans) {
      if (sonarSettingsBean.isEmpty()) {
        continue;
      }
      List<Violation> violations = sonarService.getViolations(sonarSettingsBean);
      for (Violation violation : violations) {
        String resourceKey = violation.getResourceKey();
        Collection<Violation> violationsOfFile = violationsMap.get(resourceKey);
        if (null == violationsOfFile) {
          violationsOfFile = new LinkedList<Violation>();
          violationsMap.put(resourceKey, violationsOfFile);
        }

        boolean violationAlreadyExists = false;
        for (Violation alreadyExistingViolation : violationsOfFile) {
          if (SonarViolationUtils.isEqual(alreadyExistingViolation, violation)) {
            violationAlreadyExists = true;
            break;
          }
        }

        if (!violationAlreadyExists) {
          violationsOfFile.add(violation);
        }
      }
    }

    return violationsMap;
  }

  private void clearState() {
    if (null != this.mySonarViolations) {
      mySonarViolations.clear();
    }
  }
}
