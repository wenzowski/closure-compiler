/*
 * Copyright 2013 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.parsing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.IdentifierToken;
import com.google.javascript.jscomp.parsing.parser.LiteralToken;
import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.BinaryOperatorTree;
import com.google.javascript.jscomp.parsing.parser.trees.BlockTree;
import com.google.javascript.jscomp.parsing.parser.trees.BreakStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.CallExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.CaseClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.CatchTree;
import com.google.javascript.jscomp.parsing.parser.trees.ClassDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.CommaExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.trees.ConditionalExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ContinueStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DebuggerStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.DoWhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.EmptyStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExpressionStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FinallyTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForInStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForOfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FormalParameterListTree;
import com.google.javascript.jscomp.parsing.parser.trees.FunctionDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.GetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.IdentifierExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.IfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.LabelledStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.LiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberLookupExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MissingPrimaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ModuleImportTree;
import com.google.javascript.jscomp.parsing.parser.trees.NewExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NullTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParenExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTreeType;
import com.google.javascript.jscomp.parsing.parser.trees.PostfixExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.trees.PropertyNameAssignmentTree;
import com.google.javascript.jscomp.parsing.parser.trees.ReturnStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.SetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.SuperExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.SwitchStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThisExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThrowStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.TryStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.UnaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationListTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WithStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.YieldExpressionTree;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * IRFactory transforms the external AST to the internal AST.
 */
class NewIRFactory {

  static final String GETTER_ERROR_MESSAGE =
      "getters are not supported in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.";

  static final String SETTER_ERROR_MESSAGE =
      "setters are not supported in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.";

  static final String SUSPICIOUS_COMMENT_WARNING =
      "Non-JSDoc comment has annotations. " +
      "Did you mean to start it with '/**'?";

  static final String MISPLACED_TYPE_ANNOTATION =
      "Type annotations are not allowed here. Are you missing parentheses?";

  static final String INVALID_ES3_PROP_NAME =
      "Keywords and reserved words are not allowed as unquoted property " +
      "names in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.";

  static final String INVALID_ES5_STRICT_OCTAL =
      "Octal integer literals are not supported in Ecmascript 5 strict mode.";

  static final String STRING_CONTINUATION_WARNING =
      "String continuations are not supported in this language mode.";

  static final String BINARY_NUMBER_LITERAL_WARNING =
      "Binary integer literals are not supported in this language mode.";

  static final String OCTAL_NUMBER_LITERAL_WARNING =
      "Octal integer literals are not supported in this language mode.";

  static final String DUPLICATE_PARAMETER =
      "Duplicate parameter name \"%s\".";

  static final String UNLABELED_BREAK =
      "unlabelled break must be inside loop or switch";

  static final String UNEXPECTED_CONTINUE = "continue must be inside loop";

  static final String UNEXPECTED_LABLED_CONTINUE =
      "continue can only use labeles of iteration statements";

  static final String UNDEFINED_LABEL = "undefined label \"%s\"";

  private final String sourceString;
  private final List<Integer> newlines;
  private final StaticSourceFile sourceFile;
  private final String sourceName;
  private final Config config;
  private final ErrorReporter errorReporter;
  private final TransformDispatcher transformDispatcher;

  private static final ImmutableSet<String> ALLOWED_DIRECTIVES =
      ImmutableSet.of("use strict");

  private static final ImmutableSet<String> ES5_RESERVED_KEYWORDS =
      ImmutableSet.of(
          // From Section 7.6.1.2
          "class", "const", "enum", "export", "extends", "import", "super");
  private static final ImmutableSet<String> ES5_STRICT_RESERVED_KEYWORDS =
      ImmutableSet.of(
          // From Section 7.6.1.2
          "class", "const", "enum", "export", "extends", "import", "super",
          "implements", "interface", "let", "package", "private", "protected",
          "public", "static", "yield");

  private final Set<String> reservedKeywords;
  private final Set<Comment> parsedComments = Sets.newHashSet();

  // @license text gets appended onto the fileLevelJsDocBuilder as found,
  // and stored in JSDocInfo for placeholder node.
  Node rootNodeJsDocHolder = new Node(Token.SCRIPT);
  Node.FileLevelJsDocBuilder fileLevelJsDocBuilder =
      rootNodeJsDocHolder.getJsDocBuilderForNode();
  JSDocInfo fileOverviewInfo = null;

  // Use a template node for properties set on all nodes to minimize the
  // memory footprint associated with these.
  private final Node templateNode;

  private final UnmodifiableIterator<Comment> nextCommentIter;

  private Comment currentComment;

  private NewIRFactory(String sourceString,
                    StaticSourceFile sourceFile,
                    Config config,
                    ErrorReporter errorReporter,
                    ImmutableList<Comment> comments) {
    this.sourceString = sourceString;
    this.nextCommentIter = comments.iterator();
    this.currentComment = nextCommentIter.hasNext() ? nextCommentIter.next() : null;
    this.newlines = Lists.newArrayList();
    this.sourceFile = sourceFile;

    // Pre-generate all the newlines in the file.
    for (int charNo = 0; true; charNo++) {
      charNo = sourceString.indexOf('\n', charNo);
      if (charNo == -1) {
        break;
      }
      newlines.add(Integer.valueOf(charNo));
    }

    // Sometimes this will be null in tests.
    this.sourceName = sourceFile == null ? null : sourceFile.getName();

    this.config = config;
    this.errorReporter = errorReporter;
    this.transformDispatcher = new TransformDispatcher();
    // The template node properties are applied to all nodes in this transform.
    this.templateNode = createTemplateNode();

    switch (config.languageMode) {
      case ECMASCRIPT3:
        reservedKeywords = null; // use TokenStream.isKeyword instead
        break;
      case ECMASCRIPT5:
        reservedKeywords = ES5_RESERVED_KEYWORDS;
        break;
      case ECMASCRIPT5_STRICT:
        reservedKeywords = ES5_STRICT_RESERVED_KEYWORDS;
        break;
      case ECMASCRIPT6:
        reservedKeywords = ES5_RESERVED_KEYWORDS;
        break;
      case ECMASCRIPT6_STRICT:
        reservedKeywords = ES5_STRICT_RESERVED_KEYWORDS;
        break;
      default:
        throw new IllegalStateException("unknown language mode");
    }
  }

  // Create a template node to use as a source of common attributes, this allows
  // the prop structure to be shared among all the node from this source file.
  // This reduces the cost of these properties to O(nodes) to O(files).
  private Node createTemplateNode() {
    // The Node type choice is arbitrary.
    Node templateNode = new Node(Token.SCRIPT);
    templateNode.setStaticSourceFile(sourceFile);
    return templateNode;
  }

  public static Node transformTree(ProgramTree tree,
                                   StaticSourceFile sourceFile,
                                   String sourceString,
                                   Config config,
                                   ErrorReporter errorReporter) {
    NewIRFactory irFactory = new NewIRFactory(sourceString, sourceFile,
        config, errorReporter, tree.sourceComments);

    // don't call transform as we don't want standard jsdoc handling.
    Node n = irFactory.justTransform(tree);
    irFactory.setSourceInfo(n, tree);

    if (tree.sourceComments != null) {
      for (Comment comment : tree.sourceComments) {
        if (comment.type == Comment.Type.JSDOC &&
            !irFactory.parsedComments.contains(comment)) {
          irFactory.handlePossibleFileOverviewJsDoc(comment);
        } else if (comment.type == Comment.Type.BLOCK) {
          irFactory.handleBlockComment(comment);
        }
      }
    }

    irFactory.setFileOverviewJsDoc(n);

    irFactory.validateAll(n);

    // DEBUG: remove this, verify the tree can be print, remove this.
    n.toStringTree();
    //

    return n;
  }

  private void validateAll(Node n) {
    validate(n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateAll(c);
    }
  }

  private void validate(Node n) {
    validateTypeAnnotations(n);
    validateParameters(n);
    validateBreakContinue(n);
  }

