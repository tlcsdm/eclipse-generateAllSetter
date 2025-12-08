package com.tlcsdm.eclipse.generateallsetter.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class GenAllGetterHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (!(editor instanceof ITextEditor)) {
			return null;
		}

		ITextEditor textEditor = (ITextEditor) editor;
		IEditorInput input = textEditor.getEditorInput();
//		ICompilationUnit icu = JavaUI.getWorkingCopyManager().getWorkingCopy(input);
//		if (icu == null) {
//			return null;
//		}
//
//		IDocument doc = textEditor.getDocumentProvider().getDocument(input);
//		ITextSelection selection = (ITextSelection) textEditor.getSelectionProvider().getSelection();
//		int offset = selection.getOffset();
//
//		ArgumentFiller filler = new ArgumentFiller(doc, offset, icu);
//		filler.fillArguments();

		return null;
	}
}