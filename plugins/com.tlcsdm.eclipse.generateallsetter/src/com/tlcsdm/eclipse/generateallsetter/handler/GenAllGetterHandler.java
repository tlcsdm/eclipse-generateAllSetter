package com.tlcsdm.eclipse.generateallsetter.handler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
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
		ICompilationUnit icu = JavaUI.getWorkingCopyManager().getWorkingCopy(input);
		if (icu == null) {
			return null;
		}

		IDocument doc = textEditor.getDocumentProvider().getDocument(input);
		ITextSelection selection = (ITextSelection) textEditor.getSelectionProvider().getSelection();
		int offset = selection.getOffset();

		try {
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(icu);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setResolveBindings(true);
			parser.setProject(icu.getJavaProject());

			CompilationUnit cu = (CompilationUnit) parser.createAST(null);

			// find the variable declaration fragment at offset
			VariableFinder finder = new VariableFinder(offset);
			cu.accept(finder);
			VariableDeclarationFragment fragment = finder.getFragment();
			if (fragment == null) {
				return null;
			}

			ITypeBinding type = null;
			try {
				if (fragment.resolveBinding() != null) {
					type = fragment.resolveBinding().getType();
				}
			} catch (Exception e) {
				// ignore
			}
			if (type == null) {
				return null;
			}

			String varName = fragment.getName().getIdentifier();

			List<String> lines = new ArrayList<>();
			for (IMethodBinding m : type.getDeclaredMethods()) {
				if (m == null)
					continue;
				if (m.getParameterTypes() != null && m.getParameterTypes().length == 0) {
					String name = m.getName();
					if (name.startsWith("get") && name.length() > 3 || name.startsWith("is") && name.length() > 2) {
						String propName;
						if (name.startsWith("get")) {
							propName = name.substring(3);
						} else {
							propName = name.substring(2);
						}
						if (propName.length() == 0) {
							continue;
						}
						propName = propName.substring(0, 1).toLowerCase() + propName.substring(1);

						ITypeBinding ret = m.getReturnType();
						String typeName = (ret == null) ? "Object" : ret.getName();
						lines.add(typeName + " " + propName + " = " + varName + "." + name + "();");
					}
				}
			}

			if (lines.isEmpty()) {
				return null;
			}

			StringBuilder sb = new StringBuilder();
			for (String l : lines) {
				sb.append(l).append(System.lineSeparator());
			}

			// insert at caret
			doc.replace(offset, 0, sb.toString());

		} catch (BadLocationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	static class VariableFinder extends ASTVisitor {
		private final int offset;
		private VariableDeclarationFragment fragment;

		VariableFinder(int offset) {
			this.offset = offset;
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
//			int start = node.getStartPosition();
//			int end = start + node.getLength();
			if (offset >= node.getName().getStartPosition()
					&& offset <= node.getName().getStartPosition() + node.getName().getLength()) {
				this.fragment = node;
			}
			return super.visit(node);
		}

		VariableDeclarationFragment getFragment() {
			return fragment;
		}
	}
}