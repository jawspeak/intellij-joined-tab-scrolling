package com.jawspeak.intellij;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.VisualPosition;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
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
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    logger.info("fileOpened: opened file=" + file.getCanonicalPath()
        + " openedCount=" + openCount.incrementAndGet());
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
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
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    // Don't care, as we observe visible area changes instead.
  }

  @Override
  public void visibleAreaChanged(VisibleAreaEvent event) {
    Editor masterEditor = event.getEditor();
    try {
      // Disable while we move things. Must remember to always add it back on.
      // Doing everything on this thread and not with SwingUtilities to ensure single threaded.
      masterEditor.getScrollingModel().removeVisibleAreaListener(this);

      VirtualFile virtualFile =
          FileDocumentManager.getInstance().getFile(masterEditor.getDocument());

      List<Editor> allTheseShowingEditors = new ArrayList<>();
      for (Editor e : EditorFactory.getInstance().getEditors(masterEditor.getDocument())) {
        if (e.getComponent().isShowing()) {
          allTheseShowingEditors.add(e);
        }
      }

      if (allTheseShowingEditors.size() < 2) {
        logger.info("visibleAreaChanged: <2 showing editors for file="
            + (virtualFile != null ? virtualFile.getCanonicalPath() : "<null>")
            + " editors=" + listShortObjects(allTheseShowingEditors));
        return;
      }

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
      masterEditor.getScrollingModel().addVisibleAreaListener(this);
    }
  }

  private static void syncJoinedTabScrolling(String filePathWeAreWorkingOn /*used for logging and debugging. hack hack hack.*/,
      Editor master, List<Editor> allTheseEditors) {
    int masterIndex = -1;
    // TODO later can make more efficient w/ a doubly linked list or something.
    for (int i = 0; i < allTheseEditors.size(); i++) {
      if (allTheseEditors.get(i) == master) {
        masterIndex = i;
        break;
      }
    }
    if (masterIndex < 0) {
      logger.error("Did not find masterIndex - a bug");
    }

    // only scroll one to the left or right because events will cascade onward to others.
    if (masterIndex - 1 >= 0) {
      Editor slave = allTheseEditors.get(masterIndex - 1);
      scroll(master, slave, SlavePosition.SLAVE_LEFT_OF_MASTER);
    }

    if (masterIndex + 1 < allTheseEditors.size()) {
      Editor slave = allTheseEditors.get(masterIndex + 1);
      scroll(master, slave, SlavePosition.SLAVE_RIGHT_OF_MASTER);
    }
  }

  private enum SlavePosition {
    SLAVE_LEFT_OF_MASTER,
    SLAVE_RIGHT_OF_MASTER
  }

  private static class EditorTopBottom {
    final int column;
    final int topLine;
    final int bottomLine;
    final int verticalScrollOffset;
    final int linesVisible;
    final Editor editor;

    EditorTopBottom(Editor editor) {
      this.editor = editor;
      ScrollingModel scrolling = editor.getScrollingModel();
      verticalScrollOffset = scrolling.getVerticalScrollOffset();
      // convert to visual position (including folding) if this is logical (ignoring folding).
      VisualPosition vp = editor.xyToVisualPosition(
          new Point(scrolling.getVisibleArea().x, verticalScrollOffset));
      topLine = vp.line;
      column = vp.column;
      bottomLine = editor.xyToVisualPosition(
          new Point(scrolling.getVisibleArea().x,
              verticalScrollOffset + scrolling.getVisibleArea().height)).line;
      linesVisible = bottomLine - topLine + 1; // inclusive of both lines, so add one.
      Preconditions.checkState(linesVisible >= 0, "Invalid lines visible calculation - bug!");
    }

    public String toString() {
      return "EditorTopBottom{editor=" + shortObjectString(editor) + ", top=" + topLine
          + ", bottom=" + bottomLine + ", linesVisible=" + linesVisible + "}";
    }
  }

  // With inspiration from SyncScrollSupport.java in intellij CE source.
  private static void scroll(Editor master, Editor slave, SlavePosition slavePosition) {
    EditorTopBottom masterTopBottom = new EditorTopBottom(master);
    EditorTopBottom slaveTopBottom = new EditorTopBottom(slave);

    // For slaves to the LEFT of master:  their new top line
    //     = convertFromVisualLinesToXY(master_top_line - (size_of_slave_in_lines -1))

    // For slaves to the RIGHT of master: their new top line
    //     = convertFromVisualLinesToXY(master_top_line + (size_of_master_in_lines - 1))

    // (-1 to give overlap in the two editors so 1 line is the same continuing b/w the two).

    int slaveNewTopLine;
    switch (slavePosition) {
      case SLAVE_LEFT_OF_MASTER:
        slaveNewTopLine = masterTopBottom.topLine - (slaveTopBottom.linesVisible - 1);
        break;
      case SLAVE_RIGHT_OF_MASTER:
        slaveNewTopLine = masterTopBottom.topLine + (masterTopBottom.linesVisible - 1);
        break;
      default:
        throw new RuntimeException("Invalid state - should never happen");
    }
    if (slaveNewTopLine == slaveTopBottom.topLine) {
      return; // already at the desired position.
    }
    slaveNewTopLine = Math.max(0, slaveNewTopLine);
    logger.info("scroll: masterTopBottom=" + masterTopBottom
        + " slaveTopBottom=" + slaveTopBottom
        + " slaveNewTopLine=" + slaveNewTopLine);

    int correction = (masterTopBottom.verticalScrollOffset) % master.getLineHeight();
    Point point = slave.visualPositionToXY(
        new VisualPosition(slaveNewTopLine, masterTopBottom.column));
    int offset = point.y + correction;

    int deltaHeaderOffset = getHeaderOffset(slave) - getHeaderOffset(master);
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
    // Works in developement. Not in prod.
    try {
      if (editor.getScrollingModel().getClass().equals(ScrollingModelImpl.class)) {
        List<VisibleAreaListener> listeners =
            ReflectionUtil.getField(ScrollingModelImpl.class, editor.getScrollingModel(),
                List.class, "myVisibleAreaListeners");
        logger.info(logLabel + ": editor=" + shortObjectString(editor)
            + " currentListeners=" + listShortObjects(listeners));
      }
    } catch (Exception e) {
      logger.error("reflectivelyCheckCurrentListeners: error trying.", e);
    }
  }

  private static String shortObjectString(Object o) {
    if (o == null) {
      return "<null>";
    }
    if (!o.toString().contains(".")) {
      return o.toString();
    }
    return o.toString().substring(o.toString().lastIndexOf('.') + 1);
  }

  private static String listShortObjects(Collection c) {
    if (c == null) {
      return "<unavailable>";
    }
    List<String> collector = new ArrayList<>();
    for (Object o : c) {
      collector.add(shortObjectString(o));
    }
    return Joiner.on(",").join(collector);
  }
}