  private void validateBreakContinue(Node n) {
    if (n.isBreak() || n.isContinue()) {
      Node labelName = n.getFirstChild();
      if (labelName != null) {
        Node parent = n.getParent();
        while (!parent.isLabel() || !labelsMatch(parent, labelName)) {
          if (parent.isFunction() || parent.isScript()) {
            // report missing label
            errorReporter.error(
                String.format(UNDEFINED_LABEL, labelName.getString()),
                sourceName,
                n.getLineno(), "", n.getCharno());
            break;
          }
          parent = parent.getParent();
        }
        if (parent.isLabel() && labelsMatch(parent, labelName)) {
          if (n.isContinue() && !isContinueTarget(parent.getLastChild())) {
            // report invalid continue target
            errorReporter.error(
                UNEXPECTED_LABLED_CONTINUE,
                sourceName,
                n.getLineno(), "", n.getCharno());
          }
        }
      } else {
        if (n.isContinue()) {
          Node parent = n.getParent();
          while (!isContinueTarget(parent)) {
            if (parent.isFunction() || parent.isScript()) {
              // report invalid continue
              errorReporter.error(
                  String.format(UNEXPECTED_CONTINUE),
                  sourceName,
                  n.getLineno(), "", n.getCharno());
              break;
            }
            parent = parent.getParent();
          }
        } else {
          Node parent = n.getParent();
          while (!isBreakTarget(parent)) {
            if (parent.isFunction() || parent.isScript()) {
              // report invalid break
              errorReporter.error(
                  String.format(UNLABELED_BREAK),
                  sourceName,
                  n.getLineno(), "", n.getCharno());
              break;
            }
            parent = parent.getParent();
          }
        }
      }
    }
  }

