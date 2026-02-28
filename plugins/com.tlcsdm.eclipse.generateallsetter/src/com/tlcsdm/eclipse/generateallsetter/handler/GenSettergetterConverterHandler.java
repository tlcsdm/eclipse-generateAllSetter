package com.tlcsdm.eclipse.generateallsetter.handler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.handlers.HandlerUtil;

public class GenSettergetterConverterHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		HandlerHelper.EditorContext ctx = HandlerHelper.getEditorContext(HandlerUtil.getActiveEditor(event));
		if (ctx == null) {
			return null;
		}

		try {
			CompilationUnit cu = HandlerHelper.parseCompilationUnit(ctx.compilationUnit());

			MethodFinder finder = new MethodFinder(ctx.offset());
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
			if (returnType == null || returnType.isPrimitive()) {
				return null;
			}
			String typeName = returnType.getName();
			if (typeName == null || typeName.isEmpty()) {
				return null;
			}

			String varName = HandlerHelper.decapitalize(typeName);

			List<String> lines = new ArrayList<>();
			lines.add(typeName + " " + varName + " = new " + typeName + "();");
			for (IMethodBinding m : HandlerHelper.collectSetters(returnType)) {
				lines.add(varName + "." + m.getName() + "();");
			}
			lines.add("return " + varName + ";");

			if (method.getBody() == null) {
				return null;
			}

			IDocument doc = ctx.document();
			int bodyStart = method.getBody().getStartPosition() + 1;
			int bodyEnd = method.getBody().getStartPosition() + method.getBody().getLength() - 1;

			int insertOffset;
			if (ctx.offset() < bodyStart) {
				insertOffset = bodyStart;
			} else {
				int line = doc.getLineOfOffset(ctx.offset());
				int lineEnd = doc.getLineOffset(line) + doc.getLineLength(line);
				insertOffset = Math.min(lineEnd, bodyEnd);
			}

			String indent;
			try {
				indent = HandlerHelper.getIndentAtOffset(doc, insertOffset);
			} catch (BadLocationException e) {
				indent = "";
			}

			StringBuilder sb = new StringBuilder();
			for (String l : lines) {
				sb.append(indent).append(l).append(System.lineSeparator());
			}

			doc.replace(insertOffset, 0, System.lineSeparator() + sb.toString());

		} catch (BadLocationException e) {
			HandlerHelper.logError("Failed to insert converter code", e);
		} catch (Exception e) {
			HandlerHelper.logError("Unexpected error generating converter", e);
		}

		return null;
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
			return true;
		}

		MethodDeclaration getMethod() {
			return method;
		}
	}
}