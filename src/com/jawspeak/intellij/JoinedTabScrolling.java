package com.jawspeak.intellij;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Created by jaw on 10/25/15. See META-INF/plugin.xml for more info.
 */
public class JoinedTabScrolling implements ProjectComponent {
  private static final Logger logger = Logger.getInstance("#" + JoinedTabScrolling.class.getName());
  private final JoinedScroller listener;
  private Project project;

  public JoinedTabScrolling(Project project) {
    this.project = project;
    listener = new JoinedScroller(project);
  }

  @Override
  public void projectOpened() {
    logger.info("projectOpened: project=" + project);
  }

  @Override
  public void projectClosed() {
    logger.info("projectClosed: project=" + project);
  }

  @Override
  public void initComponent() {
    logger.info("initComponent: starting");
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    EditorFactory.getInstance().addEditorFactoryListener(listener);
    logger.info("initComponent: done");
  }

  @Override
  public void disposeComponent() {
    // no-op
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "JoinedTabScrollingPlugin";
  }
}
