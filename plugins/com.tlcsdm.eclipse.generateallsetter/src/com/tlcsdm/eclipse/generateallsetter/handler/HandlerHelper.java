package com.tlcsdm.eclipse.generateallsetter.handler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.tlcsdm.eclipse.generateallsetter.Activator;

/**
 * Shared utility methods for code generation handlers.
 */
public final class HandlerHelper {

	private HandlerHelper() {
	}

	/**
	 * Holds the editor context needed by all handlers.
	 */
	public record EditorContext(ITextEditor textEditor, IEditorInput input, ICompilationUnit compilationUnit,
			IDocument document, int offset) {
	}

	/**
	 * Extracts the editor context from the active editor. Returns {@code null} if
	 * the editor is not a Java text editor.
	 */
	public static EditorContext getEditorContext(IEditorPart editor) {
		if (!(editor instanceof ITextEditor textEditor)) {
			return null;
		}
		IEditorInput input = textEditor.getEditorInput();
		ICompilationUnit icu = JavaUI.getWorkingCopyManager().getWorkingCopy(input);
		if (icu == null) {
			return null;
		}
		IDocument doc = textEditor.getDocumentProvider().getDocument(input);
		ITextSelection selection = (ITextSelection) textEditor.getSelectionProvider().getSelection();
		int offset = selection.getOffset();
		return new EditorContext(textEditor, input, icu, doc, offset);
	}

	/**
	 * Creates and configures an AST parser with binding resolution enabled.
	 */
	public static CompilationUnit parseCompilationUnit(ICompilationUnit icu) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(icu);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setProject(icu.getJavaProject());
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Resolves the type binding for a variable declaration fragment. Returns
	 * {@code null} if the binding cannot be resolved.
	 */
	public static ITypeBinding resolveType(VariableDeclarationFragment fragment) {
		if (fragment == null) {
			return null;
		}
		try {
			if (fragment.resolveBinding() != null) {
				return fragment.resolveBinding().getType();
			}
		} catch (Exception e) {
			logWarning("Failed to resolve type binding", e);
		}
		return null;
	}

	/**
	 * Collects all setter methods from the given type and its superclasses. A
	 * setter is a method whose name starts with "set", has more than 3 characters,
	 * and takes exactly one parameter.
	 */
	public static List<IMethodBinding> collectSetters(ITypeBinding type) {
		List<IMethodBinding> setters = new ArrayList<>();
		ITypeBinding current = type;
		while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
			for (IMethodBinding m : current.getDeclaredMethods()) {
				if (m == null) {
					continue;
				}
				if (m.getParameterTypes() != null && m.getParameterTypes().length == 1) {
					String name = m.getName();
					if (name.startsWith("set") && name.length() > 3) {
						setters.add(m);
					}
				}
			}
			current = current.getSuperclass();
		}
		return setters;
	}

	/**
	 * Collects all getter methods from the given type and its superclasses. A
	 * getter is a no-arg method whose name starts with "get" (length > 3) or "is"
	 * (length > 2).
	 */
	public static List<IMethodBinding> collectGetters(ITypeBinding type) {
		List<IMethodBinding> getters = new ArrayList<>();
		ITypeBinding current = type;
		while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
			for (IMethodBinding m : current.getDeclaredMethods()) {
				if (m == null) {
					continue;
				}
				if (m.getParameterTypes() != null && m.getParameterTypes().length == 0) {
					String name = m.getName();
					if ((name.startsWith("get") && name.length() > 3)
							|| (name.startsWith("is") && name.length() > 2)) {
						getters.add(m);
					}
				}
			}
			current = current.getSuperclass();
		}
		return getters;
	}

	/**
	 * Extracts the leading whitespace indentation from the line at the given
	 * offset.
	 */
	public static String getIndentAtOffset(IDocument doc, int offset) throws BadLocationException {
		int line = doc.getLineOfOffset(offset);
		int lineOffset = doc.getLineOffset(line);
		int lineLength = doc.getLineLength(line);
		String lineText = doc.get(lineOffset, lineLength);
		StringBuilder indent = new StringBuilder();
		for (int i = 0; i < lineText.length(); i++) {
			char c = lineText.charAt(i);
			if (c == ' ' || c == '\t') {
				indent.append(c);
			} else {
				break;
			}
		}
		return indent.toString();
	}

	/**
	 * Inserts the given lines after the current line at the specified offset,
	 * preserving indentation.
	 */
	public static void insertLinesAfterOffset(IDocument doc, int offset, List<String> lines)
			throws BadLocationException {
		int line = doc.getLineOfOffset(offset);
		int lineOffset = doc.getLineOffset(line);
		int lineLength = doc.getLineLength(line);

		String indent;
		try {
			indent = getIndentAtOffset(doc, offset);
		} catch (BadLocationException e) {
			indent = "";
		}

		StringBuilder indented = new StringBuilder();
		for (String l : lines) {
			indented.append(indent).append(l).append(System.lineSeparator());
		}

		int lineEnd = lineOffset + lineLength;
		doc.replace(lineEnd, 0, System.lineSeparator() + indented.toString());
	}

	/**
	 * Returns a sensible default value literal for the given type binding.
	 */
	public static String defaultValueFor(ITypeBinding param) {
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
		}
		String q = param.getQualifiedName();
		if ("java.lang.String".equals(q) || "String".equals(param.getName())) {
			return "\"\"";
		}
		return "null";
	}

	/**
	 * Decapitalizes the first character of a string. Returns "obj" for null or
	 * empty input.
	 */
	public static String decapitalize(String s) {
		if (s == null || s.isEmpty()) {
			return "obj";
		}
		return s.substring(0, 1).toLowerCase() + s.substring(1);
	}

	/**
	 * Logs an error message to the Eclipse error log.
	 */
	public static void logError(String message, Throwable t) {
		Activator activator = Activator.getDefault();
		if (activator != null) {
			activator.getLog().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, message, t));
		}
	}

	/**
	 * Logs a warning message to the Eclipse error log.
	 */
	public static void logWarning(String message, Throwable t) {
		Activator activator = Activator.getDefault();
		if (activator != null) {
			activator.getLog().log(new Status(IStatus.WARNING, Activator.PLUGIN_ID, message, t));
		}
	}

	/**
	 * AST visitor that finds a {@link VariableDeclarationFragment} at a given
	 * offset. Checks both the fragment and the enclosing
	 * {@link VariableDeclarationStatement}, with a fallback to the first fragment
	 * when the offset is within the statement but not on a specific fragment name.
	 */
	public static class VariableFinder extends ASTVisitor {
		private final int offset;
		private VariableDeclarationFragment fragment;

		public VariableFinder(int offset) {
			this.offset = offset;
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			int start = node.getStartPosition();
			int end = start + node.getLength();
			if (offset >= start && offset <= end) {
				this.fragment = node;
			}
			return true;
		}

		@Override
		public boolean visit(VariableDeclarationStatement node) {
			int start = node.getStartPosition();
			int end = start + node.getLength();
			if (offset >= start && offset <= end) {
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
				if (this.fragment == null && !node.fragments().isEmpty()
						&& node.fragments().get(0) instanceof VariableDeclarationFragment f) {
					this.fragment = f;
				}
			}
			return true;
		}

		public VariableDeclarationFragment getFragment() {
			return fragment;
		}
	}
}
