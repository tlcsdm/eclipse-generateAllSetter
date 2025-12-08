package com.tlcsdm.eclipse.generateallsetter.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

public class ArgumentFiller {

	private final IDocument document;
	private final int offset;
	private final ICompilationUnit icu;
	private int replaceOffset;
	private int replaceLength;

	public ArgumentFiller(IDocument document, int offset, ICompilationUnit icu) {
		this.document = document;
		this.offset = offset;
		this.icu = icu;
	}

	public int getReplaceOffset() {
		return replaceOffset;
	}

	public int getReplaceLength() {
		return replaceLength;
	}

	/**
	 * Preview generated arguments without modifying the document
	 * 
	 * @return argument string or null if cannot resolve
	 */
	public String previewArguments() {
		try {
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(icu);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setResolveBindings(true);
			parser.setProject(icu.getJavaProject());

			CompilationUnit cu = (CompilationUnit) parser.createAST(null);

			MethodInvocationFinder finder = new MethodInvocationFinder(offset);
			cu.accept(finder);

			ASTNode node = finder.getTargetNode();
			if (node == null) {
				return null;
			}

			IMethodBinding binding = null;

			if (node instanceof MethodInvocation mi) {
				binding = mi.resolveMethodBinding();
				computeReplaceRange(mi.getName().getStartPosition() + mi.getName().getLength(), mi.arguments());

			} else if (node instanceof SuperMethodInvocation smi) {
				binding = smi.resolveMethodBinding();
				computeReplaceRange(smi.getName().getStartPosition() + smi.getName().getLength(), smi.arguments());

			} else if (node instanceof ClassInstanceCreation cic) {
				binding = cic.resolveConstructorBinding();
				computeReplaceRange(cic.getType().getStartPosition() + cic.getType().getLength(), cic.arguments());

			} else {
				return null;
			}

			if (binding == null) {
				return null;
			}

			List<String> args = new ArrayList<>();

			// Try to get parameter names from source code
			IMethod method = (IMethod) binding.getJavaElement();
			if (method != null && method.exists()) {
				try {
					args.addAll(Arrays.asList(method.getParameterNames()));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			// Fallback: generate placeholder names based on type
			if (args.isEmpty()) {
				int index = 1;
				for (ITypeBinding paramType : binding.getParameterTypes()) {
					String typeName = paramType.getName();
					if (typeName == null || typeName.isEmpty()) {
						typeName = "arg" + index;
						index++;
					} else {
						typeName = typeName.substring(0, 1).toLowerCase() + typeName.substring(1);
					}
					args.add(typeName);
				}
			}

			return String.join(", ", args);

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Compute the replacement range for method or constructor arguments
	 * 
	 * @param start    the start position after method/constructor name
	 * @param argsList the arguments list
	 */
	private void computeReplaceRange(int start, List<?> argsList) {
		replaceOffset = start + 1;
		if (argsList != null && !argsList.isEmpty()) {
			int lastArgEnd = 0;
			for (Object o : argsList) {
				if (o instanceof ASTNode node) {
					lastArgEnd = Math.max(lastArgEnd, node.getStartPosition() + node.getLength());
				}
			}
			if (lastArgEnd == 0) {
				lastArgEnd = start;
			}
			replaceLength = lastArgEnd - replaceOffset;
		} else {
			replaceLength = 0;
		}
	}

	/**
	 * Fill arguments directly into document
	 */
	public void fillArguments() {
		try {
			String replacement = previewArguments();
			if (replacement != null) {
				document.replace(replaceOffset, replaceLength, replacement);
			}
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ASTVisitor to find the target node at cursor
	 */
	static class MethodInvocationFinder extends ASTVisitor {

		private final int offset;
		private ASTNode target;

		MethodInvocationFinder(int offset) {
			this.offset = offset;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			if (inRange(node)) {
				target = node;
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(SuperMethodInvocation node) {
			if (inRange(node)) {
				target = node;
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(ClassInstanceCreation node) {
			if (inRange(node)) {
				target = node;
			}
			return super.visit(node);
		}

		private boolean inRange(ASTNode node) {
			int start = node.getStartPosition();
			int end = start + node.getLength();

			if (node instanceof MethodInvocation mi) {
				// Cover expression + method call range
				if (mi.getExpression() != null) {
					start = mi.getName().getStartPosition();
					end = mi.getStartPosition() + mi.getLength();
				}
			} else if (node instanceof SuperMethodInvocation smi) {
				start = smi.getName().getStartPosition();
				end = smi.getStartPosition() + smi.getLength();
			} else if (node instanceof ClassInstanceCreation cic) {
				start = cic.getStartPosition();
				end = cic.getStartPosition() + cic.getLength();
			}

			return offset >= start && offset <= end;
		}

		ASTNode getTargetNode() {
			return target;
		}
	}
}
