package com.jawspeak.intellij;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Created by jaw on 10/25/15.
 */
public class JoinedTabScrolling implements ProjectComponent {
  private static final Logger logger = Logger.getInstance("#" + JoinedTabScrolling.class.getName());
  private final JoinedScroller listener;
  private Project project;

  public JoinedTabScrolling(Project project) {
    this.project = project;
    listener = new JoinedScroller();
  }

  @Override
  public void projectOpened() {
    logger.info("project opened " + project);
  }

  @Override
  public void projectClosed() {
    logger.info("project closed " + project);
  }

  @Override
  public void initComponent() {
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    logger.info("project initialized");
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
