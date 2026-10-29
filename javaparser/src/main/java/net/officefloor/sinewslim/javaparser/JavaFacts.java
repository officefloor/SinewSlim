package net.officefloor.sinewslim.javaparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.YieldStmt;

import net.officefloor.sinewslim.core.MethodFacts;
import net.officefloor.sinewslim.core.Stmt;
import net.officefloor.sinewslim.core.StmtKind;
import net.officefloor.sinewslim.core.Var;

/**
 * The facts of every method in a Java source file, from JavaParser's syntax tree. Taken over from
 * the study's {@code LiveWidth.java} (schema 3), keeping what the search reads: the variables'
 * reference, definition and write lines, the literal rule, the loops and the statement tree. The
 * length is the study measurer's nloc. No symbol solver: names are matched to declarations by
 * lexical scope alone.
 *
 * <ul>
 * <li><b>Variables:</b> parameters of the callable; locals, for and for-each variables,
 * resources, catch and lambda parameters and pattern variables; and fields: a {@code this.x}, or a
 * simple name no local declaration in scope matches. A simple name starting with an upper-case
 * letter is taken as a type or a constant and is not a variable; nor is an enum constant as a
 * {@code case} label.</li>
 * <li><b>The literal rule:</b> a local declared with no initializer, or with a literal one, depends
 * on nothing above it, so its declaration could move down to its first use without changing the
 * method. Its declaration is not a use, though a literal initializer is still a definition.</li>
 * <li><b>Own calls:</b> where the study read the symbol solver's resolution, this reads syntax: a
 * call with no receiver, or {@code this} as its receiver, to a name declared as an instance method
 * in the enclosing type or a type around it, and an explicit {@code this(...)}. A call to a static
 * method of the class does not bind a piece to it, so it is not counted (the study's reading
 * counted it, an upper bound).</li>
 * </ul>
 */
public final class JavaFacts {

	/** The levels a file is parsed at, in order: the newest the parser knows, then Java 8 for old code. */
	static final LanguageLevel[] LEVELS = { LanguageLevel.JAVA_25, LanguageLevel.JAVA_8 };

	static final Pattern UPPER = Pattern.compile("^[A-Z].*");

	private JavaFacts() {
	}

	/** A variable as it is gathered, before it becomes the core's. */
	static final class VarB {
		final Var.Kind kind;
		final String name;
		/** The node the declaration is visible in (null for a field). */
		final Node scope;
		/** Where the declaration is, for the declared-before rule (0 for a parameter or field). */
		final int declLine, declCol;
		final TreeSet<Integer> refLines = new TreeSet<>(), defLines = new TreeSet<>(), mutLines = new TreeSet<>();
		int first = Integer.MAX_VALUE, last = -1;
		boolean literal;

		VarB(Var.Kind kind, String name, Node scope, Node at) {
			this.kind = kind;
			this.name = name;
			this.scope = scope;
			this.declLine = at == null ? 0 : line(at);
			this.declCol = at == null ? 0 : col(at);
		}

		void ref(Node n) {
			int line = line(n);
			refLines.add(line);
			first = Math.min(first, line);
			last = Math.max(last, line);
		}

		/** A literal initializer: the declaration is in the range, not a use. */
		void declare(int line) {
			literal = true;
			first = Math.min(first, line);
			last = Math.max(last, line);
		}

		Var build() {
			return new Var(name, kind, first, literal, ints(refLines), ints(defLines), ints(mutLines));
		}
	}

