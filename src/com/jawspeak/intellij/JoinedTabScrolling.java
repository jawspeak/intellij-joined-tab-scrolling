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

    // Lots of different ways to grab editor windows.
    // 1. which is best to give me each tabs?
    // 2. copy to new file. later put this as a gist.
    // 3. find all tabs that are linked. find scroll posn. set scroll position.
    // 4. trigger continuously on any scroll / state change. not just when ran manually.

  public JoinedTabScrolling(Project project) {
    this.project = project;
    listener = new JoinedScroller(project);
  }

  @Override
  public void projectOpened() {
    logger.warn("project opened " + project);
  }

  @Override
  public void projectClosed() {
    logger.warn("project closed " + project);
  }

  @Override
  public void initComponent() {
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
    logger.warn("project initialized");
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
