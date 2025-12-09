package com.tlcsdm.eclipse.generateallsetter.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

import java.util.ArrayList;
import java.util.List;

public class GenAllSetterWithDefaultHandler extends AbstractHandler {

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
				if (m == null) {
					continue;
				}
				if (m.getParameterTypes() != null && m.getParameterTypes().length == 1) {
					String name = m.getName();
					if (name.startsWith("set") && name.length() > 3) {
						ITypeBinding param = m.getParameterTypes()[0];
						String defaultVal = defaultValueFor(param);
						lines.add(varName + "." + name + "(" + defaultVal + ");");
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

			// insert at next line of current caret line with preserved indentation
			int line = doc.getLineOfOffset(offset);
			int lineOffset = doc.getLineOffset(line);
			int lineLength = doc.getLineLength(line);
			String indent = "";
			try {
				String lineText = doc.get(lineOffset, lineLength);
				for (int i = 0; i < lineText.length(); i++) {
					char c = lineText.charAt(i);
					if (c == ' ' || c == '\t') {
						indent += c;
					} else {
						break;
					}
				}
			} catch (BadLocationException e) {
				// ignore
			}

			StringBuilder indented = new StringBuilder();
			for (String l : lines) {
				indented.append(indent).append(l).append(System.lineSeparator());
			}

			int lineEnd = lineOffset + lineLength;
			doc.replace(lineEnd, 0, System.lineSeparator() + indented.toString());

		} catch (BadLocationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private static String defaultValueFor(ITypeBinding param) {
		if (param == null) {
			return "null";
		}
		if (param.isPrimitive()) {
			String n = param.getName();
			switch (n) {
			case "boolean":
				return "false";
			case "char":
				return "'\\u0000'";
			case "float":
				return "0.0f";
			case "double":
				return "0.0d";
			default:
				return "0";
			}
		} else {
			String q = param.getQualifiedName();
			if ("java.lang.String".equals(q) || "String".equals(param.getName())) {
				return "\"\"";
			}
			// default for other objects
			return "null";
		}
	}

	static class VariableFinder extends ASTVisitor {
		private final int offset;
		private VariableDeclarationFragment fragment;

		VariableFinder(int offset) {
			this.offset = offset;
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			int start = node.getStartPosition();
			int end = start + node.getLength();
			if (offset >= start && offset <= end) {
				this.fragment = node;
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(VariableDeclarationStatement node) {
			int start = node.getStartPosition();
			int end = start + node.getLength();
			if (offset >= start && offset <= end) {
				// try to find fragment whose name covers offset
				for (Object o : node.fragments()) {
					if (o instanceof VariableDeclarationFragment f) {
						int fs = f.getStartPosition();
						int fe = fs + f.getLength();
						if (offset >= fs && offset <= fe) {
							this.fragment = f;
							break;
						}
					}
				}
				if (this.fragment == null) {
					// fallback to first fragment
					if (!node.fragments().isEmpty() && node.fragments().get(0) instanceof VariableDeclarationFragment f) {
						this.fragment = f;
					}
				}
			}
			return super.visit(node);
		}

		VariableDeclarationFragment getFragment() {
			return fragment;
		}
	}
}