	/** Parse a file's source and gather the facts of each of its methods. */
	public static ParsedFile parse(String path, String source) {
		CompilationUnit cu = null;
		String problem = null;
		for (LanguageLevel level : LEVELS) {
			JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(level));
			try {
				ParseResult<CompilationUnit> pr = parser.parse(source);
				if (pr.isSuccessful() && pr.getResult().isPresent()) {
					cu = pr.getResult().get();
					break;
				}
				if (problem == null) {
					problem = pr.getProblems().isEmpty() ? "no result" : pr.getProblems().get(0).getVerboseMessage();
				}
			} catch (RuntimeException | StackOverflowError e) {
				if (problem == null) {
					problem = e.getClass().getSimpleName() + ": " + e.getMessage();
				}
			}
		}
		if (cu == null) {
			return new ParsedFile(path, problem, List.of());
		}
		List<MethodUnit> methods = new ArrayList<>();
		for (Node n : cu.findAll(Node.class,
				x -> x instanceof CallableDeclaration || x instanceof CompactConstructorDeclaration)) {
			if (n instanceof MethodDeclaration md && md.getBody().isEmpty()) {
				continue; // no body, not a unit
			}
			methods.add(measure(n));
		}
		return new ParsedFile(path, null, methods);
	}

	static int line(Node n) {
		return n.getBegin().map(p -> p.line).orElse(0);
	}

	static int col(Node n) {
		return n.getBegin().map(p -> p.column).orElse(0);
	}

	static int endLine(Node n) {
		return n.getEnd().map(p -> p.line).orElse(line(n));
	}

	static boolean within(Node ancestor, Node n) {
		return ancestor == n || ancestor.isAncestorOf(n);
	}

	static int[] ints(Iterable<Integer> xs) {
		List<Integer> l = new ArrayList<>();
		xs.forEach(l::add);
		return l.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Whether a child node belongs to a nested type's own members rather than to the enclosing body. */
	static boolean isNestedTypePart(Node parent, Node child) {
		if (child instanceof TypeDeclaration) {
			return true; // a local class, record or enum
		}
		return parent instanceof ObjectCreationExpr && child instanceof BodyDeclaration;
	}

	/** Every node of a callable's own body, depth first (nested type members excluded). */
	static void ownNodes(Node n, List<Node> out) {
		out.add(n);
		for (Node c : n.getChildNodes()) {
			if (!isNestedTypePart(n, c)) {
				ownNodes(c, out);
			}
		}
	}

	static MethodUnit measure(Node decl) {
		SimpleName name;
		NodeList<Parameter> params;
		Node body;
		if (decl instanceof MethodDeclaration md) {
			name = md.getName();
			params = md.getParameters();
			body = md.getBody().get();
		} else if (decl instanceof ConstructorDeclaration cd) {
			name = cd.getName();
			params = cd.getParameters();
			body = cd.getBody();
		} else {
			CompactConstructorDeclaration ccd = (CompactConstructorDeclaration) decl;
			name = ccd.getName();
			params = new NodeList<>(); // the record components read as fields
			body = ccd.getBody();
		}
		int declLine = line(decl);
		int nameLine = name.getBegin().map(p -> p.line).orElse(declLine);
		String signature = name.asString() + "("
				+ params.stream().map(p -> p.getType().asString() + (p.isVarArgs() ? "..." : ""))
						.collect(Collectors.joining(", "))
				+ ")";

		List<Node> own = new ArrayList<>();
		ownNodes(body, own);

		// declarations, each with the node it is visible in
		List<VarB> locals = new ArrayList<>();
		for (Parameter p : params) {
			locals.add(new VarB(Var.Kind.PARAM, p.getNameAsString(), decl, null));
		}
		for (Node n : own) {
			if (n instanceof VariableDeclarator vd && vd.getParentNode().orElse(null) instanceof VariableDeclarationExpr vde) {
				Node holder = vde.getParentNode().orElse(vde);
				Node scope = holder instanceof ForStmt || holder instanceof ForEachStmt || holder instanceof TryStmt ? holder
						: holder.getParentNode().orElse(holder);
				boolean bound = vd.getInitializer().isPresent() || holder instanceof ForEachStmt;
				VarB v = new VarB(Var.Kind.LOCAL, vd.getNameAsString(), scope, vd.getName());
				if (bound) {
					v.defLines.add(line(vd.getName()));
				}
				if (holder instanceof ForEachStmt || (bound && !literal(vd.getInitializer().get()))) {
					v.ref(vd.getName());
				} else if (bound) {
					v.declare(line(vd.getName())); // a literal: in the range, not a use
				} else {
					v.literal = true; // no value: not a reference at all, so the range starts at the first use
				}
				locals.add(v);
			} else if (n instanceof Parameter p) {
				Node owner = p.getParentNode().orElse(null);
				if (owner instanceof CatchClause || owner instanceof LambdaExpr) {
					VarB v = new VarB(Var.Kind.LOCAL, p.getNameAsString(), owner, p.getName());
					v.defLines.add(line(p.getName()));
					locals.add(v);
				}
			} else if (n instanceof TypePatternExpr tp) {
				VarB v = new VarB(Var.Kind.LOCAL, tp.getNameAsString(), patternScope(tp), tp.getName());
				v.defLines.add(line(tp.getName()));
				v.ref(tp.getName());
				locals.add(v);
			}
		}

		// references, matched to the innermost declaration in scope
		Map<String, VarB> fields = new LinkedHashMap<>();
		for (Node n : own) {
			if (n instanceof NameExpr ne) {
				if (excluded(ne)) {
					continue;
				}
				VarB v = resolve(ne, locals);
				if (v == null) {
					v = fields.computeIfAbsent(ne.getNameAsString(), k -> new VarB(Var.Kind.FIELD, k, null, null));
				}
				v.ref(ne);
			} else if (n instanceof FieldAccessExpr fa && fa.getScope() instanceof ThisExpr) {
				fields.computeIfAbsent(fa.getNameAsString(), k -> new VarB(Var.Kind.FIELD, k, null, null)).ref(fa);
			}
		}

		// definitions, and the writes into a variable's object
		for (Node n : own) {
			Expression target = null;
			if (n instanceof AssignExpr ae) {
				target = ae.getTarget();
			} else if (n instanceof UnaryExpr ue && (ue.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT
					|| ue.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT
					|| ue.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
					|| ue.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT)) {
				target = ue.getExpression();
			}
			if (target == null) {
				continue;
			}
			VarB rebound = varOf(target, locals, fields);
			if (rebound != null) {
				rebound.defLines.add(line(n));
				continue;
			}
			Expression base = target;
			while (base instanceof ArrayAccessExpr || base instanceof FieldAccessExpr) {
				base = base instanceof ArrayAccessExpr aa ? aa.getName() : ((FieldAccessExpr) base).getScope();
				VarB into = varOf(base, locals, fields);
				if (into != null) {
					into.mutLines.add(line(n));
					break;
				}
			}
		}

		List<Var> vars = new ArrayList<>();
		for (VarB v : locals) {
			if (v.last >= 0) { // referenced, or declared with a literal
				vars.add(v.build());
			}
		}
		for (VarB v : fields.values()) {
			vars.add(v.build());
		}

		List<int[]> loops = new ArrayList<>();
		for (Node n : own) {
			if (n instanceof ForStmt || n instanceof ForEachStmt || n instanceof WhileStmt || n instanceof DoStmt) {
				loops.add(new int[] { line(n), endLine(n) });
			}
		}

		MethodFacts facts = new MethodFacts(declLine, tree(body), vars, loops, ownCallLines(decl, own),
				nloc(decl, name));
		return new MethodUnit(typeName(decl), name.asString(), signature, declLine, nameLine, endLine(decl), facts);
	}

	// ------------------------------------------------------------------------------------------
	// the statement tree
	// ------------------------------------------------------------------------------------------

	/** The statement tree under a node: its statements and switch entries, nested types left out. */
	static Stmt tree(Node n) {
		List<Stmt> kids = new ArrayList<>();
		kids(n, kids);
		return new Stmt(line(n), endLine(n), n instanceof BlockStmt || n instanceof SwitchEntry, kind(n), kids);
	}

	static void kids(Node n, List<Stmt> out) {
		for (Node c : n.getChildNodes()) {
			if (isNestedTypePart(n, c)) {
				continue;
			}
			if (c instanceof Statement || c instanceof SwitchEntry) {
				out.add(tree(c));
			} else {
				kids(c, out);
			}
		}
	}

	static StmtKind kind(Node n) {
		if (n instanceof ReturnStmt) {
			return StmtKind.RETURN;
		} else if (n instanceof BreakStmt) {
			return StmtKind.BREAK;
		} else if (n instanceof ContinueStmt) {
			return StmtKind.CONTINUE;
		} else if (n instanceof ThrowStmt) {
			return StmtKind.THROW;
		} else if (n instanceof YieldStmt) {
			return StmtKind.YIELD;
		} else if (n instanceof IfStmt) {
			return StmtKind.IF;
		} else if (n instanceof ForStmt || n instanceof ForEachStmt || n instanceof WhileStmt || n instanceof DoStmt) {
			return StmtKind.LOOP;
		} else if (n instanceof TryStmt) {
			return StmtKind.TRY;
		} else if (n instanceof SwitchStmt) {
			return StmtKind.SWITCH;
		} else if (n instanceof SynchronizedStmt) {
			return StmtKind.SYNCHRONIZED;
		} else if (n instanceof LabeledStmt) {
			return StmtKind.LABELED;
		} else if (n instanceof BlockStmt) {
			return StmtKind.BLOCK;
		} else if (n instanceof SwitchEntry) {
			return StmtKind.ENTRY;
		}
		return StmtKind.OTHER;
	}

	// ------------------------------------------------------------------------------------------
	// the length: the measurer's nloc
	// ------------------------------------------------------------------------------------------

	/** The distinct lines holding a code token, from the name to the end of the body, nested type members excluded. */
	static int nloc(Node decl, SimpleName name) {
		List<Range> skip = new ArrayList<>();
		nestedRanges(decl, skip);
		Optional<JavaToken> first = name.getTokenRange().map(tr -> tr.getBegin());
		Optional<JavaToken> last = decl.getTokenRange().map(tr -> tr.getEnd());
		if (first.isEmpty() || last.isEmpty()) {
			return 0;
		}
		Set<Integer> lines = new HashSet<>();
		JavaToken t = first.get();
		while (t != null) {
			if (!t.getCategory().isWhitespaceOrComment() && t.getRange().isPresent()) {
				Range r = t.getRange().get();
				boolean nested = false;
				for (Range s : skip) {
					if (s.contains(r)) {
						nested = true;
						break;
					}
				}
				if (!nested) {
					lines.add(r.begin.line);
				}
			}
			if (t == last.get()) {
				break;
			}
			t = t.getNextToken().orElse(null);
		}
		return lines.size();
	}

	/** The ranges of nested type members inside a callable, whose tokens are not the callable's. */
	static void nestedRanges(Node n, List<Range> out) {
		for (Node c : n.getChildNodes()) {
			if (isNestedTypePart(n, c)) {
				c.getRange().ifPresent(out::add);
			} else {
				nestedRanges(c, out);
			}
		}
	}

	// ------------------------------------------------------------------------------------------
	// own calls
	// ------------------------------------------------------------------------------------------

	/** The lines of the calls to the class's own instance methods, one entry per call. */
	static int[] ownCallLines(Node decl, List<Node> own) {
		Map<String, Boolean> instance = instanceMethods(decl);
		List<Integer> lines = new ArrayList<>();
		for (Node n : own) {
			if (n instanceof MethodCallExpr mce) {
				Expression scope = mce.getScope().orElse(null);
				if ((scope == null || scope instanceof ThisExpr)
						&& Boolean.TRUE.equals(instance.get(mce.getNameAsString()))) {
					lines.add(line(n));
				}
			} else if (n instanceof ExplicitConstructorInvocationStmt ecis && ecis.isThis()) {
				lines.add(line(n));
			}
		}
		return lines.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * The method names declared in the types around a callable, innermost first: true when any of
	 * them is an instance method (the call may bind the piece to the object), false when every one
	 * is static.
	 */
	static Map<String, Boolean> instanceMethods(Node decl) {
		Map<String, Boolean> out = new HashMap<>();
		for (Node p = decl.getParentNode().orElse(null); p != null; p = p.getParentNode().orElse(null)) {
			List<? extends Node> members;
			if (p instanceof TypeDeclaration<?> td) {
				members = td.getMembers();
			} else if (p instanceof ObjectCreationExpr oce && oce.getAnonymousClassBody().isPresent()) {
				members = oce.getAnonymousClassBody().get();
			} else {
				continue;
			}
			for (Node m : members) {
				if (m instanceof MethodDeclaration md) {
					out.merge(md.getNameAsString(), !md.isStatic(), Boolean::logicalOr);
				}
			}
		}
		return out;
	}

	/** The declaring type, outer types first; an anonymous class is {@code new Base}. */
	static String typeName(Node decl) {
		List<String> parts = new ArrayList<>();
		Node child = decl;
		for (Node p = decl.getParentNode().orElse(null); p != null; child = p, p = p.getParentNode().orElse(null)) {
			if (p instanceof TypeDeclaration<?> td) {
				parts.add(0, td.getNameAsString());
			} else if (p instanceof ObjectCreationExpr oce && child instanceof BodyDeclaration) {
				parts.add(0, "new " + oce.getType().getNameAsString());
			}
		}
		return String.join(".", parts);
	}

	// ------------------------------------------------------------------------------------------
	// names
	// ------------------------------------------------------------------------------------------

	/** A literal, or one under a unary sign, a cast or parentheses: a value from nowhere. */
	static boolean literal(Expression e) {
		if (e instanceof LiteralExpr) {
			return true;
		}
		if (e instanceof UnaryExpr u) {
			return literal(u.getExpression());
		}
		if (e instanceof CastExpr c) {
			return literal(c.getExpression());
		}
		if (e instanceof EnclosedExpr p) {
			return literal(p.getInner());
		}
		return false;
	}

	/** The node a pattern variable is visible in: the switch entry, or the block of its statement. */
	static Node patternScope(Node pattern) {
		Node p = pattern.getParentNode().orElse(null);
		while (p != null) {
			if (p instanceof SwitchEntry) {
				return p;
			}
			if (p instanceof Statement) {
				return p.getParentNode().orElse(p);
			}
			p = p.getParentNode().orElse(null);
		}
		return pattern;
	}

	/** A simple name that is not a variable: a type or constant by convention, a case label, an annotation. */
	static boolean excluded(NameExpr ne) {
		if (UPPER.matcher(ne.getNameAsString()).matches()) {
			return true;
		}
		if (ne.findAncestor(AnnotationExpr.class).isPresent()) {
			return true;
		}
		return ne.getParentNode().orElse(null) instanceof SwitchEntry se && se.getLabels().contains(ne);
	}

	/** The innermost declaration of the name whose scope holds the use and that precedes it. */
	static VarB resolve(NameExpr ne, List<VarB> locals) {
		String s = ne.getNameAsString();
		int l = line(ne), c = col(ne);
		VarB best = null;
		for (VarB v : locals) {
			if (!v.name.equals(s) || !within(v.scope, ne)) {
				continue;
			}
			if (v.kind == Var.Kind.LOCAL && (v.declLine > l || (v.declLine == l && v.declCol > c))) {
				continue;
			}
			if (best == null || within(best.scope, v.scope)) {
				best = v;
			}
		}
		return best;
	}

	/** The variable a write target rebinds: a simple name or {@code this.x}. */
	static VarB varOf(Expression target, List<VarB> locals, Map<String, VarB> fields) {
		if (target instanceof NameExpr ne && !excluded(ne)) {
			VarB v = resolve(ne, locals);
			return v != null ? v : fields.get(ne.getNameAsString());
		}
		if (target instanceof FieldAccessExpr fa && fa.getScope() instanceof ThisExpr) {
			return fields.get(fa.getNameAsString());
		}
		return null;
	}
}
