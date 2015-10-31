package com.jawspeak.intellij;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.Point;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for scroll change events in "interested editors" (the split ones), and then moves the
 * others to stay in sync.
 * With inspiration from SyncScrollSupport.java in intellij CE source.
 */
public class JoinedScroller implements FileEditorManagerListener, VisibleAreaListener {
  private final static Logger logger = Logger.getInstance(JoinedScroller.class.getName());

  private Map<String, List<Editor>> activeEditors = new HashMap<>();
  // TODO can i have these? does Editor implement hashcode / equals?

  @Override
  public void fileOpened(FileEditorManager source, VirtualFile file) {
    Editor editor = source.getSelectedTextEditor();

    int line = editor.getSelectionModel().getSelectionStartPosition().line;
    logger.info("just opened " + file.getCanonicalPath() + ", at logical line " + line +
        " editor is " + editor);

    Collection<String> openFiles = Collections2.transform(Arrays.asList(source.getSelectedFiles()),
        virtualFile -> virtualFile.getCanonicalPath());
    if (openFiles.size() > 1 || openFiles.size() == 0) {
      logger.warn("Unexpected: Somehow collection is > 1 OR = 0. contents: " + openFiles);
    }
    String filePath = openFiles.iterator().next();
    if (activeEditors.containsKey(filePath)) {
      activeEditors.get(filePath).add(editor);
    } else {
      activeEditors.put(filePath, Lists.newArrayList(editor));
    }
    logger.info("Active editors now: " + activeEditors);

    editor.getScrollingModel().addVisibleAreaListener(this);
  }

  @Override
  public void fileClosed(FileEditorManager source, VirtualFile file) {
    logger.info("just closed file: " + file.getCanonicalPath());

    Editor[] allRemaining = EditorFactory.getInstance().getAllEditors();
    List<Editor> editors = activeEditors.get(file.getCanonicalPath());
    boolean removed = false;
    for (int i = 0; i < editors.size(); i++) {
      boolean found = false;
      for (Editor remaining : allRemaining) {
        if (editors.get(i) == remaining) {
          found = true;
          break;
        }
      }
      if (!found) {
        editors.remove(i);
        removed = true;
      }
    }
    if (!removed) {
      logger.error("Did not remove closed editor! Why?");
    }
    logger.info("Active editors now: " + activeEditors);
  }

  @Override
  public void selectionChanged(FileEditorManagerEvent event) {
    // Don't care, as we observe visible area changes instead.
  }

  @Override
  public void visibleAreaChanged(VisibleAreaEvent e) {
    Editor thisEditor = e.getEditor();
    thisEditor.getScrollingModel().removeVisibleAreaListener(this);

    // inefficient
    for (Map.Entry<String, List<Editor>> activeEditorEntries : activeEditors.entrySet()) {
      List<Editor> allTheseEditors = activeEditorEntries.getValue();
      if (allTheseEditors.size() < 2) {
        continue;
      }
      for (Editor editor : allTheseEditors) {
        if (thisEditor == editor) {
         // logger.warn("adjusting scroll for file: " + activeEditorEntries.getKey());
          syncJoinedTabScrolling(activeEditorEntries.getKey(), editor, allTheseEditors);
        }

      }
    }

    // prevent feedback loop
    thisEditor.getScrollingModel().addVisibleAreaListener(this);
  }

  private void syncJoinedTabScrolling(String filePathWeAreWorkingOn /*used for logging and debugging. hack hack hack.*/, Editor masterEditor, List<Editor> allTheseEditors) {
    int masterIndex = -1;
    // TODO later can make more efficient w/ a doubly linked list or something.
    for (int i = 0; i < allTheseEditors.size(); i++) {
      //StringUtils.left(editor.toString(), 10)
      /*
      Function<Editor, String> fcn = editor -> editor.toString().split("\\.")[editor.toString().split("\\.").length - 1] + " x: " + editor.getComponent().getVisibleRect().getX() + " y: " + editor.getComponent().getVisibleRect().getY();
      // TODO later i'd want to find a better way to get the editors left to right and top to bottom, such that i easily can ensure scrolling is sane. but for now seems like it works!
      logger.warn("All the Editors: " + Collections2.transform(allTheseEditors, fcn));
      List<Editor> editors = Arrays.asList(EditorFactory.getInstance().getAllEditors());
      logger.warn("Other way to get all: " + Collections2.transform(editors, fcn));
      */
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
  private void scroll(Editor masterEditor, List<Editor> allTheseEditors, int i, int directionCoefficient) {
    ScrollingModel masterScrollingModel = masterEditor.getScrollingModel();
    int masterVerticalScrollOffset = masterScrollingModel.getVerticalScrollOffset();

    Editor slave = allTheseEditors.get(i);
    ScrollingModel slaveScrollingModel = slave.getScrollingModel();
    int slaveVerticalScrollOffset = slaveScrollingModel.getVerticalScrollOffset();

    LogicalPosition masterPos = masterEditor.xyToLogicalPosition(
          new Point(masterScrollingModel.getVisibleArea().x, masterVerticalScrollOffset));
    int masterTopLine = masterPos.line - directionCoefficient; // move the top line so we have a bit of line overlap.

    LogicalPosition slavePos = slave.xyToLogicalPosition(
        new Point(slaveScrollingModel.getVisibleArea().x, slaveVerticalScrollOffset));
    int slaveTopLine = slavePos.line;
    int slaveBottomLine = slave.xyToLogicalPosition(
        new Point(slaveScrollingModel.getVisibleArea().x, slaveVerticalScrollOffset + slaveScrollingModel.getVisibleArea().height)).line;
    int slaveHeight = slaveBottomLine - slaveTopLine;
    // for slaves their top offset should = convertFromLines(master_line - 1 - size_of_slave_in_lines)
    int scrollToLine = Math.max(0, masterTopLine + directionCoefficient + directionCoefficient * slaveHeight);
    logger.info("masterTopLine=" + masterTopLine + " slaveHeight=" + slaveHeight + " slaveTopLine=" + slaveTopLine + " scrollToLine=" + scrollToLine);

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
}
