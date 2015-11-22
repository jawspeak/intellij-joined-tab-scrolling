package com.jawspeak.intellij;

import com.google.common.base.Preconditions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.impl.ScrollingModelImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionUtil;
import java.awt.Point;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for scroll change events in "interested editors" (the split ones), and then moves the
 * others to stay in sync. With inspiration from my buddy Mike's request.
 */
public class JoinedScroller
    implements FileEditorManagerListener, VisibleAreaListener, EditorFactoryListener {
  private final static Logger logger = Logger.getInstance(JoinedScroller.class.getName());
  private final Set<Editor> openedEditors = new HashSet<>();
  private final static AtomicInteger openCount = new AtomicInteger();
  private final static AtomicInteger closedCount = new AtomicInteger();
  private final static AtomicInteger editorCreatedCount = new AtomicInteger();
  private final static AtomicInteger editorReleasedCount = new AtomicInteger();
  private final Project project;

  public JoinedScroller(Project project) {
    this.project = project;
  }

  @Override
  public void fileOpened(FileEditorManager source, VirtualFile file) {
    logger.info("fileOpened: opened file=" + file.getCanonicalPath()
        + " openedCount=" + openCount.incrementAndGet());
  }

  @Override
  public void fileClosed(FileEditorManager source, VirtualFile file) {
    // no-op handle editors closing below.
    logger.info("fileClosed: closed file=" + file.getCanonicalPath()
        + " closedCount=" + closedCount.incrementAndGet());
  }

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    if (!openedEditors.contains(editor)) {
      reflectivelyCheckCurrentListeners("editorCreated.before", editor);
      openedEditors.add(editor);
      editor.getScrollingModel().addVisibleAreaListener(this);

      logger.info("editorCreated: createdCount=" + editorCreatedCount.incrementAndGet()
          + " listening for editor=" + shortObjectString(editor)
          + " openedEditors=" + listShortObjects(openedEditors));
      reflectivelyCheckCurrentListeners("editorCreated.after", editor);
    } else {
      logger.warn("editorCreated: createdCount=" + editorCreatedCount.incrementAndGet()
          + " (should not happen) already contains editor=" + shortObjectString(editor));
    }
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    if (openedEditors.contains(editor)) {
      logger.info("editorReleased: releasedCount=" + editorReleasedCount.incrementAndGet()
          + " removed listener for editor=" + shortObjectString(editor)
          + " openedEditors=" + listShortObjects(openedEditors));
      reflectivelyCheckCurrentListeners("editorReleased.before", editor);

      editor.getScrollingModel().removeVisibleAreaListener(this);
      openedEditors.remove(editor);
      reflectivelyCheckCurrentListeners("editorReleased.after", editor);
    } else {
      logger.warn("editorReleased: releasedCount=" + editorReleasedCount.incrementAndGet()
          + " (should not happen) released editor we were not tracking editor="
          + shortObjectString(editor));
    }
  }

  @Override
  public void selectionChanged(FileEditorManagerEvent event) {
    // Don't care, as we observe visible area changes instead.
  }

  @Override
  public void visibleAreaChanged(VisibleAreaEvent event) {
    Editor masterEditor = event.getEditor();

    // TODO kinda ugly adding and removing this. can't forget to add back.
    masterEditor.getScrollingModel().removeVisibleAreaListener(this);

    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(masterEditor.getDocument());
    List<Editor> allTheseShowingEditors =
        Arrays.asList(EditorFactory.getInstance().getEditors(masterEditor.getDocument()))
            .stream()
            .filter(e -> e.getComponent().isShowing())
            .collect(Collectors.toList());;

    if (allTheseShowingEditors.size() < 2) {
      logger.info("visibleAreaChanged: <2 showing editors for file="
          + (virtualFile != null ? virtualFile.getCanonicalPath() : "<null>")
          + " editors=" + listShortObjects(allTheseShowingEditors));

      // Re-enable listener.
      masterEditor.getScrollingModel().addVisibleAreaListener(this);
      return;
    }

    // I think we are supposed to do this with Swing... Didn't pay much attention.
    // TODO thread concurrency issues? Likely if scrolling / events happen fast enough.
    SwingUtilities.invokeLater(() -> {
      try {
        // sort all editors by their location on the screen Left to Right, Top to Bottom.
        Collections.sort(allTheseShowingEditors, (e1, e2) -> {
          if (!e1.getComponent().isShowing() || !e2.getComponent().isShowing()) {
            return 0; // don't try to look when not on the screen.
          }
          Point e1Location = e1.getComponent().getLocationOnScreen();
          Point e2Location = e2.getComponent().getLocationOnScreen();
          if (e1Location.getX() != e2Location.getX()) {
            return (int) (e1Location.getX() - e2Location.getX());
          }
          return (int) (e1Location.getY() - e2Location.getY());
        });
        syncJoinedTabScrolling(virtualFile.getCanonicalPath(), masterEditor, allTheseShowingEditors);

      } finally {
        // Re-enable listener.
        masterEditor.getScrollingModel().addVisibleAreaListener(JoinedScroller.this);
      }
    });
  }

  private static void syncJoinedTabScrolling(String filePathWeAreWorkingOn /*used for logging and debugging. hack hack hack.*/,
      Editor masterEditor, List<Editor> allTheseEditors) {
    int masterIndex = -1;
    // TODO later can make more efficient w/ a doubly linked list or something.
    for (int i = 0; i < allTheseEditors.size(); i++) {
      if (allTheseEditors.get(i) == masterEditor) {
        masterIndex = i;
        break;
      }
    }
    if (masterIndex < 0) {
      logger.error("Did not find masterIndex - a bug");
    }

    // only scroll one to the left or right because events will cascade onward to others.
    if (masterIndex - 1 >= 0) {
      int directionCoefficient = -1;
      scroll(masterEditor, allTheseEditors, masterIndex - 1, directionCoefficient);
    }

    if (masterIndex + 1 < allTheseEditors.size()) {
      int directionCoefficient = +1;
      scroll(masterEditor, allTheseEditors, masterIndex + 1, directionCoefficient);
    }
  }

  // With inspiration from SyncScrollSupport.java in intellij CE source.
  private static void scroll(Editor masterEditor, List<Editor> allTheseEditors, int i,
      int directionCoefficient) {
    ScrollingModel masterScrollingModel = masterEditor.getScrollingModel();
    int masterVerticalScrollOffset = masterScrollingModel.getVerticalScrollOffset();

    Editor slave = allTheseEditors.get(i);
    ScrollingModel slaveScrollingModel = slave.getScrollingModel();
    int slaveVerticalScrollOffset = slaveScrollingModel.getVerticalScrollOffset();

    LogicalPosition masterPos = masterEditor.xyToLogicalPosition(
        new Point(masterScrollingModel.getVisibleArea().x, masterVerticalScrollOffset));
    int masterTopLine = masterPos.line
        - 2 * directionCoefficient; // move the top line so we have a bit of line overlap.

    LogicalPosition slavePos = slave.xyToLogicalPosition(
        new Point(slaveScrollingModel.getVisibleArea().x, slaveVerticalScrollOffset));
    int slaveTopLine = slavePos.line;
    int slaveBottomLine = slave.xyToLogicalPosition(
        new Point(slaveScrollingModel.getVisibleArea().x,
            slaveVerticalScrollOffset + slaveScrollingModel.getVisibleArea().height)).line;
    int slaveHeight = slaveBottomLine - slaveTopLine;
    // for slaves their top offset should = convertFromLines(master_line - 1 - size_of_slave_in_lines)
    int scrollToLine =
        Math.max(0, masterTopLine + directionCoefficient + directionCoefficient * slaveHeight);
    logger.debug("scroll: master=" + shortObjectString(masterEditor)
        + " masterTopLine=" + masterTopLine
        + " slaveHeight=" + slaveHeight
        + " slaveTopLine=" + slaveTopLine
        + " scrollToLine=" + scrollToLine);

    int correction = (masterVerticalScrollOffset) % masterEditor.getLineHeight();
    Point point = slave.logicalPositionToXY(new LogicalPosition(scrollToLine, masterPos.column));
    int offset = point.y + correction;

    int deltaHeaderOffset = getHeaderOffset(slave) - getHeaderOffset(masterEditor);
    doScrollVertically(slave.getScrollingModel(), offset + deltaHeaderOffset);
  }

  private static void doScrollVertically(@NotNull ScrollingModel model, int offset) {
    model.disableAnimation();
    try {
      model.scrollVertically(offset);
    } finally {
      model.enableAnimation();
    }
  }

  private static int getHeaderOffset(@NotNull final Editor editor) {
    final JComponent header = editor.getHeaderComponent();
    return header == null ? 0 : header.getHeight();
  }

  private static void reflectivelyCheckCurrentListeners(String logLabel, Editor editor) {
    if (editor.getScrollingModel().getClass().equals(ScrollingModelImpl.class)) {
      List<VisibleAreaListener> listeners =
          ReflectionUtil.getField(ScrollingModelImpl.class, editor.getScrollingModel(),
              List.class, "myVisibleAreaListeners");
      logger.info(logLabel + ": editor=" + shortObjectString(editor)
          + " currentListeners=" + listShortObjects(listeners));
    }
  }

  private static String shortObjectString(Object o) {
    if (!o.toString().contains(".")) {
      return o.toString();
    }
    return o.toString().substring(o.toString().lastIndexOf('.') + 1);
  }

  private static String listShortObjects(Collection c) {
    //return (String) l.stream().map(Object::toString).collect(Collectors.joining(","));
    return (String) c.stream()
        .map(JoinedScroller::shortObjectString)
        .collect(Collectors.joining(","));
  }
}