  private boolean isBreakTarget(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.WHILE:
      case Token.DO:
      case Token.SWITCH:
        return true;
    }
    return false;
  }

  private boolean isContinueTarget(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.WHILE:
      case Token.DO:
        return true;
    }
    return false;
  }


  private boolean labelsMatch(Node label, Node labelName) {
    return label.getFirstChild().getString().equals(labelName.getString());
  }

  private void validateParameters(Node n) {
    if (n.isParamList()) {
      Node c = n.getFirstChild();
      for (; c != null; c = c.getNext()) {
        if (!c.isName()) {
          continue;
        }
        Node sibling = c.getNext();
        for (; sibling != null; sibling = sibling.getNext()) {
          if (sibling.isName() && c.getString().equals(sibling.getString())) {
            errorReporter.warning(
                String.format(DUPLICATE_PARAMETER, c.getString()),
                sourceName,
                n.getLineno(), "", n.getCharno());
          }
        }
      }
    }
  }

  @SuppressWarnings("incomplete-switch")
  private void validateTypeAnnotations(Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info != null && info.hasType()) {
      boolean valid = false;
      switch (n.getType()) {
        // Casts are valid
        case Token.CAST:
          valid = true;
          break;
        // Variable declarations are valid
        case Token.VAR:
          valid = true;
          break;
        // Function declarations are valid
        case Token.FUNCTION:
          valid = isFunctionDeclaration(n);
          break;
        // Object literal properties, catch declarations and variable
        // initializers are valid.
        case Token.NAME:
          Node parent = n.getParent();
          switch (parent.getType()) {
            case Token.STRING_KEY:
            case Token.GETTER_DEF:
            case Token.SETTER_DEF:
            case Token.CATCH:
            case Token.FUNCTION:
            case Token.VAR:
            case Token.LP:
              valid = true;
              break;
          }
          break;
        // Object literal properties are valid
        case Token.STRING_KEY:
        case Token.GETTER_DEF:
        case Token.SETTER_DEF:
          valid = true;
          break;
        // Property assignments are valid, if at the root of an expression.
        case Token.ASSIGN:
          valid = n.getParent().isExprResult()
            && (n.getFirstChild().isGetProp() || n.getFirstChild().isGetElem());
          break;
        case Token.GETPROP:
          valid = n.getParent().isExprResult() && n.getQualifiedName() != null;
          break;
        case Token.CALL:
          valid = info.isDefine();
          break;
      }

      if (!valid) {
        errorReporter.warning(MISPLACED_TYPE_ANNOTATION,
            sourceName,
            n.getLineno(), "", n.getCharno());
      }
    }
  }

  private void setFileOverviewJsDoc(Node irNode) {
    // Only after we've seen all @fileoverview entries, attach the
    // last one to the root node, and copy the found license strings
    // to that node.
    JSDocInfo rootNodeJsDoc = rootNodeJsDocHolder.getJSDocInfo();
    if (rootNodeJsDoc != null) {
      irNode.setJSDocInfo(rootNodeJsDoc);
      rootNodeJsDoc.setAssociatedNode(irNode);
    }

    if (fileOverviewInfo != null) {
      if ((irNode.getJSDocInfo() != null) &&
          (irNode.getJSDocInfo().getLicense() != null)) {
        fileOverviewInfo.setLicense(irNode.getJSDocInfo().getLicense());
      }
      irNode.setJSDocInfo(fileOverviewInfo);
      fileOverviewInfo.setAssociatedNode(irNode);
    }
  }

  private Node transformBlock(ParseTree node) {
    Node irNode = transform(node);
    if (!irNode.isBlock()) {
      if (irNode.isEmpty()) {
        irNode.setType(Token.BLOCK);
        irNode.setWasEmptyNode(true);
      } else {
        Node newBlock = newNode(Token.BLOCK, irNode);
        setSourceInfo(newBlock, irNode);
        irNode = newBlock;
      }
    }
    return irNode;
  }

  /**
   * Check to see if the given block comment looks like it should be JSDoc.
   */
  private void handleBlockComment(Comment comment) {
    Pattern p = Pattern.compile("(/|(\n[ \t]*))\\*[ \t]*@[a-zA-Z]+[ \t\n{]");
    if (p.matcher(comment.value).find()) {
      errorReporter.warning(
          SUSPICIOUS_COMMENT_WARNING,
          sourceName,
          comment.location.start.line, "", 0);
    }
  }

  /**
   * @return true if the jsDocParser represents a fileoverview.
   */
  private boolean handlePossibleFileOverviewJsDoc(
      JsDocInfoParser jsDocParser) {
    if (jsDocParser.getFileOverviewJSDocInfo() != fileOverviewInfo) {
      fileOverviewInfo = jsDocParser.getFileOverviewJSDocInfo();
      return true;
    }
    return false;
  }

  private void handlePossibleFileOverviewJsDoc(Comment comment) {
    JsDocInfoParser jsDocParser = createJsDocInfoParser(comment);
    parsedComments.add(comment);
    handlePossibleFileOverviewJsDoc(jsDocParser);
  }

  private Comment getJsDoc(SourceRange location) {
    Comment closestPreviousComment = null;
    while (currentComment != null &&
        currentComment.location.end.offset <= location.start.offset) {
      if (currentComment.type == Comment.Type.JSDOC) {
        closestPreviousComment = currentComment;
      }
      if (this.nextCommentIter.hasNext()) {
        currentComment = this.nextCommentIter.next();
      } else {
        currentComment = null;
      }
    }

    return closestPreviousComment;
  }

  private Comment getJsDoc(ParseTree tree) {
    return getJsDoc(tree.location);
  }

  private Comment getJsDoc(
      com.google.javascript.jscomp.parsing.parser.Token token) {
    return getJsDoc(token.location);
  }

  private JSDocInfo handleJsDoc(Comment comment) {
    if (comment != null) {
      JsDocInfoParser jsDocParser = createJsDocInfoParser(comment);
      parsedComments.add(comment);
      if (!handlePossibleFileOverviewJsDoc(jsDocParser)) {
        JSDocInfo info = jsDocParser.retrieveAndResetParsedJSDocInfo();
        if (info != null) {
          // validateTypeAnnotations(info, node);
        }
        return info;
      }
    }
    return null;
  }

  private JSDocInfo handleJsDoc(ParseTree node) {
    if (!shouldAttachJSDocHere(node)) {
      return null;
    }
    return handleJsDoc(getJsDoc(node));
  }

  private boolean shouldAttachJSDocHere(ParseTree tree) {
    switch (tree.type) {
      case EXPRESSION_STATEMENT:
        return false;
      case LABELLED_STATEMENT:
        return false;
      case CALL_EXPRESSION:
      case BINARY_OPERATOR:
      case MEMBER_EXPRESSION:
      case MEMBER_LOOKUP_EXPRESSION:
        ParseTree nearest = findNearestNode(tree);
        if (nearest.type == ParseTreeType.PAREN_EXPRESSION) {
          return false;
        }
        return true;
      default:
        return true;
    }
  }

  private ParseTree findNearestNode(ParseTree tree) {
    switch (tree.type) {
      case EXPRESSION_STATEMENT:
        return findNearestNode(tree.asExpressionStatement().expression);
      case CALL_EXPRESSION:
        return findNearestNode(tree.asCallExpression().operand);
      case BINARY_OPERATOR:
        return findNearestNode(tree.asBinaryOperator().left);
      case MEMBER_EXPRESSION:
        return findNearestNode(tree.asMemberExpression().operand);
      case MEMBER_LOOKUP_EXPRESSION:
        return findNearestNode(tree.asMemberLookupExpression().operand);
      default:
        return tree;
    }
  }

  private JSDocInfo handleJsDoc(
      com.google.javascript.jscomp.parsing.parser.Token token) {
    return handleJsDoc(getJsDoc(token));
  }

  private boolean isFunctionDeclaration(Node n) {
    return n.isFunction() && isStmtContainer(n.getParent());
  }

  private boolean isStmtContainer(Node n) {
    return n.isBlock() || n.isScript();
  }

  private Node transform(ParseTree tree) {
    JSDocInfo info = handleJsDoc(tree);
    Node node = justTransform(tree);
    if (info != null) {
      node = maybeInjectCastNode(tree, info, node);
      attachJSDoc(info, node);
    }
    setSourceInfo(node, tree);
    return node;
  }

  private void attachJSDoc(JSDocInfo info, Node n) {
    info.setAssociatedNode(n);
    n.setJSDocInfo(info);
  }

  private Node maybeInjectCastNode(ParseTree node, JSDocInfo info, Node irNode) {
    if (node.type == ParseTreeType.PAREN_EXPRESSION
        && info.hasType()) {
      irNode = newNode(Token.CAST, irNode);
    }
    return irNode;
  }

  /**
   * NAMEs in parameters or variable declarations are special, because they can
   * have inline type docs attached.
   *
   * function f(/** string &#42;/ x) {}
   * annotates 'x' as a string.
   *
   * @see <a href="http://code.google.com/p/jsdoc-toolkit/wiki/InlineDocs">
   *   Using Inline Doc Comments</a>
   */
  private Node transformNodeWithInlineJsDoc(
      ParseTree node, boolean optionalInline) {
    JSDocInfo info = handleInlineJsDoc(node, optionalInline);
    Node irNode = justTransform(node);
    if (info != null) {
      irNode.setJSDocInfo(info);
    }
    setSourceInfo(irNode, node);
    return irNode;
  }

  private JSDocInfo handleInlineJsDoc(ParseTree node, boolean optional) {
    return handleInlineJsDoc(node.location, optional);
  }

  private JSDocInfo handleInlineJsDoc(
      com.google.javascript.jscomp.parsing.parser.Token token,
      boolean optional) {
    return handleInlineJsDoc(token.location, optional);
  }

  private JSDocInfo handleInlineJsDoc(
      SourceRange location,
      boolean optional) {
    Comment comment = getJsDoc(location);
    if (comment != null && (!optional || !comment.value.contains("@"))) {
      return parseInlineTypeDoc(comment);
    } else {
      return handleJsDoc(comment);
    }
  }

  private Node transformNumberAsString(LiteralToken token) {
    double value = normalizeNumber(token);
    Node irNode = newStringNode(getStringValue(value));
    JSDocInfo jsDocInfo = handleJsDoc(token);
    if (jsDocInfo != null) {
      irNode.setJSDocInfo(jsDocInfo);
    }
    setSourceInfo(irNode, token);
    return irNode;
  }

  private static String getStringValue(double value) {
    long longValue = (long) value;

    // Return "1" instead of "1.0"
    if (longValue == value) {
      return Long.toString(longValue);
    } else {
      return Double.toString(value);
    }
  }

  private int lineno(ParseTree node) {
    // location lines start at zero, our AST starts at 1.
    return lineno(node.location.start);
  }

  private int charno(ParseTree node) {
    return charno(node.location.start);
  }

  private int lineno(SourcePosition location) {
    // location lines start at zero, our AST starts at 1.
    return location.line + 1;
  }

  private int charno(SourcePosition location) {
    return location.column;
  }

  private void setSourceInfo(Node node, Node ref) {
    node.setLineno(ref.getLineno());
    node.setCharno(ref.getCharno());
    maybeSetLengthFrom(node, ref);
  }

  private void setSourceInfo(Node irNode, ParseTree node) {
    if (irNode.getLineno() == -1) {
      setSourceInfo(irNode, node.location.start, node.location.end);
    }
  }

  private void setSourceInfo(
      Node irNode, com.google.javascript.jscomp.parsing.parser.Token token) {
    setSourceInfo(irNode, token.location.start, token.location.end);
  }

  private void setSourceInfo(
      Node node, SourcePosition start, SourcePosition end) {
    if (node.getLineno() == -1) {
      // If we didn't already set the line, then set it now. This avoids
      // cases like ParenthesizedExpression where we just return a previous
      // node, but don't want the new node to get its parent's line number.
      int lineno = lineno(start);
      node.setLineno(lineno);
      int charno = charno(start);
      node.setCharno(charno);
      maybeSetLength(node, start, end);
    }
  }

  /**
   * Creates a JsDocInfoParser and parses the JsDoc string.
   *
   * Used both for handling individual JSDoc comments and for handling
   * file-level JSDoc comments (@fileoverview and @license).
   *
   * @param node The JsDoc Comment node to parse.
   * @return A JsDocInfoParser. Will contain either fileoverview JsDoc, or
   *     normal JsDoc, or no JsDoc (if the method parses to the wrong level).
   */
  private JsDocInfoParser createJsDocInfoParser(Comment node) {
    String comment = node.value;
    int lineno = node.location.start.line;
    int charno = node.location.start.column;
    int position = node.location.start.offset;

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser jsdocParser =
      new JsDocInfoParser(
          new JsDocTokenStream(comment.substring(numOpeningChars),
                               lineno,
                               charno + numOpeningChars),
          comment,
          position,
          null,
          sourceFile,
          config,
          errorReporter);
    jsdocParser.setFileLevelJsDocBuilder(fileLevelJsDocBuilder);
    jsdocParser.setFileOverviewJSDocInfo(fileOverviewInfo);
    jsdocParser.parse();
    return jsdocParser;
  }

  /**
   * Parses inline type info.
   */
  private JSDocInfo parseInlineTypeDoc(Comment node) {
    String comment = node.value;
    int lineno = node.location.start.line + 1;
    int charno = node.location.start.column;

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser parser =
      new JsDocInfoParser(
          new JsDocTokenStream(comment.substring(numOpeningChars),
                               lineno,
                               charno + numOpeningChars),
          comment,
          node.location.start.offset,
          null,
          sourceFile,
          config,
          errorReporter);
    return parser.parseInlineTypeDoc();
  }

  // Set the length on the node if we're in IDE mode.
  private void maybeSetLength(
      Node node, SourcePosition start, SourcePosition end) {
    if (config.isIdeMode) {
      node.setLength(end.offset - start.offset);
    }
  }

  private void maybeSetLengthFrom(Node node, Node ref) {
    if (config.isIdeMode) {
      node.setLength(ref.getLength());
    }
  }

  private void maybeSetLength(Node node, int length) {
    if (config.isIdeMode) {
      node.setLength(length);
    }
  }

  /*
  private int position2charno(int position) {
    int newlineIndex = Collections.binarySearch(newlines, position);
    int lineIndex = -1;
    if (newlineIndex >= 0) {
      lineIndex = newlines.get(newlineIndex);
    } else if (newlineIndex <= -2) {
      lineIndex = newlines.get(-newlineIndex - 2);
    }

    if (lineIndex == -1) {
      return position;
    } else {
      // Subtract one for initial position being 0.
      return position - lineIndex - 1;
    }
  }
  */

  private Node justTransform(ParseTree node) {
    return transformDispatcher.process(node);
  }

  private class TransformDispatcher extends NewTypeSafeDispatcher<Node> {
    /**
     * Transforms the given node and then sets its type to Token.STRING if it
     * was Token.NAME. If its type was already Token.STRING, then quotes it.
     * Used for properties, as the old AST uses String tokens, while the new one
     * uses Name tokens for unquoted strings. For example, in
     * var o = {'a' : 1, b: 2};
     * the string 'a' is quoted, while the name b is turned into a string, but
     * unquoted.
     */
    private Node processObjecLitKeyAsString(
        com.google.javascript.jscomp.parsing.parser.Token token) {
      Node ret;
      if (token == null) {
        return createMissingExpressionNode();
      } else if (token.type == TokenType.IDENTIFIER) {
        ret = processName(token.asIdentifier(), true);
      } else if (token.type == TokenType.NUMBER) {
        ret = transformNumberAsString(token.asLiteral());
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      } else {
        ret = processString(token.asLiteral());
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      }
      Preconditions.checkState(ret.isString());
      return ret;
    }

    @Override
    Node processArrayLiteral(ArrayLiteralExpressionTree tree) {
      Node node = newNode(Token.ARRAYLIT);
      for (ParseTree child : tree.elements) {
        Node c = transform(child);
        node.addChildToBack(c);
      }
      return node;
    }

    @Override
    Node processAstRoot(ProgramTree rootNode) {
      Node node = newNode(Token.SCRIPT);
      for (ParseTree child : rootNode.sourceElements) {
        node.addChildToBack(transform(child));
      }
      parseDirectives(node);
      return node;
    }

    /**
     * Parse the directives, encode them in the AST, and remove their nodes.
     *
     * For information on ES5 directives, see section 14.1 of
     * ECMA-262, Edition 5.
     *
     * It would be nice if Rhino would eventually take care of this for
     * us, but right now their directive-processing is a one-off.
     */
    private void parseDirectives(Node node) {
      // Remove all the directives, and encode them in the AST.
      Set<String> directives = null;
      while (isDirective(node.getFirstChild())) {
        String directive = node.removeFirstChild().getFirstChild().getString();
        if (directives == null) {
          directives = Sets.newHashSet(directive);
        } else {
          directives.add(directive);
        }
      }

      if (directives != null) {
        node.setDirectives(directives);
      }
    }

    private boolean isDirective(Node n) {
      if (n == null) {
        return false;
      }
      int nType = n.getType();
      return nType == Token.EXPR_RESULT &&
          n.getFirstChild().isString() &&
          ALLOWED_DIRECTIVES.contains(n.getFirstChild().getString());
    }

    @Override
    Node processBlock(BlockTree blockNode) {
      Node node = newNode(Token.BLOCK);
      for (ParseTree child : blockNode.statements) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processBreakStatement(BreakStatementTree statementNode) {
      Node node = newNode(Token.BREAK);
      if (statementNode.getLabel() != null) {
        Node labelName = transformLabelName(statementNode.name);
        node.addChildToBack(labelName);
      }
      return node;
    }

    Node transformLabelName(IdentifierToken token) {
      Node label =  newStringNode(Token.LABEL_NAME, token.value);
      setSourceInfo(label, token);
      return label;
    }

    @Override
    Node processConditionalExpression(ConditionalExpressionTree exprNode) {
      return newNode(
          Token.HOOK,
          transform(exprNode.condition),
          transform(exprNode.left),
          transform(exprNode.right));
    }

    @Override
    Node processContinueStatement(ContinueStatementTree statementNode) {
      Node node = newNode(Token.CONTINUE);
      if (statementNode.getLabel() != null) {
        Node labelName = transformLabelName(statementNode.name);
        node.addChildToBack(labelName);
      }
      return node;
    }

    @Override
    Node processDoLoop(DoWhileStatementTree loopNode) {
      return newNode(
          Token.DO,
          transformBlock(loopNode.body),
          transform(loopNode.condition));
    }

    @Override
    Node processElementGet(MemberLookupExpressionTree getNode) {
      return newNode(
          Token.GETELEM,
          transform(getNode.operand),
          transform(getNode.memberExpression));
    }

    @Override
    Node processEmptyStatement(EmptyStatementTree exprNode) {
      Node node = newNode(Token.EMPTY);
      return node;
    }

    @Override
    Node processExpressionStatement(ExpressionStatementTree statementNode) {
      Node node = newNode(Token.EXPR_RESULT);
      node.addChildToBack(transform(statementNode.expression));
      return node;
    }

    @Override
    Node processForInLoop(ForInStatementTree loopNode) {
      return newNode(
          Token.FOR,
          transform(loopNode.initializer),
          transform(loopNode.collection),
          transformBlock(loopNode.body));
    }

    @Override
    Node processForOf(ForOfStatementTree loopNode) {
      return newNode(
          Token.FOR_OF,
          transform(loopNode.initializer),
          transform(loopNode.collection),
          transformBlock(loopNode.body));
    }

    @Override
    Node processForLoop(ForStatementTree loopNode) {
      Node node = newNode(
          Token.FOR,
          transformOrEmpty(loopNode.initializer),
          transformOrEmpty(loopNode.condition),
          transformOrEmpty(loopNode.increment));
      node.addChildToBack(transformBlock(loopNode.body));
      return node;
    }

    Node transformOrEmpty(ParseTree tree) {
      return (tree == null) ? newNode(Token.EMPTY) : transform(tree);
    }

    Node transformOrEmpty(IdentifierToken token) {
      return (token == null) ? newNode(Token.EMPTY) : processName(token);
    }

    @Override
    Node processFunctionCall(CallExpressionTree callNode) {
      Node node = newNode(Token.CALL,
                           transform(callNode.operand));
      for (ParseTree child : callNode.arguments.arguments) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processFunction(FunctionDeclarationTree functionTree) {
      boolean isDeclaration = (functionTree.kind == FunctionDeclarationTree.Kind.DECLARATION);
      boolean isMember = (functionTree.kind == FunctionDeclarationTree.Kind.MEMBER);
      boolean isArrow = (functionTree.kind == FunctionDeclarationTree.Kind.ARROW);
      boolean isGenerator = functionTree.isGenerator;

      if (!isEs6Mode()) {
        if (isGenerator) {
          maybeWarnEs6Feature(functionTree, "generators");
        }

        if (isMember) {
          maybeWarnEs6Feature(functionTree, "member declarations");
        }

        if (isArrow) {
          maybeWarnEs6Feature(functionTree, "short function syntax");
        }
      }

      IdentifierToken name = functionTree.name;
      Node newName;
      if (name != null) {
        newName = processNameWithInlineJSDoc(name);
      } else {
        if (isDeclaration || isMember) {
          errorReporter.error(
            "unnamed function statement",
            sourceName,
            lineno(functionTree), "", charno(functionTree));

          // Return the bare minimum to put the AST in a valid state.
          newName = createMissingNameNode();
        } else {
          newName = newStringNode(Token.NAME, "");
        }

        // Old Rhino tagged the empty name node with the line number of the
        // declaration.
        newName.setLineno(lineno(functionTree));
        newName.setCharno(charno(functionTree));
        maybeSetLength(newName, 0);
      }

      Node node = newNode(Token.FUNCTION);
      if (!isMember) {
        node.addChildToBack(newName);
      } else {
        Node emptyName = newStringNode(Token.NAME, "");
        emptyName.setLineno(lineno(functionTree));
        emptyName.setCharno(charno(functionTree));
        maybeSetLength(emptyName, 0);
        node.addChildToBack(emptyName);
      }
      node.addChildToBack(transform(functionTree.formalParameterList));

      Node bodyNode = transform(functionTree.functionBody);
      if (!isArrow && !bodyNode.isBlock()) {
        // When in ideMode the parser tries to parse some constructs the
        // compiler doesn't support, repair it here.
        Preconditions.checkState(config.isIdeMode);
        bodyNode = IR.block();
      }
      parseDirectives(bodyNode);
      node.addChildToBack(bodyNode);

      Node result;

      if (functionTree.kind == FunctionDeclarationTree.Kind.MEMBER) {
        setSourceInfo(node, functionTree);
        Node member = newStringNode(Token.MEMBER_DEF, name.value);
        member.addChildToBack(node);
        member.setStaticMember(functionTree.isStatic);
        result = member;
      } else {
        result = node;
      }

      result.setIsGeneratorFunction(isGenerator);
      result.setIsArrowFunction(isArrow);

      return result;
    }

    @Override
    Node processFormalParameterList(FormalParameterListTree tree) {
      Node params = newNode(Token.PARAM_LIST);
      for (ParseTree param : tree.parameters) {
        Node paramNode = transformNodeWithInlineJsDoc(param, false);
        // We only support simple names for the moment.
        Preconditions.checkState(paramNode.isName());
        params.addChildToBack(paramNode);
      }
      return params;
    }

    @Override
    Node processIfStatement(IfStatementTree statementNode) {
      Node node = newNode(Token.IF);
      node.addChildToBack(transform(statementNode.condition));
      node.addChildToBack(transformBlock(statementNode.ifClause));
      if (statementNode.elseClause != null) {
        node.addChildToBack(transformBlock(statementNode.elseClause));
      }
      return node;
    }

    @Override
    Node processBinaryExpression(BinaryOperatorTree exprNode) {
      Node n =  newNode(
          transformBinaryTokenType(exprNode.operator.type),
          transform(exprNode.left),
          transform(exprNode.right));

      if (isAssignmentOp(n)) {
        Node target = n.getFirstChild();
        if (!validAssignmentTarget(target)) {
          errorReporter.error(
              "invalid assignment target",
              sourceName,
              target.getLineno(), "", 0);
        }
      }

      return n;
    }

    // Move this to a more maintainable location.
    boolean isAssignmentOp(Node n) {
      switch (n.getType()){
        case Token.ASSIGN:
        case Token.ASSIGN_BITOR:
        case Token.ASSIGN_BITXOR:
        case Token.ASSIGN_BITAND:
        case Token.ASSIGN_LSH:
        case Token.ASSIGN_RSH:
        case Token.ASSIGN_URSH:
        case Token.ASSIGN_ADD:
        case Token.ASSIGN_SUB:
        case Token.ASSIGN_MUL:
        case Token.ASSIGN_DIV:
        case Token.ASSIGN_MOD:
          return true;
      }
      return false;
    }

    @Override
    Node processDebuggerStatement(DebuggerStatementTree node) {
      return newNode(Token.DEBUGGER);
    }

    @Override
    Node processThisExpression(ThisExpressionTree node) {
      return newNode(Token.THIS);
    }

    @Override
    Node processLabeledStatement(LabelledStatementTree labelTree) {
      Node node = newNode(Token.LABEL,
          transformLabelName(labelTree.name),
          transform(labelTree.statement));
      return node;
    }

    @Override
    Node processName(IdentifierExpressionTree nameNode) {
      return processName(nameNode, false);
    }

    Node processName(IdentifierExpressionTree nameNode, boolean asString) {
      return processName(nameNode.identifierToken, asString);
    }

    Node processName(IdentifierToken identifierToken) {
      return processName(identifierToken, false);
    }

    Node processName(IdentifierToken identifierToken, boolean asString) {
      Node node;
      if (asString) {
        node = newStringNode(Token.STRING, identifierToken.value);
      } else {
        JSDocInfo info = handleJsDoc(identifierToken);
        if (identifierToken == null ||
            isReservedKeyword(identifierToken.toString())) {
          errorReporter.error(
            "identifier is a reserved word",
            sourceName,
            identifierToken.location.start.line, "", 0);
        }
        node = newStringNode(Token.NAME, identifierToken.value);
        if (info != null) {
          // validateTypeAnnotations(info, identifierToken);
          attachJSDoc(info, node);
        }
      }
      setSourceInfo(node, identifierToken);
      return node;
    }

    Node processString(LiteralToken token) {
      Preconditions.checkState(token.type == TokenType.STRING);
      Node node = newStringNode(Token.STRING, normalizeString(token));
      setSourceInfo(node, token);
      return node;
    }

    Node processNameWithInlineJSDoc(IdentifierToken identifierToken) {
      Node node;
      JSDocInfo info = handleInlineJsDoc(identifierToken, true);
      if (identifierToken == null ||
          isReservedKeyword(identifierToken.toString())) {
        errorReporter.error(
          "identifier is a reserved word",
          sourceName,
          identifierToken.location.start.line, "", 0);
      }
      node = newStringNode(Token.NAME, identifierToken.toString());
      if (info != null) {
        // validateTypeAnnotations(info, identifierToken);
        attachJSDoc(info, node);
      }
      setSourceInfo(node, identifierToken);
      return node;
    }

    private boolean isAllowedProp(String identifier) {
      if (config.languageMode == LanguageMode.ECMASCRIPT3) {
        return !TokenStream.isKeyword(identifier);
      }
      return true;
    }

    private boolean isReservedKeyword(String identifier) {
      if (config.languageMode == LanguageMode.ECMASCRIPT3) {
        return TokenStream.isKeyword(identifier);
      }
      return reservedKeywords != null && reservedKeywords.contains(identifier);
    }

    @Override
    Node processNewExpression(NewExpressionTree exprNode) {
      Node node = newNode(
          Token.NEW,
          transform(exprNode.operand));
      if (exprNode.arguments != null) {
        for (ParseTree arg : exprNode.arguments.arguments) {
          node.addChildToBack(transform(arg));
        }
      }
      return node;
    }

    @Override
    Node processNumberLiteral(LiteralExpressionTree literalNode) {
      double value = normalizeNumber(literalNode.literalToken.asLiteral());
      return newNumberNode(value);
    }

    @Override
    Node processObjectLiteral(ObjectLiteralExpressionTree objTree) {
      Node node = newNode(Token.OBJECTLIT);
      for (ParseTree el : objTree.propertyNameAndValues) {
        if (config.languageMode == LanguageMode.ECMASCRIPT3) {
          if (el.type == ParseTreeType.GET_ACCESSOR) {
            reportGetter(el);
            continue;
          } else if (el.type == ParseTreeType.SET_ACCESSOR) {
            reportSetter(el);
            continue;
          }
        }

        Node key = transform(el);
        if (!key.isQuotedString() && !isAllowedProp(key.getString())) {
          errorReporter.warning(INVALID_ES3_PROP_NAME, sourceName,
              key.getLineno(), "", key.getCharno());
        }

        node.addChildToBack(key);
      }
      return node;
    }

    @Override
    Node processGetAccessor(GetAccessorTree tree) {
      Node key = processObjecLitKeyAsString(tree.propertyName);
      key.setType(Token.GETTER_DEF);
      Node body = transform(tree.body);
      Node dummyName = IR.name("");
      setSourceInfo(dummyName, tree.body);
      Node paramList = IR.paramList();
      setSourceInfo(paramList, tree.body);
      Node value = IR.function(dummyName, paramList, body);
      setSourceInfo(value, tree.body);
      key.addChildToFront(value);
      key.setStaticMember(tree.isStatic);
      return key;
    }

    @Override
    Node processSetAccessor(SetAccessorTree tree) {
      Node key = processObjecLitKeyAsString(tree.propertyName);
      key.setType(Token.SETTER_DEF);
      Node body = transform(tree.body);
      Node dummyName = IR.name("");
      setSourceInfo(dummyName, tree.propertyName);
      Node paramList = IR.paramList(
          safeProcessName(tree.parameter));
      setSourceInfo(paramList, tree.parameter);
      Node value = IR.function(dummyName, paramList, body);
      setSourceInfo(value, tree.body);
      key.addChildToFront(value);
      key.setStaticMember(tree.isStatic);
      return key;
    }

    @Override
    Node processPropertyNameAssignment(PropertyNameAssignmentTree tree) {
      Node key = processObjecLitKeyAsString(tree.name);
      key.setType(Token.STRING_KEY);
      key.addChildToFront(transform(tree.value));
      return key;
    }

    private Node safeProcessName(IdentifierToken identifierToken) {
      if (identifierToken == null) {
        return createMissingExpressionNode();
      } else {
        return processName(identifierToken);
      }
    }

    @Override
    Node processParenthesizedExpression(ParenExpressionTree exprNode) {
      Node node = transform(exprNode.expression);
      return node;
    }

    @Override
    Node processPropertyGet(MemberExpressionTree getNode) {
      Node leftChild = transform(getNode.operand);
      IdentifierToken nodeProp = getNode.memberName;
      Node rightChild = processObjecLitKeyAsString(nodeProp);
      if (!rightChild.isQuotedString() && !isAllowedProp(
          rightChild.getString())) {
        errorReporter.warning(INVALID_ES3_PROP_NAME, sourceName,
            rightChild.getLineno(), "", rightChild.getCharno());
      }
      Node newNode = newNode(
          Token.GETPROP, leftChild, rightChild);
      return newNode;
    }

    @Override
    Node processRegExpLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();
      Node literalStringNode = newStringNode(normalizeRegex(token));
      // TODO(johnlenz): fix the source location.
      setSourceInfo(literalStringNode, token);
      Node node = newNode(Token.REGEXP, literalStringNode);

      String rawRegex = token.value;
      int lastSlash = rawRegex.lastIndexOf('/');
      String flags = "";
      if (lastSlash < rawRegex.length()) {
        flags = rawRegex.substring(lastSlash + 1);
      }

      if (!flags.isEmpty()) {
        Node flagsNode = newStringNode(flags);
        // TODO(johnlenz): fix the source location.
        setSourceInfo(flagsNode, token);
        node.addChildToBack(flagsNode);
      }
      return node;
    }

    @Override
    Node processReturnStatement(ReturnStatementTree statementNode) {
      Node node = newNode(Token.RETURN);
      if (statementNode.expression != null) {
        node.addChildToBack(transform(statementNode.expression));
      }
      return node;
    }

    @Override
    Node processStringLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();

      Node n = processString(token);
      String value = n.getString();
      if (value.indexOf('\u000B') != -1) {
        // NOTE(nicksantos): In JavaScript, there are 3 ways to
        // represent a vertical tab: \v, \x0B, \u000B.
        // The \v notation was added later, and is not understood
        // on IE. So we need to preserve it as-is. This is really
        // obnoxious, because we do not have a good way to represent
        // how the original string was encoded without making the
        // representation of strings much more complicated.
        //
        // To handle this, we look at the original source test, and
        // mark the string as \v-encoded or not. If a string is
        // \v encoded, then all the vertical tabs in that string
        // will be encoded with a \v.
        int start = token.location.start.offset;
        int end = token.location.end.offset;
        if (start < sourceString.length() &&
            (sourceString.substring(
                 start, Math.min(sourceString.length(), end))
             .indexOf("\\v") != -1)) {
          n.putBooleanProp(Node.SLASH_V, true);
        }
      }
      return n;
    }


    @Override
    Node processSwitchCase(CaseClauseTree caseNode) {
      ParseTree expr = caseNode.expression;
      Node node = newNode(Token.CASE, transform(expr));
      Node block = newNode(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      setSourceInfo(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    @Override
    Node processSwitchDefault(DefaultClauseTree caseNode) {
      Node node = newNode(Token.DEFAULT_CASE);
      Node block = newNode(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      setSourceInfo(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    @Override
    Node processSwitchStatement(SwitchStatementTree statementNode) {
      Node node = newNode(Token.SWITCH,
          transform(statementNode.expression));
      for (ParseTree child : statementNode.caseClauses) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processThrowStatement(ThrowStatementTree statementNode) {
      return newNode(Token.THROW,
          transform(statementNode.value));
    }

    @Override
    Node processTryStatement(TryStatementTree statementNode) {
      Node node = newNode(Token.TRY,
          transformBlock(statementNode.body));
      Node block = newNode(Token.BLOCK);
      boolean lineSet = false;

      ParseTree cc = statementNode.catchBlock;
      if (cc != null) {
        // Mark the enclosing block at the same line as the first catch
        // clause.
        if (lineSet == false) {
          setSourceInfo(block, cc);
          lineSet = true;
        }
        block.addChildToBack(transform(cc));
      }

      node.addChildToBack(block);

      ParseTree finallyBlock = statementNode.finallyBlock;
      if (finallyBlock != null) {
        node.addChildToBack(transformBlock(finallyBlock));
      }

      // If we didn't set the line on the catch clause, then
      // we've got an empty catch clause.  Set its line to be the same
      // as the finally block (to match Old Rhino's behavior.)
      if ((lineSet == false) && (finallyBlock != null)) {
        setSourceInfo(block, finallyBlock);
      }

      return node;
    }

    @Override
    Node processCatchClause(CatchTree clauseNode) {
      IdentifierToken catchVar = clauseNode.exceptionName;
      Node node = newNode(Token.CATCH, processName(catchVar));
      node.addChildToBack(transformBlock(clauseNode.catchBody));
      return node;
    }

    @Override
    Node processFinally(FinallyTree finallyNode) {
      return transformBlock(finallyNode.block);
    }

    @Override
    Node processUnaryExpression(UnaryExpressionTree exprNode) {
      int type = transformUniaryTokenType(exprNode.operator.type);
      Node operand = transform(exprNode.operand);
      if (type == Token.NEG && operand.isNumber()) {
        operand.setDouble(-operand.getDouble());
        return operand;
      } else {
        if (type == Token.DELPROP &&
            !(operand.isGetProp() ||
              operand.isGetElem() ||
              operand.isName())) {
          String msg =
              "Invalid delete operand. Only properties can be deleted.";
          errorReporter.error(
              msg,
              sourceName,
              operand.getLineno(), "", 0);
        } else  if (type == Token.INC || type == Token.DEC) {
          if (!validAssignmentTarget(operand)) {
            String msg = (type == Token.INC)
                ? "invalid increment target"
                : "invalid decrement target";
            errorReporter.error(
                msg,
                sourceName,
                operand.getLineno(), "", 0);
          }
        }

        Node node = newNode(type, operand);
        return node;
      }
    }

    @Override
    Node processPostfixExpression(PostfixExpressionTree exprNode) {
      int type = transformPostfixTokenType(exprNode.operator.type);
      Node operand = transform(exprNode.operand);
      // Only INC and DEC
      if (!validAssignmentTarget(operand)) {
        String msg = (type == Token.INC)
            ? "invalid increment target"
            : "invalid decrement target";
        errorReporter.error(
            msg,
            sourceName,
            operand.getLineno(), "", 0);
      }

      Node node = newNode(type, operand);
      node.putBooleanProp(Node.INCRDECR_PROP, true);
      return node;
    }

    private boolean validAssignmentTarget(Node target) {
      switch (target.getType()) {
        case Token.CAST: // CAST is a bit weird, but syntactically valid.
        case Token.NAME:
        case Token.GETPROP:
        case Token.GETELEM:
          return true;
      }
      return false;
    }

    @Override
    Node processVariableStatement(VariableStatementTree stmt) {
      // skip the special handling so the doc is attached in the right place.
      return justTransform(stmt.declarations);
    }

    @Override
    Node processVariableDeclarationList(VariableDeclarationListTree decl) {
      int declType;
      switch (decl.declarationType) {
        case CONST:
          if (!config.acceptConstKeyword) {
            maybeWarnEs6Feature(decl, "const declarations");
          }
          declType = Token.CONST;
          break;
        case LET:
          maybeWarnEs6Feature(decl, "let declarations");
          declType = Token.LET;
          break;
        case VAR:
          declType = Token.VAR;
          break;
        default:
          throw new IllegalStateException();
      }

      Node node = newNode(declType);
      for (VariableDeclarationTree child : decl.declarations) {
        node.addChildToBack(
            transformNodeWithInlineJsDoc(child, true));
      }
      return node;
    }

    @Override
    Node processVariableDeclaration(VariableDeclarationTree decl) {
      Node node = transformNodeWithInlineJsDoc(decl.lvalue, true);
      if (decl.initializer != null) {
        Node initalizer = transform(decl.initializer);
        node.addChildToBack(initalizer);
      }
      return node;
    }

    @Override
    Node processWhileLoop(WhileStatementTree stmt) {
      return newNode(
          Token.WHILE,
          transform(stmt.condition),
          transformBlock(stmt.body));
    }

    @Override
    Node processWithStatement(WithStatementTree stmt) {
      return newNode(
          Token.WITH,
          transform(stmt.expression),
          transformBlock(stmt.body));
    }

    @Override
    Node processMissingExpression(MissingPrimaryExpressionTree tree) {
      // This will already have been reported as an error by the parser.
      // Try to create something valid that ide mode might be able to
      // continue with.
      return createMissingExpressionNode();
    }

    private Node createMissingNameNode() {
      return newStringNode(Token.NAME, "__missing_name__");
    }

    private Node createMissingExpressionNode() {
      return newStringNode(Token.NAME, "__missing_expression__");
    }

    @Override
    Node processIllegalToken(ParseTree node) {
      errorReporter.error(
          "Unsupported syntax: " + node.type.toString(),
          sourceName,
          lineno(node), "", 0);
      return newNode(Token.EMPTY);
    }

    void reportDestructuringAssign(ParseTree node) {
      errorReporter.error(
          "destructuring assignment forbidden",
          sourceName,
          lineno(node), "", 0);
    }

    void reportGetter(ParseTree node) {
      errorReporter.error(
          GETTER_ERROR_MESSAGE,
          sourceName,
          lineno(node), "", 0);
    }

    void reportSetter(ParseTree node) {
      errorReporter.error(
          SETTER_ERROR_MESSAGE,
          sourceName,
          lineno(node), "", 0);
    }

    @Override
    Node processBooleanLiteral(LiteralExpressionTree literal) {
      return newNode(transformBooleanTokenType(
          literal.literalToken.type));
    }

    @Override
    Node processNullLiteral(LiteralExpressionTree literal) {
      return newNode(Token.NULL);
    }

    @Override
    Node processNull(NullTree literal) {
      // NOTE: This is not a NULL literal but a placeholder node such as in
      // an array with "holes".
      return newNode(Token.EMPTY);
    }

    @Override
    Node processCommaExpression(CommaExpressionTree tree) {
      Node root = newNode(Token.COMMA);
      SourcePosition start = tree.expressions.get(0).location.start;
      SourcePosition end = tree.expressions.get(1).location.end;
      setSourceInfo(root, start, end);
      for (ParseTree expr : tree.expressions) {
        int count = root.getChildCount();
        if (count < 2) {
          root.addChildrenToBack(transform(expr));
        } else {
          end = expr.location.end;
          root = newNode(Token.COMMA, root, transform(expr));
          setSourceInfo(root, start, end);
        }
      }
      return root;
    }

    @Override
    Node processClassDeclaration(ClassDeclarationTree tree) {
      if (!isEs6Mode()) {
        maybeWarnEs6Feature(tree, "class");
      }

      Node name = transformOrEmpty(tree.name);
      Node superClass = transformOrEmpty(tree.superClass);

      Node body = newNode(Token.CLASS_MEMBERS);
      setSourceInfo(body, tree);
      for (ParseTree child : tree.elements) {
        body.addChildToBack(transform(child));
      }

      return newNode(Token.CLASS, name, superClass, body);
    }

    @Override
    Node processSuper(SuperExpressionTree tree) {
      if (!isEs6Mode()) {
        maybeWarnEs6Feature(tree, "super");
      }

      return newNode(Token.SUPER);
    }

    @Override
    Node processYield(YieldExpressionTree tree) {
      Node yield = new Node(Token.YIELD);
      if (tree.expression != null) {
        yield.addChildToBack(transform(tree.expression));
      }
      yield.setYieldFor(tree.isYieldFor);
      return yield;
    }

    @Override
    Node processExportDecl(ExportDeclarationTree tree) {
      maybeWarnEs6Feature(tree, "modules");
      Node decls = null;
      if (tree.isExportAll) {
        Preconditions.checkState(
            tree.declaration == null &&
            tree.exportSpecifierList == null);
      } else if (tree.declaration != null) {
        Preconditions.checkState(tree.exportSpecifierList == null);
        decls = transform(tree.declaration);
      } else {
        decls = transformList(Token.EXPORT_SPECS, tree.exportSpecifierList);
      }
      if (decls == null) {
        decls = newNode(Token.EMPTY);
      }
      Node export = newNode(Token.EXPORT, decls);
      if (tree.from != null) {
        Node from = processString(tree.from);
        export.addChildToBack(from);
      }

      export.putBooleanProp(Node.EXPORT_ALL_FROM, tree.isExportAll);
      export.putBooleanProp(Node.EXPORT_DEFAULT, tree.isDefault);
      return export;
    }

    @Override
    Node processExportSpec(ExportSpecifierTree tree) {
      Node importSpec = newNode(Token.EXPORT_SPEC,
          processName(tree.importedName));
      if (tree.destinationName != null) {
        importSpec.addChildToBack(processName(tree.destinationName));
      }
      return importSpec;
    }

    @Override
    Node processImportDecl(ImportDeclarationTree tree) {
      maybeWarnEs6Feature(tree, "modules");
      Node export = newNode(Token.IMPORT,
          transformOrEmpty(tree.defaultBindingIndentifier),
          transformListOrEmpty(Token.IMPORT_SPECS, tree.importSpecifierList),
          processString(tree.moduleSpecifier));
      return export;
    }

    @Override
    Node processImportSpec(ImportSpecifierTree tree) {
      Node importSpec = newNode(Token.IMPORT_SPEC,
          processName(tree.importedName));
      if (tree.destinationName != null) {
        importSpec.addChildToBack(processName(tree.destinationName));
      }
      return importSpec;
    }

    @Override
    Node processModuleImport(ModuleImportTree tree) {
      maybeWarnEs6Feature(tree, "modules");
      Node module = newNode(Token.MODULE,
          processName(tree.name),
          processString(tree.from));
      return module;
    }

    private Node transformList(
        int type, ImmutableList<ParseTree> list) {
      Node n = newNode(type);
      for (ParseTree tree : list) {
        n.addChildToBack(transform(tree));
      }
      return n;
    }

    private Node transformListOrEmpty(
        int type, ImmutableList<ParseTree> list) {
      if (list != null) {
        return transformList(type, list);
      } else {
        return newNode(Token.EMPTY);
      }
    }

    void maybeWarnEs6Feature(ParseTree node, String feature) {
      if (!isEs6Mode()) {
        errorReporter.warning(
            "this language feature is only supported in es6 mode: " + feature,
            sourceName,
            lineno(node), "", charno(node));
      }
    }

    @Override
    Node unsupportedLanguageFeature(ParseTree node, String feature) {
      errorReporter.error(
          "unsupported language feature: " + feature,
          sourceName,
          lineno(node), "", charno(node));
      return createMissingExpressionNode();
    }
  }

  private String normalizeRegex(LiteralToken token) {
    String value = token.value;
    int lastSlash = value.lastIndexOf('/');
    return value.substring(1, lastSlash);
  }


  private String normalizeString(LiteralToken token) {
    String value = token.value;
    int start = 1; // skip the leading quote
    int cur = value.indexOf('\\');
    if (cur == -1) {
      // short circuit no escapes.
      return value.substring(1, value.length() - 1);
    }
    StringBuilder result = new StringBuilder();
    while (cur != -1) {
      if (cur - start > 0) {
        result.append(value.substring(start, cur));
      }
      cur += 1; // skip the escape char.
      char c = value.charAt(cur);
      switch (c) {
        case '\'':
        case '"':
        case '\\':
          result.append(c);
          break;
        case 'b':
          result.append('\b');
          break;
        case 'f':
          result.append('\f');
          break;
        case 'n':
          result.append('\n');
          break;
        case 'r':
          result.append('\r');
          break;
        case 't':
          result.append('\t');
          break;
        case 'v':
          result.append('\u000B');
          break;
        case '\n':
          if (!isEs5OrBetterMode()) {
            errorReporter.warning(STRING_CONTINUATION_WARNING,
                sourceName,
                lineno(token.location.start), "", charno(token.location.start));
          }
          // line continuation, skip the line break
          break;
        case '0':
          // TODO(johnlenz): support octal?
          result.append('\0');
          break;
        case 'x':
          result.append((char) (
              hexdigit(value.charAt(cur + 1)) * 16
              + hexdigit(value.charAt(cur + 2))));
          cur += 2;
          break;
        case 'u':
          result.append((char) (
              hexdigit(value.charAt(cur + 1)) * 16 * 16 * 16
              + hexdigit(value.charAt(cur + 2)) * 16 * 16
              + hexdigit(value.charAt(cur + 3)) * 16
              + hexdigit(value.charAt(cur + 4))));
          cur += 4;
          break;
        default:
          result.append(c);
          break;
      }
      start = cur + 1;
      cur = value.indexOf('\\', start);
    }
    // skip the trailing quote.
    result.append(value.substring(start, value.length() - 1));

    return result.toString();
  }

  boolean isEs6Mode() {
    return config.languageMode == LanguageMode.ECMASCRIPT6
        || config.languageMode == LanguageMode.ECMASCRIPT6_STRICT;
  }

  boolean isEs5OrBetterMode() {
    return config.languageMode != LanguageMode.ECMASCRIPT3;
  }

  private boolean inStrictContext() {
    // TODO(johnlenz): in ECMASCRIPT5/6 is a "mixed" mode and we should track the context
    // that we are in, if we want to support it.
    return config.languageMode == LanguageMode.ECMASCRIPT5_STRICT
        || config.languageMode == LanguageMode.ECMASCRIPT6_STRICT;
  }

  double normalizeNumber(LiteralToken token) {
    String value = token.value;
    SourceRange location = token.location;
    int length = value.length();
    Preconditions.checkState(length > 0);
    Preconditions.checkState(value.charAt(0) != '-'
        && value.charAt(0) != '+');
    if (value.charAt(0) == '.') {
      return Double.valueOf("0" + value);
    } else if (value.charAt(0) == '0' && length > 1) {
      // TODO(johnlenz): accept octal numbers in es3 etc.
      switch (value.charAt(1)) {
        case '.':
          return Double.valueOf(value);
        case 'b':
        case 'B': {
          if (!isEs6Mode()) {
            errorReporter.warning(BINARY_NUMBER_LITERAL_WARNING,
                sourceName,
                lineno(token.location.start), "", charno(token.location.start));
          }
          long v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 2) + binarydigit(value.charAt(c));
          }
          return Double.valueOf(v);
        }
        case 'o':
        case 'O': {
          if (!isEs6Mode()) {
            errorReporter.warning(OCTAL_NUMBER_LITERAL_WARNING,
                sourceName,
                lineno(token.location.start), "", charno(token.location.start));
          }
          long v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 8) + octaldigit(value.charAt(c));
          }
          return Double.valueOf(v);
        }
        case 'x':
        case 'X': {
          long v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 16) + hexdigit(value.charAt(c));
          }
          return Double.valueOf(v);
        }
        case '0': case '1': case '2': case '3':
        case '4': case '5': case '6': case '7':
          errorReporter.warning(INVALID_ES5_STRICT_OCTAL, sourceName,
              lineno(location.start), "", charno(location.start));
          if (!inStrictContext()) {
            long v = 0;
            int c = 0;
            while (++c < length) {
              v = (v * 8) + octaldigit(value.charAt(c));
            }
            return Double.valueOf(v);
          } else {
            return Double.valueOf(value);
          }
      }
    } else {
      return Double.valueOf(value);
    }
    throw new IllegalStateException("unexpected");
  }

  int binarydigit(char c) {
    if (c >= '0' && c <= '1') {
      return (c - '0');
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  int octaldigit(char c) {
    if (c >= '0' && c <= '7') {
      return (c - '0');
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  int digit(char c) {
    if (c >= '0' && c <= '9') {
      return (c - '0');
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  int hexdigit(char c) {
    switch (c) {
      case '0': return 0;
      case '1': return 1;
      case '2': return 2;
      case '3': return 3;
      case '4': return 4;
      case '5': return 5;
      case '6': return 6;
      case '7': return 7;
      case '8': return 8;
      case '9': return 9;
      case 'a': case 'A': return 10;
      case 'b': case 'B': return 11;
      case 'c': case 'C': return 12;
      case 'd': case 'D': return 13;
      case 'e': case 'E': return 14;
      case 'f': case 'F': return 15;
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  private static int transformBooleanTokenType(TokenType token) {
    switch (token) {
      case TRUE:
        return Token.TRUE;
      case FALSE:
        return Token.FALSE;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  private static int transformPostfixTokenType(TokenType token) {
    switch (token) {
      case PLUS_PLUS:
        return Token.INC;
      case MINUS_MINUS:
        return Token.DEC;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  private static int transformUniaryTokenType(TokenType token) {
    switch (token) {
      case BANG:
        return Token.NOT;
      case TILDE:
        return Token.BITNOT;
      case PLUS:
        return Token.POS;
      case MINUS:
        return Token.NEG;
      case DELETE:
        return Token.DELPROP;
      case TYPEOF:
        return Token.TYPEOF;

      case PLUS_PLUS:
        return Token.INC;
      case MINUS_MINUS:
        return Token.DEC;

      case VOID:
        return Token.VOID;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  private static int transformBinaryTokenType(TokenType token) {
    switch (token) {
      case BAR:
        return Token.BITOR;
      case CARET:
        return Token.BITXOR;
      case AMPERSAND:
        return Token.BITAND;
      case EQUAL_EQUAL:
        return Token.EQ;
      case NOT_EQUAL:
        return Token.NE;
      case OPEN_ANGLE:
        return Token.LT;
      case LESS_EQUAL:
        return Token.LE;
      case CLOSE_ANGLE:
        return Token.GT;
      case GREATER_EQUAL:
        return Token.GE;
      case LEFT_SHIFT:
        return Token.LSH;
      case RIGHT_SHIFT:
        return Token.RSH;
      case UNSIGNED_RIGHT_SHIFT:
        return Token.URSH;
      case PLUS:
        return Token.ADD;
      case MINUS:
        return Token.SUB;
      case STAR:
        return Token.MUL;
      case SLASH:
        return Token.DIV;
      case PERCENT:
        return Token.MOD;

      case EQUAL_EQUAL_EQUAL:
        return Token.SHEQ;
      case NOT_EQUAL_EQUAL:
        return Token.SHNE;

      case IN:
        return Token.IN;
      case INSTANCEOF:
        return Token.INSTANCEOF;
      case COMMA:
        return Token.COMMA;

      case EQUAL:
        return Token.ASSIGN;
      case BAR_EQUAL:
        return Token.ASSIGN_BITOR;
      case CARET_EQUAL:
        return Token.ASSIGN_BITXOR;
      case AMPERSAND_EQUAL:
        return Token.ASSIGN_BITAND;
      case LEFT_SHIFT_EQUAL:
        return Token.ASSIGN_LSH;
      case RIGHT_SHIFT_EQUAL:
        return Token.ASSIGN_RSH;
      case UNSIGNED_RIGHT_SHIFT_EQUAL:
        return Token.ASSIGN_URSH;
      case PLUS_EQUAL:
        return Token.ASSIGN_ADD;
      case MINUS_EQUAL:
        return Token.ASSIGN_SUB;
      case STAR_EQUAL:
        return Token.ASSIGN_MUL;
      case SLASH_EQUAL:
        return Token.ASSIGN_DIV;
      case PERCENT_EQUAL:
        return Token.ASSIGN_MOD;

      case OR:
        return Token.OR;
      case AND:
        return Token.AND;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  // Simple helper to create nodes and set the initial node properties.
  private Node newNode(int type) {
    return new Node(type).clonePropsFrom(templateNode);
  }

  private Node newNode(int type, Node child1) {
    return new Node(type, child1).clonePropsFrom(templateNode);
  }

  private Node newNode(int type, Node child1, Node child2) {
    return new Node(type, child1, child2).clonePropsFrom(templateNode);
  }

  private Node newNode(int type, Node child1, Node child2, Node child3) {
    return new Node(type, child1, child2, child3).clonePropsFrom(templateNode);
  }

  private Node newStringNode(String value) {
    return IR.string(value).clonePropsFrom(templateNode);
  }

  private Node newStringNode(int type, String value) {
    return Node.newString(type, value).clonePropsFrom(templateNode);
  }

  private Node newNumberNode(Double value) {
    return IR.number(value).clonePropsFrom(templateNode);
  }
}
