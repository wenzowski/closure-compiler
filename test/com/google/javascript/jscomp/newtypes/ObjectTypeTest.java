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

package com.google.javascript.jscomp.newtypes;

import static com.google.javascript.jscomp.newtypes.JSType.NUMBER;
import static com.google.javascript.jscomp.newtypes.JSType.STRING;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

/**
 * Unit tests for JSType/fromTypeExpression.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class ObjectTypeTest extends TestCase {
  public void testObjectSubtyping() {
    ObjectType topObj = ObjectType.TOP_OBJECT;
    ObjectType withPNum = topObj.withProperty("p", NUMBER);
    ObjectType withPStr = topObj.withProperty("p", STRING);
    ObjectType withOptionalProps = ObjectType.join(
        withPNum, topObj.withProperty("q", STRING));
    ObjectType withOneOptional = ObjectType.join(withPNum, topObj);

    assertTrue(withPNum.isSubtypeOf(topObj));
    assertTrue(withPNum.isSubtypeOf(withPNum));
    assertFalse(withPNum.isSubtypeOf(withPStr));
    assertTrue(topObj.isSubtypeOf(withOptionalProps));
    assertTrue(withOptionalProps.isSubtypeOf(topObj));
    assertTrue(withOptionalProps.isSubtypeOf(withOneOptional));
  }

  public void testClassSubtyping() {
    ObjectType foo = ObjectType.fromNominalType(
        NominalType.makeClass("Foo", null, 0).finalizeNominalType());
    ObjectType bar = ObjectType.fromNominalType(
        NominalType.makeClass("Bar", null, 1).finalizeNominalType());
    assertTrue(foo.isSubtypeOf(foo));
    assertFalse(foo.isSubtypeOf(bar));
  }

  public void testObjectUnions() {
    ObjectType foo = ObjectType.fromNominalType(
        NominalType.makeClass("Foo", null, 0).finalizeNominalType());
    ObjectType bar = ObjectType.fromNominalType(
        NominalType.makeClass("Bar", null, 1).finalizeNominalType());
    ObjectType baz = ObjectType.fromNominalType(
        NominalType.makeClass("Baz", null, 2).finalizeNominalType());
    ObjectType topObj = ObjectType.TOP_OBJECT;
    ObjectType withPNum = topObj.withProperty("p", NUMBER);
    ObjectType fooWithPNum = foo.withProperty("p", NUMBER);
    ObjectType fooWithQStr = foo.withProperty("q", STRING);
    // joins
    assertEquals(topObj, ObjectType.join(foo, topObj));
    assertEquals(ImmutableSet.of(topObj),
        ObjectType.joinSets(ImmutableSet.of(foo), ImmutableSet.of(topObj)));
    assertEquals(ImmutableSet.of(foo, withPNum),
        ObjectType.joinSets(ImmutableSet.of(foo), ImmutableSet.of(withPNum)));
    assertEquals(ImmutableSet.of(withPNum), ObjectType.joinSets(
            ImmutableSet.of(fooWithPNum), ImmutableSet.of(withPNum)));
    assertEquals(ImmutableSet.of(withPNum), ObjectType.joinSets(
            ImmutableSet.of(withPNum), ImmutableSet.of(fooWithPNum)));
    assertEquals(ImmutableSet.of(ObjectType.join(fooWithPNum, fooWithQStr)),
        ObjectType.joinSets(
            ImmutableSet.of(fooWithQStr), ImmutableSet.of(fooWithPNum)));
    // meets
    assertEquals(ImmutableSet.of(foo),
        ObjectType.meetSets(ImmutableSet.of(foo), ImmutableSet.of(topObj)));
    assertEquals(ImmutableSet.of(fooWithPNum), ObjectType.meetSets(
            ImmutableSet.of(fooWithPNum), ImmutableSet.of(withPNum)));
    assertEquals(ImmutableSet.of(fooWithPNum), ObjectType.meetSets(
        ImmutableSet.of(foo), ImmutableSet.of(withPNum)));
    assertEquals(ImmutableSet.of(foo),
        ObjectType.meetSets(ImmutableSet.of(foo, bar),
          ImmutableSet.of(foo, baz)));
  }

  public void testSimpleClassInheritance() {
    NominalType parentClass =
        NominalType.makeClass("Parent", null, 0).finalizeNominalType();
    NominalType child1Class = NominalType.makeClass("Child1", null, 1);
    child1Class.addSuperClass(parentClass);
    child1Class.finalizeNominalType();
    NominalType child2Class = NominalType.makeClass("Child2", null, 2);
    child2Class.addSuperClass(parentClass);
    child2Class.finalizeNominalType();
    ObjectType foo = ObjectType.fromNominalType(
        NominalType.makeClass("Foo", null, 3).finalizeNominalType());
    ObjectType parent = ObjectType.fromNominalType(parentClass);
    ObjectType child1 = ObjectType.fromNominalType(child1Class);
    ObjectType child2 = ObjectType.fromNominalType(child2Class);
    ObjectType parentWithP = parent.withProperty("p", NUMBER);
    ObjectType parentWithOptP = ObjectType.join(parent, parentWithP);

    assertEquals(ImmutableSet.of(parentWithOptP),
          ObjectType.joinSets(
              ImmutableSet.of(parentWithP), ImmutableSet.of(child1)));

    assertTrue(ObjectType.isUnionSubtype(
        ImmutableSet.of(foo, child1), ImmutableSet.of(foo, parent)));
    assertFalse(ObjectType.isUnionSubtype(
        ImmutableSet.of(foo, parent), ImmutableSet.of(foo, child1)));

    assertTrue(ObjectType.isUnionSubtype(
        ImmutableSet.of(child1, child2), ImmutableSet.of(parent)));
    assertFalse(ObjectType.isUnionSubtype(
        ImmutableSet.of(parent), ImmutableSet.of(child1, child2)));
  }
}
