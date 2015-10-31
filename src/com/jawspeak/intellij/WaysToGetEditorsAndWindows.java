package com.jawspeak.intellij;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created by jaw on 10/25/15.
 *
 *
 *  Have something like this in plugin.xml to add a command to invoke it.

 <action id="com.jawspeak.intellij.WaysToGetEditorsAndWindows"
 class="com.jawspeak.intellij.WaysToGetEditorsAndWindows"
 text="Test WaysToGetEditorsAndWindows"
 description="test scrollBetween ... etc">
   <add-to-group group-id="EditorTabPopupMenu"/> <!-- right clink on the tab to run it. -->
 </action>
 *
 */
public class WaysToGetEditorsAndWindows extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + WaysToGetEditorsAndWindows.class.getName());

  @Override
  public void actionPerformed(AnActionEvent event) {
    // Lots of different ways to grab editor windows.
    // 1. which is best to give me each tabs?
    // 2. copy to new file. later put this as a gist.
    // 3. find all tabs that are linked. find scroll posn. set scroll position.
    // 4. trigger continuously on any scroll / state change. not just when ran manually.


    LOG.warn("action invoked"); // using warn so that it shows up w/o configuration in plugin log.

    Project project = event.getData(PlatformDataKeys.PROJECT);
    VirtualFile[] data = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    LOG.warn("* virtual files, all the selected files we are working with");
    for (VirtualFile virtualFile : data) {
      LOG.warn(virtualFile.getCanonicalPath());
    }

    LOG.warn("* open editor files (will only show 2 if we have 3 'tabs' but only 2 files.");
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
      LOG.warn(virtualFile.getCanonicalPath());
    };

    // LOG.warn("* current editor window");
    EditorWindow editorWindow = event.getData(EditorWindow.DATA_KEY);
    // if you want to switch the orientation.
    //editorWindow.changeOrientation();
    //editorWindow.getManager().getMainSplitters();


    LOG.warn("* open windows by all editors (same as above, only 2 if 3 'tabs' but 2 files");
    for (VirtualFile file : editorWindow.getOwner().getOpenFiles()) {
      LOG.warn(file.getCanonicalPath());
    }

    LOG.warn("* all open windows, but show tabs (Splitters) separately");
    EditorWithProviderComposite[] editorsComposites = editorWindow.getOwner().getEditorsComposites();
    for (EditorWithProviderComposite editorsComposite : editorsComposites) {
      LOG.warn(editorsComposite.getFile().getCanonicalPath());
    }

    LOG.warn("* all editor windows");
    EditorWindow[] allEditorWindows = editorWindow.getOwner().getWindows();
    for (EditorWindow window : allEditorWindows) {
      for (VirtualFile virtualFile : window.getFiles()) {
        LOG.warn("   inner file " + virtualFile.getCanonicalPath());
      }
    }

    for (EditorWindow window : allEditorWindows) {
      EditorWithProviderComposite[] innerEditors = window.getEditors();
      for (EditorWithProviderComposite innerEditor : innerEditors) {
        // this is the way to recursively get all if nested
        LOG.warn("   inner editor in window, get file editor: " + innerEditor.getFile().getCanonicalPath() +
        " size of editors " + innerEditor.getEditors().length);
      }
    }

    LOG.warn("* all editors (includes Text and Data names, whatever that is)"); // not very useful.
    for (FileEditor editor : FileEditorManager.getInstance(project).getAllEditors()) {
      LOG.warn(editor.getName());
    }

    LOG.warn("* another way to get all editor windows"); // not very useful.
    for (FileEditor editor : FileEditorManagerEx.getInstance(project).getAllEditors()) {
      LOG.warn(editor.getName());
    }

    LOG.warn("* end");

    final VirtualFile file = event.getData(PlatformDataKeys.VIRTUAL_FILE);
    Editor editor = event.getData(PlatformDataKeys.EDITOR);
    Integer line = (editor != null)
        // convert the VisualPosition to the LogicalPosition to have the correct line number.
        // http://grepcode.com/file/repository.grepcode.com/java/ext/com.jetbrains/intellij-idea/10.0/com/intellij/openapi/editor/LogicalPosition.java#LogicalPosition
        ? editor.visualToLogicalPosition(editor.getSelectionModel().getSelectionStartPosition()).line + 1 : null;

    LOG.warn("line number: " + line);
  }
}
