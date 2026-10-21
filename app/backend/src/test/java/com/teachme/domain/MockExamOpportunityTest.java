package com.teachme.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MockExamOpportunityTest {

  @Test
  void 授業の完了が_3_回に達すると機会がある() {
    assertFalse(MockExamOpportunity.isAvailable(2));
    assertTrue(MockExamOpportunity.isAvailable(3));
    assertTrue(MockExamOpportunity.isAvailable(4));
  }
}
