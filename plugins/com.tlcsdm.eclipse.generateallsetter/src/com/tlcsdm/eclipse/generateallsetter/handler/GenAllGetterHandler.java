package com.tlcsdm.eclipse.generateallsetter.handler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ui.handlers.HandlerUtil;

public class GenAllGetterHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		HandlerHelper.EditorContext ctx = HandlerHelper.getEditorContext(HandlerUtil.getActiveEditor(event));
		if (ctx == null) {
			return null;
		}

		try {
			CompilationUnit cu = HandlerHelper.parseCompilationUnit(ctx.compilationUnit());

			HandlerHelper.VariableFinder finder = new HandlerHelper.VariableFinder(ctx.offset());
			cu.accept(finder);
			VariableDeclarationFragment fragment = finder.getFragment();

			ITypeBinding type = HandlerHelper.resolveType(fragment);
			if (type == null) {
				return null;
			}

			String varName = fragment.getName().getIdentifier();

			List<String> lines = new ArrayList<>();
			for (IMethodBinding m : HandlerHelper.collectGetters(type)) {
				String name = m.getName();
				String propName;
				if (name.startsWith("get")) {
					propName = name.substring(3);
				} else {
					propName = name.substring(2);
				}
				if (propName.isEmpty()) {
					continue;
				}
				propName = propName.substring(0, 1).toLowerCase() + propName.substring(1);

				ITypeBinding ret = m.getReturnType();
				String typeName = (ret == null) ? "Object" : ret.getName();
				lines.add(typeName + " " + propName + " = " + varName + "." + name + "();");
			}

			if (lines.isEmpty()) {
				return null;
			}

			HandlerHelper.insertLinesAfterOffset(ctx.document(), ctx.offset(), lines);

		} catch (BadLocationException e) {
			HandlerHelper.logError("Failed to insert getter code", e);
		} catch (Exception e) {
			HandlerHelper.logError("Unexpected error generating getters", e);
		}

		return null;
	}
}