/*
 * Copyright 2007 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;


/**
 * Tests {@link ConstCheck}.
 *
 */
public class ConstCheckTest extends CompilerTestCase {

  public ConstCheckTest() {
    enableNormalize();
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new ConstCheck(compiler);
  }

  @Override
  public int getNumRepetitions() {
    return 1;
  }

  public void testConstantDefinition1() {
    testSame("var XYZ = 1;");
  }

  public void testConstantDefinition2() {
    testSame("var a$b$XYZ = 1;");
  }

  public void testConstantInitializedInAnonymousNamespace1() {
    testSame("var XYZ; (function(){ XYZ = 1; })();");
  }

  public void testConstantInitializedInAnonymousNamespace2() {
    testSame("var a$b$XYZ; (function(){ a$b$XYZ = 1; })();");
  }

  public void testObjectModified() {
    testSame("var IE = true, XYZ = {a:1,b:1}; if (IE) XYZ['c'] = 1;");
  }

  public void testObjectPropertyInitializedLate() {
    testSame("var XYZ = {}; for (var i = 0; i < 10; i++) { XYZ[i] = i; }");
  }

  public void testObjectRedefined1() {
    testError("var XYZ = {}; XYZ = 2;");
  }

  public void testConstantRedefined1() {
    testError("var XYZ = 1; XYZ = 2;");
  }

  public void testConstantRedefined2() {
    testError("var a$b$XYZ = 1; a$b$XYZ = 2;");
  }

  public void testConstantRedefinedInLocalScope1() {
    testError("var XYZ = 1; (function(){ XYZ = 2; })();");
  }

  public void testConstantRedefinedInLocalScope2() {
    testError("var a$b$XYZ = 1; (function(){ a$b$XYZ = 2; })();");
  }

  public void testConstantRedefinedInLocalScopeOutOfOrder() {
    testError("function f() { XYZ = 2; } var XYZ = 1;");
  }

  public void testConstantPostIncremented1() {
    testError("var XYZ = 1; XYZ++;");
  }

  public void testConstantPostIncremented2() {
    testError("var a$b$XYZ = 1; a$b$XYZ++;");
  }

  public void testConstantPreIncremented1() {
    testError("var XYZ = 1; ++XYZ;");
  }

  public void testConstantPreIncremented2() {
    testError("var a$b$XYZ = 1; ++a$b$XYZ;");
  }

  public void testConstantPostDecremented1() {
    testError("var XYZ = 1; XYZ--;");
  }

  public void testConstantPostDecremented2() {
    testError("var a$b$XYZ = 1; a$b$XYZ--;");
  }

  public void testConstantPreDecremented1() {
    testError("var XYZ = 1; --XYZ;");
  }

  public void testConstantPreDecremented2() {
    testError("var a$b$XYZ = 1; --a$b$XYZ;");
  }

  public void testAbbreviatedArithmeticAssignment1() {
    testError("var XYZ = 1; XYZ += 2;");
  }

  public void testAbbreviatedArithmeticAssignment2() {
    testError("var a$b$XYZ = 1; a$b$XYZ %= 2;");
  }

  public void testAbbreviatedBitAssignment1() {
    testError("var XYZ = 1; XYZ |= 2;");
  }

  public void testAbbreviatedBitAssignment2() {
    testError("var a$b$XYZ = 1; a$b$XYZ &= 2;");
  }

  public void testAbbreviatedShiftAssignment1() {
    testError("var XYZ = 1; XYZ >>= 2;");
  }

  public void testAbbreviatedShiftAssignment2() {
    testError("var a$b$XYZ = 1; a$b$XYZ <<= 2;");
  }

  public void testConstAnnotation() {
    testError("/** @const */ var xyz = 1; xyz = 3;");
  }

  public void testConstSuppression() {
    testSame("/**\n" +
             " * @fileoverview\n" +
             " * @suppress {const}\n" +
             " */\n" +
             "/** @const */ var xyz = 1; xyz = 3;");
  }

  private void testError(String js) {
    test(js, null, ConstCheck.CONST_REASSIGNED_VALUE_ERROR);
  }
}
