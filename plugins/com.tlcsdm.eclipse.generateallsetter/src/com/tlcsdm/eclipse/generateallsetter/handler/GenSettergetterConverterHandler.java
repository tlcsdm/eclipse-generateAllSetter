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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class GenSettergetterConverterHandler extends AbstractHandler {

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

			MethodFinder finder = new MethodFinder(offset);
			cu.accept(finder);
			MethodDeclaration method = finder.getMethod();
			if (method == null) {
				return null;
			}
			IMethodBinding mb = method.resolveBinding();
			if (mb == null) {
				return null;
			}
			ITypeBinding returnType = mb.getReturnType();
			if (returnType == null) {
				return null;
			}
			if (returnType.isPrimitive()) {
				return null;
			}
			String typeName = returnType.getName();
			if (typeName == null || typeName.isEmpty()) {
				return null;
			}

			String varName = decapitalize(typeName);

			List<String> lines = new ArrayList<>();
			lines.add(typeName + " " + varName + " = new " + typeName + "();");
			for (IMethodBinding m : returnType.getDeclaredMethods()) {
				if (m == null) {
					continue;
				}
				if (m.getParameterTypes() != null && m.getParameterTypes().length == 1) {
					String name = m.getName();
					if (name.startsWith("set") && name.length() > 3) {
						lines.add(varName + "." + name + "();");
					}
				}
			}
			lines.add("return " + varName + ";");

			if (lines.isEmpty()) {
				return null;
			}

			// insert near caret inside method body: use next line of caret but ensure within body
			if (method.getBody() == null) {
				return null;
			}
			int bodyStart = method.getBody().getStartPosition() + 1;
			int bodyEnd = method.getBody().getStartPosition() + method.getBody().getLength() - 1;

			int insertOffset;
			try {
				if (offset < bodyStart) {
					insertOffset = bodyStart;
				} else {
					int line = doc.getLineOfOffset(offset);
					int lineEnd = doc.getLineOffset(line) + doc.getLineLength(line);
					insertOffset = lineEnd;
					if (insertOffset > bodyEnd) {
						insertOffset = bodyEnd;
					}
				}

				// compute indentation at insertion line
				int insertLine = doc.getLineOfOffset(Math.max(0, insertOffset));
				int insertLineOffset = doc.getLineOffset(insertLine);
				int insertLineLength = doc.getLineLength(insertLine);
				String indent = "";
				try {
					String lineText = doc.get(insertLineOffset, insertLineLength);
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

				StringBuilder sb = new StringBuilder();
				for (String l : lines) {
					sb.append(indent).append(l).append(System.lineSeparator());
				}

				// ensure we add a newline before inserted block
				doc.replace(insertOffset, 0, System.lineSeparator() + sb.toString());
			} catch (BadLocationException e) {
				e.printStackTrace();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private static String decapitalize(String s) {
		if (s == null || s.isEmpty()) {
			return "obj";
		}
		return s.substring(0, 1).toLowerCase() + s.substring(1);
	}

	static class MethodFinder extends ASTVisitor {
		private final int offset;
		private MethodDeclaration method;

		MethodFinder(int offset) {
			this.offset = offset;
		}

		@Override
		public boolean visit(MethodDeclaration node) {
			int start = node.getStartPosition();
			int end = start + node.getLength();
			if (offset >= start && offset <= end) {
				this.method = node;
			}
			return super.visit(node);
		}

		MethodDeclaration getMethod() {
			return method;
		}
	}
}